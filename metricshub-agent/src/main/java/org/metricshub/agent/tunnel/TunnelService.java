package org.metricshub.agent.tunnel;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.TunnelConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.fleet.AgentInstanceUid;
import org.metricshub.agent.fleet.EnrollmentCoordinator;
import org.metricshub.agent.fleet.FleetHeaders;
import org.metricshub.agent.tunnel.client.TunnelClient;
import org.metricshub.agent.tunnel.client.TunnelListener;
import org.metricshub.agent.tunnel.client.TunnelSettings;
import org.metricshub.agent.tunnel.protocol.AgentDescriptor;
import org.metricshub.agent.tunnel.protocol.HostDescriptor;
import org.metricshub.agent.tunnel.protocol.TunnelMessage;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.AgentRegister;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.AgentRegistered;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.HostsUpdated;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.ToolInvoke;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Supervises the central tunnel for the lifetime of the JVM, outside the restartable
 * {@link AgentContext}: a periodic tick reads the current {@code central.tunnel:} configuration, (re)builds the
 * tunnel client when it changes, retries a failed startup on the next tick, and pushes
 * {@code hosts.updated} when a configuration reload changed the monitored hosts.
 * <p>
 * The registry snapshot is built per configuration and re-advertised on every (re)connection. The connector and
 * protocol tools follow the agent context but are not advertised (Central asks {@code ListHostTools} per host). A
 * reload that changes the advertised tools, or the declaration of a host tool Central may have been told about,
 * restarts the tunnel; one that only rebuilds the same tools refreshes the bridge's callbacks without reconnecting.
 * </p>
 */
@Slf4j
public class TunnelService {

	static final long SUPERVISOR_PERIOD_SECONDS = 30;

	/**
	 * Loads the persistent agent uid; a seam for tests, which must not touch the security directory.
	 */
	@FunctionalInterface
	interface UidSupplier {
		String load() throws IOException;
	}

	private final AgentContextHolder agentContextHolder;
	private final ToolCallbackProvider toolCallbackProvider;
	private final BiFunction<TunnelSettings, TunnelListener, TunnelClient> clientFactory;
	private final UidSupplier uidSupplier;
	/**
	 * Says whether the agent may connect to Central. Tests may replace it.
	 */
	private EnrollmentCoordinator enrollment = EnrollmentCoordinator.global();
	private final ExecutorService toolWorkers;
	private final ScheduledExecutorService supervisor;

	/**
	 * How long a renewed token may wait for a moment with no invocation in flight before the tunnel reconnects
	 * anyway: below the renewal margin (7 minutes before {@code exp}), so the old token never expires first.
	 */
	static final Duration FORCED_RENEWAL = Duration.ofMinutes(5);
	private boolean usesToken;
	/**
	 * Margin kept before the expiry of the token a connection was opened with, for the reconnection to happen first.
	 */
	static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);
	private String renewedAuthorization;
	private Instant forcedRenewalAt = Instant.MAX;

	private TunnelClient client;
	private ToolBridge bridge;
	private ToolRegistrySnapshot snapshot;
	/**
	 * The context generation the snapshot was built for: the tool set is re-read once per reload, not per tick.
	 */
	private long snapshotGeneration;
	private TunnelConfig activeConfig;
	/**
	 * What of {@code central.auth} the tunnel was started with ({@link EnrollmentCoordinator#authState}): a change
	 * reconnects through the enrollment.
	 */
	private List<String> activeAuth;
	// Written by the supervisor and by the tunnel thread building a registration. Volatile rather
	// than guarded: the tunnel thread must never wait on this service's monitor, which the
	// supervisor holds while stop() waits for the tunnel thread to run its closing task.
	private volatile long advertisedGeneration;
	private volatile List<HostDescriptor> advertisedHosts = List.of();
	/**
	 * The identity the current session was registered with.
	 *
	 * <p>Kept because the protocol has no way to amend it: {@code agent.register} is the only frame
	 * that carries a descriptor, so a reload that renames the host or adds an attribute can only
	 * reach Central through a new registration.
	 */
	private volatile AgentDescriptor advertisedDescriptor;

	/**
	 * @param agentContextHolder   the running agent context
	 * @param toolCallbackProvider the tools the agent exposes
	 */
	public TunnelService(final AgentContextHolder agentContextHolder, final ToolCallbackProvider toolCallbackProvider) {
		this(agentContextHolder, toolCallbackProvider, TunnelClient::new, AgentInstanceUid::loadOrCreate);
	}

	TunnelService(
		final AgentContextHolder agentContextHolder,
		final ToolCallbackProvider toolCallbackProvider,
		final BiFunction<TunnelSettings, TunnelListener, TunnelClient> clientFactory,
		final UidSupplier uidSupplier
	) {
		this.agentContextHolder = agentContextHolder;
		this.toolCallbackProvider = toolCallbackProvider;
		this.clientFactory = clientFactory;
		this.uidSupplier = uidSupplier;
		this.supervisor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "metricshub-tunnel-supervisor");
			thread.setDaemon(true);
			return thread;
		});
		// One pool for the process, not one per bridge. A callback that ignores its interruption
		// keeps its thread through a reconfiguration too, so a ceiling that reset on every
		// configuration change would bound nothing: reload often enough with calls stuck and the
		// agent accumulates a fresh poolful of threads each time.
		this.toolWorkers = ToolBridge.newWorkerPool();
	}

	/**
	 * Replaces the enrollment coordinator (used by tests).
	 *
	 * @param enrollment the coordinator to use
	 */
	void setEnrollment(final EnrollmentCoordinator enrollment) {
		this.enrollment = enrollment;
	}

	/**
	 * Starts the supervision; the first tick runs immediately.
	 */
	public void start() {
		supervisor.scheduleWithFixedDelay(this::superviseSafely, 0, SUPERVISOR_PERIOD_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * Stops the supervision and closes the tunnel.
	 */
	public synchronized void shutdown() {
		supervisor.shutdownNow();
		closeClient("Agent shutting down");
		toolWorkers.shutdownNow();
	}

	private void superviseSafely() {
		try {
			supervise();
		} catch (Exception e) {
			log.error("Central tunnel supervision failed: {}", e.getMessage());
			log.debug("Central tunnel supervision failed:", e);
		}
	}

	synchronized void supervise() {
		final AgentContext agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null || agentContext.getAgentConfig() == null) {
			return;
		}
		final TunnelConfig config = agentContext.getAgentConfig().getCentral().tunnel();
		final List<String> auth = EnrollmentCoordinator.authState(
			agentContext.getAgentConfig().getCentral(),
			enrollment.isReady()
		);
		if (!Objects.equals(config, activeConfig) || !Objects.equals(auth, activeAuth)) {
			activeAuth = auth;
			reconfigure(config);
			return;
		}
		if (client != null && usesToken) {
			reconnectWithRenewedToken();
		}
		if (client != null && agentContextHolder.getGeneration() != snapshotGeneration) {
			// A configuration reload may have changed the connector and protocol tools. They are not advertised
			// (Central asks ListHostTools per host), so they only need the bridge's callbacks refreshed; a
			// changed advertised set means a new bridge and a new registration: the same as a configuration change.
			final ToolRegistrySnapshot current = snapshotOf(config.getExcludedTools());
			// A host tool declared otherwise counts too: Central would invoke it with what ListHostTools told it
			if (
				!current.revision().equals(snapshot.revision()) ||
					!current.hostToolsRevision().equals(snapshot.hostToolsRevision())
			) {
				log.info(
					"Central tunnel: the tool set changed; restarting the tunnel to advertise {} tool(s).",
					current.tools().size()
				);
				reconfigure(config);
				return;
			}
			// Same advertised set, rebuilt callbacks: the ones the bridge holds belong to the replaced
			// context, whose extensions the lifecycle service is about to close
			snapshot = current;
			bridge.update(current);
			log.debug("Central tunnel: tool callbacks refreshed for context generation {}.", snapshotGeneration);
		}
		if (client != null && client.isConnected() && agentContextHolder.getGeneration() != advertisedGeneration) {
			// A configuration reload swapped the agent context: re-advertise what actually changed
			final ContextSnapshot current = readContextSnapshot();
			final AgentDescriptor descriptor = AgentDescriptorMapper.map(current.context());
			if (!descriptor.equals(advertisedDescriptor)) {
				// There is no frame that amends an identity: agent.register is the only one that
				// carries a descriptor, so a renamed host or a new attribute reaches Central by
				// registering again. Reconnecting rebuilds the registration from the current context,
				// which is what the protocol already promises a reconnection does -- and it carries
				// the hosts with it, so nothing below needs to run.
				log.info("Central tunnel: the agent's identity changed; reconnecting to re-register.");
				client.reconnect("Agent description changed");
				advertisedDescriptor = descriptor;
				return;
			}
			final List<HostDescriptor> hosts = HostInventory.from(current.context());
			if (!hosts.equals(advertisedHosts)) {
				if (!client.send(new HostsUpdated(hosts))) {
					// Too large for this server to accept, so it was not sent. Recording it as
					// advertised anyway would leave every later tick seeing nothing to do, and
					// Central routing on the old inventory for as long as the process ran. Left
					// unrecorded, the next tick tries again -- which costs a log line every thirty
					// seconds and recovers by itself if hosts are removed or the server's cap raised.
					return;
				}
				log.info("Central tunnel: host inventory updated ({} host(s)).", hosts.size());
			}
			advertisedHosts = hosts;
			advertisedGeneration = current.generation();
		}
	}

	/**
	 * A context and the generation it belongs to.
	 *
	 * @param context    the agent context
	 * @param generation the generation of that context
	 */
	private record ContextSnapshot(AgentContext context, long generation) {}

	/**
	 * Reads the current context together with its generation.
	 * <p>
	 * The generation is read <em>first</em> and re-checked afterwards: recording a generation newer
	 * than the context it was taken from would make the next tick believe the new inventory had
	 * already been advertised, and Central would keep routing to stale hosts until the next
	 * reload or reconnection. The reverse mistake is harmless — the hosts are compared before
	 * anything is sent.
	 * </p>
	 *
	 * @return a consistent context and generation pair
	 */
	private ContextSnapshot readContextSnapshot() {
		long generation = agentContextHolder.getGeneration();
		AgentContext context = agentContextHolder.getAgentContext();
		if (generation != agentContextHolder.getGeneration()) {
			generation = agentContextHolder.getGeneration();
			context = agentContextHolder.getAgentContext();
		}
		return new ContextSnapshot(context, generation);
	}

	private void reconfigure(final TunnelConfig newConfig) {
		closeClient("Central tunnel configuration changed");
		activeConfig = newConfig;

		// Asked first, whatever the channel: it also stops an enrollment a reload no longer configures
		final boolean enrolled = enrollment.ready(agentContextHolder.getAgentContext());
		if (newConfig == null || !newConfig.isEnabled()) {
			log.info("Central tunnel is disabled.");
			return;
		}
		final String endpoint = newConfig.getEndpoint();
		if (endpoint == null || endpoint.isBlank()) {
			log.warn("Central tunnel is enabled but no endpoint is configured; the tunnel is not started.");
			return;
		}
		if (!enrolled) {
			// Retried on the next tick: with central.auth, nothing connects before the agent is enrolled
			activeConfig = null;
			log.debug("Central tunnel waits for the enrollment of the agent into MetricsHub Central.");
			return;
		}

		TunnelClient newClient = null;
		try {
			snapshot = snapshotOf(newConfig.getExcludedTools());
			usesToken = EnrollmentCoordinator.usesToken(agentContextHolder.getAgentContext());
			final Map<String, String> headers = FleetHeaders.decrypt(newConfig.getHeaders(), "tunnel");
			final TunnelSettings settings = new TunnelSettings(
				URI.create(endpoint.trim()),
				headers,
				newConfig.getCertificateFile(),
				// Enrolled, the uid is the one Central assigned; the agent never generates one
				usesToken ? enrollment.agentUid() : uidSupplier.load(),
				Duration.ofSeconds(atLeastOneSecond(newConfig.getHeartbeatInterval())),
				TunnelSettings.DEFAULT_CONNECT_TIMEOUT,
				TunnelSettings.DEFAULT_MAX_BACKOFF,
				newConfig.isInsecure()
			);
			if (settings.insecure() && !"wss".equalsIgnoreCase(settings.endpoint().getScheme())) {
				log.warn(
					"Central tunnel: central.insecure is set and {} is in cleartext, so the handshake headers -- the credential included -- travel unencrypted.",
					endpoint
				);
			}
			// The bridge answers on the client it was built for AND on the connection that asked.
			// Neither half is enough alone: resolving the current client at send time would let a
			// paused worker answer over a configuration that replaced its own, and pinning the
			// client would still let it answer over that client's next reconnection. Both are
			// dropped -- a stopped client discards what it is handed, and a live one compares the
			// generation on its own thread.
			final AtomicReference<TunnelClient> owner = new AtomicReference<>();
			final ToolBridge newBridge = new ToolBridge(snapshot, (message, generation) -> {
				final TunnelClient target = owner.get();
				if (target != null) {
					target.send(message, generation);
				}
			}, toolWorkers);
			newClient = clientFactory.apply(settings, new BridgeListener(newBridge));
			if (usesToken) {
				final EnrollmentCoordinator coordinator = enrollment;
				newClient.useCredential(() -> coordinator.headers(headers), coordinator::invalidateToken);
			}
			owner.set(newClient);
			bridge = newBridge;
			client = newClient;
			newClient.start();
			log.info("Central tunnel started toward {} advertising {} tool(s).", endpoint, snapshot.tools().size());
		} catch (Exception e) {
			if (newClient != null) {
				newClient.stop("Central tunnel startup failed");
			}
			// Clear the active configuration so the next supervisor tick retries the startup: a
			// transient failure (e.g. missing CA file) must not disable the tunnel until a restart.
			activeConfig = null;
			client = null;
			closeBridge();
			log.error("Failed to start the central tunnel toward {}: {}", endpoint, e.getMessage());
			log.debug("Failed to start the central tunnel:", e);
		}
	}

	/**
	 * Reconnects the tunnel once its token was renewed, at the first tick with no invocation in flight, or whatever
	 * runs after {@link #FORCED_RENEWAL}, or {@link #EXPIRY_MARGIN} before the previous token expires when that comes
	 * first (a renewal delayed by an outage), so invocations never postpone it past that expiry. Asking for the token
	 * here also renews it on time, off the tunnel thread.
	 */
	private void reconnectWithRenewedToken() {
		final String authorization;
		try {
			authorization = "Bearer " + enrollment.currentToken();
		} catch (IOException e) {
			log.debug("Central tunnel: no token to check: {}", e.getMessage());
			return;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		final Instant now = Instant.now();
		if (!authorization.equals(renewedAuthorization)) {
			final Instant previousExpiry = renewedAuthorization == null
				? null
				: EnrollmentCoordinator.expiryOf(renewedAuthorization.substring("Bearer ".length()));
			final Instant forced = now.plus(FORCED_RENEWAL);
			forcedRenewalAt = previousExpiry == null || forced.isBefore(previousExpiry.minus(EXPIRY_MARGIN))
				? forced
				: previousExpiry.minus(EXPIRY_MARGIN);
			renewedAuthorization = authorization;
		}
		final boolean overdue = !now.isBefore(forcedRenewalAt);
		// The client decides on its own thread, where invocations arrive and answers leave
		client.credentialRenewed(authorization, overdue || bridge == null ? null : bridge::quiescentMark);
	}

	private void closeClient(final String reason) {
		if (client != null) {
			client.stop(reason);
			client = null;
		}
		closeBridge();
		advertisedHosts = List.of();
		advertisedDescriptor = null;
		advertisedGeneration = 0;
	}

	/**
	 * Releases a bridge. What its outstanding work might still answer is not this method's problem:
	 * every answer is bound to the connection that asked, and the client it was bound to is either
	 * stopped by now or on a newer generation, so a late result is dropped rather than delivered.
	 * The worker pool is deliberately untouched -- it belongs to the process.
	 */
	private void closeBridge() {
		if (bridge != null) {
			bridge.shutdown();
			bridge = null;
		}
	}

	private static long atLeastOneSecond(final long seconds) {
		if (seconds < 1) {
			log.warn(
				"Invalid central.tunnel.heartbeatInterval {} second(s); using the default {} second(s).",
				seconds,
				TunnelConfig.DEFAULT_HEARTBEAT_INTERVAL
			);
			return TunnelConfig.DEFAULT_HEARTBEAT_INTERVAL;
		}
		return seconds;
	}

	/**
	 * Reactions to the tunnel: the registration payload reflects the current context, invocations go
	 * to the bridge.
	 */
	private final class BridgeListener implements TunnelListener {

		private final ToolBridge tunnelBridge;

		private BridgeListener(final ToolBridge tunnelBridge) {
			this.tunnelBridge = tunnelBridge;
		}

		@Override
		public AgentRegister buildRegistration() {
			final ContextSnapshot current = readContextSnapshot();
			final List<HostDescriptor> hosts = HostInventory.from(current.context());
			final AgentDescriptor descriptor = AgentDescriptorMapper.map(current.context());
			advertisedHosts = hosts;
			advertisedDescriptor = descriptor;
			advertisedGeneration = current.generation();
			return new AgentRegister(
				TunnelMessage.PROTOCOL_VERSION,
				descriptor,
				snapshot.revision(),
				snapshot.tools(),
				hosts
			);
		}

		@Override
		public void onRegistered(final AgentRegistered registered) {
			tunnelBridge.setLimits(registered);
		}

		@Override
		public void onDisconnected(final int code, final String reason, final long generation) {
			// Central discarded whatever it had asked for. The generation binding already stops
			// an answer from reaching the next session; this is about the slot, which the bridge
			// outliving the connection would otherwise hold until the invocation's own deadline.
			tunnelBridge.cancelGeneration(generation);
		}

		@Override
		public void onInvoke(final ToolInvoke invoke, final long generation) {
			// The generation travels with the request and comes back with the answer: the server
			// discarded what it had asked for when the connection dropped, and a result correlated
			// with a request the next session never made must never reach it.
			tunnelBridge.invoke(invoke, generation);
		}
	}

	/**
	 * Builds the snapshot from one context generation: the callbacks the provider builds and the context
	 * {@code ListHostTools} answers from must be the same configuration's, or the lookup could list a tool its
	 * callback cannot run. A reload published while it is built is caught by the generation and the snapshot rebuilt.
	 *
	 * @param excludedTools the tools never advertised nor invokable
	 * @return the snapshot, its generation recorded in {@code snapshotGeneration}
	 */
	ToolRegistrySnapshot snapshotOf(final Collection<String> excludedTools) {
		long generation;
		ToolRegistrySnapshot built;
		do {
			generation = agentContextHolder.getGeneration();
			built = ToolRegistrySnapshot.from(toolCallbackProvider, excludedTools, agentContextHolder.getAgentContext());
		} while (generation != agentContextHolder.getGeneration());
		snapshotGeneration = generation;
		return built;
	}
}
