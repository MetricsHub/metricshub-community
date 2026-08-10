package org.metricshub.opamp.client.impl;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OpAMP Client
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.opamp.client.OpampClient;
import org.metricshub.opamp.client.OpampClientCallbacks;
import org.metricshub.opamp.client.OpampClientSettings;
import org.metricshub.opamp.client.http.OpampHttpTransport;
import org.metricshub.opamp.client.http.OpampTransport;
import org.metricshub.opamp.client.http.TransportResponse;
import org.metricshub.opamp.client.packages.OpampPackagesHandler;
import org.metricshub.opamp.client.packages.PackageDownloadContext;
import org.metricshub.opamp.client.packages.PackageStatusSink;
import org.metricshub.opamp.client.retry.RetrySchedule;
import org.metricshub.opamp.client.state.InstanceUidStore;
import org.metricshub.opamp.proto.AgentCapabilities;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.opamp.proto.AgentToServer;
import org.metricshub.opamp.proto.ComponentHealth;
import org.metricshub.opamp.proto.ServerErrorResponse;
import org.metricshub.opamp.proto.ServerErrorResponseType;
import org.metricshub.opamp.proto.ServerToAgent;
import org.metricshub.opamp.proto.ServerToAgentFlags;

/**
 * OpAMP client over plain HTTP polling: periodically POSTs a serialized {@code AgentToServer}
 * message to the OpAMP server and processes the {@code ServerToAgent} response. All protocol
 * state is confined to a single daemon polling thread; state setters can be called from any
 * thread.
 */
@Slf4j
public class HttpPollingOpampClient implements OpampClient {

	private static final String POLLING_THREAD_NAME = "metricshub-opamp-client";
	private static final Duration DISCONNECT_TIMEOUT = Duration.ofSeconds(5);

	private final OpampClientSettings settings;
	private final OpampClientCallbacks callbacks;
	private final OpampTransport transport;
	private final InstanceUidStore instanceUidStore;
	private final RetrySchedule retrySchedule;
	private final ScheduledExecutorService executor;
	private final PackageStatusAggregator packageStatusAggregator;
	private final PackageDownloadContext downloadContext;

	private OpampPackagesHandler packagesHandler;
	private AgentToServerAssembler assembler;

	private final Object lifecycleLock = new Object();
	private ScheduledFuture<?> nextPoll;
	private volatile boolean started;
	private volatile boolean stopped;
	private volatile boolean connected;

	private final AgentToServerAssembler earlyStateBuffer = new AgentToServerAssembler(ByteString.EMPTY, 0);

	/**
	 * Creates the client.
	 *
	 * @param settings        the client settings
	 * @param callbacks       the callbacks notified of connectivity and message events;
	 *                        {@code null} for no-op callbacks
	 * @param transport       the transport used for OpAMP exchanges; {@code null} to use the
	 *                        default HTTP transport built from the settings
	 * @param packagesHandler the handler receiving package offers; {@code null} when package
	 *                        management is not enabled
	 */
	@Builder(setterPrefix = "with")
	public HttpPollingOpampClient(
		final OpampClientSettings settings,
		final OpampClientCallbacks callbacks,
		final OpampTransport transport,
		final OpampPackagesHandler packagesHandler
	) {
		this.settings = settings;
		this.callbacks = callbacks != null ? callbacks : new OpampClientCallbacks() {};
		this.transport = transport != null ? transport : new OpampHttpTransport(settings);
		this.packagesHandler = packagesHandler;
		this.instanceUidStore = new InstanceUidStore(settings.getInstanceUidFile());
		this.retrySchedule = new RetrySchedule(settings.getPollInterval(), settings.getMaxBackoff());
		this.packageStatusAggregator = new PackageStatusAggregator(this::pollNow);
		this.downloadContext = new PackageDownloadContext() {
			@Override
			public Map<String, String> headers() {
				return settings.getHeaders();
			}

			@Override
			public Optional<String> certificateFile() {
				return Optional.ofNullable(settings.getCertificateFile());
			}
		};
		final ThreadFactory threadFactory = runnable -> {
			final Thread thread = new Thread(runnable, POLLING_THREAD_NAME);
			thread.setDaemon(true);
			return thread;
		};
		this.executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
	}

	@Override
	public void start() {
		synchronized (lifecycleLock) {
			if (started) {
				throw new IllegalStateException("The OpAMP client is already started.");
			}
			if (stopped) {
				throw new IllegalStateException("The OpAMP client is stopped and cannot be restarted.");
			}

			final byte[] instanceUid;
			try {
				instanceUid = instanceUidStore.loadOrCreate();
			} catch (IOException e) {
				throw new IllegalStateException("Failed to load or create the OpAMP instance UID: " + e.getMessage(), e);
			}

			assembler = new AgentToServerAssembler(ByteString.copyFrom(instanceUid), computeCapabilities());
			copyEarlyState();

			if (packagesHandler != null) {
				packageStatusAggregator.seed(packagesHandler.currentPackageStatuses());
				assembler.setPackageStatusesSupplier(packageStatusAggregator::toProto);
			}

			started = true;
			log.info("OpAMP client started. Endpoint: {}.", settings.getEndpoint());
			scheduleNext(Duration.ZERO);
		}
	}

	@Override
	public void stop(final String reason) {
		synchronized (lifecycleLock) {
			if (stopped) {
				return;
			}
			stopped = true;
			if (nextPoll != null) {
				nextPoll.cancel(false);
			}
		}

		if (started) {
			log.info("OpAMP client stopping. Reason: {}.", reason);
			final Future<?> disconnectFuture = executor.submit(this::sendDisconnect);
			try {
				disconnectFuture.get(DISCONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException | TimeoutException e) {
				log.debug("The OpAMP AgentDisconnect message could not be delivered.", e);
			}
		}

		executor.shutdownNow();
		transport.close();
	}

	@Override
	public void setAgentDescription(final AgentDescription agentDescription) {
		currentAssembler().setAgentDescription(agentDescription);
	}

	@Override
	public void setHealth(final ComponentHealth health) {
		if (settings.isReportHealth()) {
			currentAssembler().setHealth(health);
		}
	}

	@Override
	public void setPackagesHandler(final OpampPackagesHandler handler) {
		synchronized (lifecycleLock) {
			if (started) {
				throw new IllegalStateException("The packages handler must be registered before the OpAMP client starts.");
			}
			this.packagesHandler = handler;
		}
	}

	@Override
	public PackageStatusSink packageStatusSink() {
		return packageStatusAggregator;
	}

	@Override
	public void pollNow() {
		synchronized (lifecycleLock) {
			if (!started || stopped) {
				return;
			}
			if (nextPoll != null) {
				nextPoll.cancel(false);
			}
			nextPoll = executor.schedule(this::pollOnce, 0, TimeUnit.MILLISECONDS);
		}
	}

	@Override
	public boolean isStarted() {
		return started && !stopped;
	}

	@Override
	public void close() {
		stop("Client closed");
	}

	/**
	 * Performs one OpAMP exchange and schedules the next one. Runs on the polling thread.
	 */
	private void pollOnce() {
		if (stopped) {
			return;
		}
		Duration nextDelay;
		try {
			final AgentToServer request = assembler.assemble();
			final TransportResponse response = transport.send(request.toByteArray());
			if (response.isSuccess()) {
				assembler.commit();
				final ServerToAgent serverToAgent = ServerToAgent.parseFrom(response.body());
				nextDelay = processServerToAgent(serverToAgent);
			} else {
				nextDelay = onTransportFailure(
					new IOException("The OpAMP server answered with HTTP status " + response.statusCode()),
					response.retryAfter().orElse(null)
				);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		} catch (IOException | RuntimeException e) {
			nextDelay = onTransportFailure(e, null);
		}
		scheduleNext(nextDelay);
	}

	/**
	 * Processes a {@code ServerToAgent} message and returns the delay before the next poll.
	 *
	 * @param response the message received from the server
	 * @return the delay before the next poll
	 */
	private Duration processServerToAgent(final ServerToAgent response) {
		if (response.hasErrorResponse()) {
			return processErrorResponse(response.getErrorResponse());
		}

		retrySchedule.reset();
		if (!connected) {
			connected = true;
			callbacks.onConnect();
		}

		if (response.hasAgentIdentification()) {
			adoptNewInstanceUid(response.getAgentIdentification().getNewInstanceUid());
		}

		Duration nextDelay = settings.getPollInterval();
		final long reportFullState = ServerToAgentFlags.ServerToAgentFlags_ReportFullState.getNumber();
		if ((response.getFlags() & reportFullState) != 0) {
			log.debug("The OpAMP server requested a full state report.");
			assembler.requestFullState();
			nextDelay = Duration.ZERO;
		}

		if (response.hasPackagesAvailable()) {
			dispatchPackagesAvailable(response);
		}

		callbacks.onMessage(response);
		return nextDelay;
	}

	/**
	 * Processes a server error response: logs it, notifies the callbacks and computes the retry
	 * delay ({@code Unavailable} errors honor the server-provided retry hint and back off).
	 *
	 * @param errorResponse the error reported by the server
	 * @return the delay before the next poll
	 */
	private Duration processErrorResponse(final ServerErrorResponse errorResponse) {
		log.warn("The OpAMP server reported an error: {} - {}", errorResponse.getType(), errorResponse.getErrorMessage());
		callbacks.onErrorResponse(errorResponse);
		if (errorResponse.getType() == ServerErrorResponseType.ServerErrorResponseType_Unavailable) {
			final Duration floor = errorResponse.hasRetryInfo()
				? Duration.ofNanos(errorResponse.getRetryInfo().getRetryAfterNanoseconds())
				: null;
			return retrySchedule.nextDelayAfterFailure(floor);
		}
		return settings.getPollInterval();
	}

	/**
	 * Adopts the new instance UID assigned by the server and persists it.
	 *
	 * @param newInstanceUid the new instance UID; empty values are ignored
	 */
	private void adoptNewInstanceUid(final ByteString newInstanceUid) {
		if (newInstanceUid.isEmpty()) {
			return;
		}
		try {
			instanceUidStore.store(newInstanceUid.toByteArray());
			assembler.setInstanceUid(newInstanceUid);
			log.info("The OpAMP server assigned a new agent instance UID.");
		} catch (IOException e) {
			log.error("Failed to persist the new OpAMP instance UID: {}", e.getMessage());
			log.debug("Failed to persist the new OpAMP instance UID:", e);
		}
	}

	/**
	 * Dispatches a package offer to the registered packages handler.
	 *
	 * @param response the message carrying the package offer
	 */
	private void dispatchPackagesAvailable(final ServerToAgent response) {
		if (packagesHandler == null) {
			log.warn("Received an OpAMP package offer, but package management is not enabled; the offer is ignored.");
			return;
		}
		packageStatusAggregator.setServerProvidedAllPackagesHash(response.getPackagesAvailable().getAllPackagesHash());
		try {
			packagesHandler.onPackagesAvailable(response.getPackagesAvailable(), packageStatusAggregator, downloadContext);
		} catch (Exception e) {
			log.error("The OpAMP packages handler failed to process a package offer: {}", e.getMessage());
			log.debug("The OpAMP packages handler failed to process a package offer:", e);
		}
	}

	/**
	 * Handles a transport-level failure: requests a full-state resend, computes the backoff delay
	 * and notifies the callbacks.
	 *
	 * @param error the failure cause
	 * @param floor the server-suggested minimum delay ({@code Retry-After}), or {@code null}
	 * @return the delay before the next attempt
	 */
	private Duration onTransportFailure(final Throwable error, final Duration floor) {
		assembler.requestFullState();
		connected = false;
		final Duration delay = retrySchedule.nextDelayAfterFailure(floor);
		log.warn("OpAMP exchange failed ({}); next attempt in {} seconds.", error.getMessage(), delay.toSeconds());
		callbacks.onConnectFailed(error, delay);
		return delay;
	}

	/**
	 * Sends the final {@code AgentDisconnect} message. Runs on the polling thread.
	 */
	private void sendDisconnect() {
		try {
			transport.send(assembler.assembleDisconnect().toByteArray());
			log.debug("The OpAMP AgentDisconnect message was sent.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException | RuntimeException e) {
			log.debug("Failed to send the OpAMP AgentDisconnect message: {}", e.getMessage());
		}
	}

	/**
	 * Schedules the next poll after the given delay.
	 *
	 * @param delay the delay before the next poll
	 */
	private void scheduleNext(final Duration delay) {
		synchronized (lifecycleLock) {
			if (!started || stopped) {
				return;
			}
			nextPoll = executor.schedule(this::pollOnce, delay.toMillis(), TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Computes the capabilities bitmask advertised to the server.
	 *
	 * @return the capabilities bitmask
	 */
	private long computeCapabilities() {
		long capabilities = AgentCapabilities.AgentCapabilities_ReportsStatus.getNumber();
		if (settings.isReportHealth()) {
			capabilities |= AgentCapabilities.AgentCapabilities_ReportsHealth.getNumber();
		}
		if (packagesHandler != null) {
			capabilities |= AgentCapabilities.AgentCapabilities_AcceptsPackages.getNumber();
			capabilities |= AgentCapabilities.AgentCapabilities_ReportsPackageStatuses.getNumber();
		}
		return capabilities;
	}

	/**
	 * Returns the assembler receiving state updates: the real assembler once the client is
	 * started, or a buffer capturing values set before {@link #start()}.
	 *
	 * @return the current assembler
	 */
	private AgentToServerAssembler currentAssembler() {
		final AgentToServerAssembler current = assembler;
		return current != null ? current : earlyStateBuffer;
	}

	/**
	 * Copies the state captured before {@link #start()} into the real assembler.
	 */
	private void copyEarlyState() {
		final AgentToServer buffered = earlyStateBuffer.assemble();
		if (buffered.hasAgentDescription()) {
			assembler.setAgentDescription(buffered.getAgentDescription());
		}
		if (buffered.hasHealth()) {
			assembler.setHealth(buffered.getHealth());
		}
	}
}
