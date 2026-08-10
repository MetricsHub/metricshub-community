package org.metricshub.agent.opamp;

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

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.OpAmpConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.opamp.client.OpampClient;
import org.metricshub.opamp.client.OpampClientCallbacks;
import org.metricshub.opamp.client.OpampClientSettings;
import org.metricshub.opamp.client.impl.HttpPollingOpampClient;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.service.ApplicationStatusService;

/**
 * Application-level service owning the embedded OpAMP client.
 * <p>
 * The service lives outside the restartable {@link AgentContext}: a small supervisor tick
 * periodically re-reads the {@code opamp:} configuration from the current context, starts, stops
 * or rebuilds the OpAMP client only when that configuration changes, and refreshes the agent
 * description and health reported to the OpAMP server. The client connection therefore survives
 * configuration reloads that do not touch the {@code opamp:} section.
 * </p>
 */
@Slf4j
public class OpAmpService {

	/**
	 * Name of the file persisting the OpAMP agent instance UID, stored in the MetricsHub
	 * security directory.
	 */
	public static final String OPAMP_INSTANCE_UID_FILENAME = "opamp-instance-uid";

	/**
	 * Period in seconds of the supervisor tick.
	 */
	static final long SUPERVISOR_PERIOD_SECONDS = 30;

	private final AgentContextHolder agentContextHolder;
	private final ApplicationStatusService applicationStatusService;
	private final Function<OpampClientSettings, OpampClient> clientFactory;
	private final ScheduledExecutorService supervisor;

	private OpampClient client;
	private OpAmpConfig activeConfig;

	/**
	 * Creates the service with the default OpAMP client factory.
	 *
	 * @param agentContextHolder the holder of the current agent context
	 */
	public OpAmpService(final AgentContextHolder agentContextHolder) {
		this(agentContextHolder, settings ->
			HttpPollingOpampClient.builder().withSettings(settings).withCallbacks(new LoggingCallbacks()).build()
		);
	}

	/**
	 * Creates the service with a caller-provided client factory (used by tests).
	 *
	 * @param agentContextHolder the holder of the current agent context
	 * @param clientFactory      the factory building an {@link OpampClient} from settings
	 */
	OpAmpService(
		final AgentContextHolder agentContextHolder,
		final Function<OpampClientSettings, OpampClient> clientFactory
	) {
		this.agentContextHolder = agentContextHolder;
		this.applicationStatusService = new ApplicationStatusService(agentContextHolder);
		this.clientFactory = clientFactory;
		this.supervisor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "metricshub-opamp-supervisor");
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Starts the supervisor tick.
	 */
	public void start() {
		supervisor.scheduleWithFixedDelay(this::superviseSafely, 0, SUPERVISOR_PERIOD_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * Stops the supervisor and the OpAMP client. Called from the JVM shutdown hook.
	 */
	public synchronized void shutdown() {
		supervisor.shutdownNow();
		if (client != null) {
			client.stop("Agent shutting down");
			client = null;
		}
	}

	/**
	 * Runs one supervision pass, never letting an exception kill the supervisor schedule.
	 */
	private void superviseSafely() {
		try {
			supervise();
		} catch (Exception e) {
			log.error("OpAMP supervision failed: {}", e.getMessage());
			log.debug("OpAMP supervision failed:", e);
		}
	}

	/**
	 * Reconciles the OpAMP client with the current configuration and refreshes the reported
	 * agent description and health.
	 */
	synchronized void supervise() {
		final AgentContext agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null || agentContext.getAgentConfig() == null) {
			return;
		}

		final OpAmpConfig config = agentContext.getAgentConfig().getOpamp();
		if (!Objects.equals(config, activeConfig)) {
			reconfigure(agentContext, config);
		}

		if (client != null && client.isStarted()) {
			client.setAgentDescription(OpAmpAgentDescriptionMapper.map(agentContext.getAgentInfo()));
			client.setHealth(OpAmpHealthMapper.map(applicationStatusService.reportApplicationStatus()));
		}
	}

	/**
	 * Applies a new {@code opamp:} configuration: stops the running client and starts a new one
	 * when the configuration enables OpAMP.
	 *
	 * @param agentContext the current agent context
	 * @param newConfig    the new OpAMP configuration
	 */
	private void reconfigure(final AgentContext agentContext, final OpAmpConfig newConfig) {
		if (client != null) {
			client.stop("OpAMP configuration changed");
			client = null;
		}
		activeConfig = newConfig;

		if (newConfig == null || !newConfig.isEnabled()) {
			log.info("OpAMP is disabled.");
			return;
		}
		final String endpoint = newConfig.getEndpoint();
		if (endpoint == null || endpoint.isBlank()) {
			log.warn("OpAMP is enabled but no endpoint is configured; the OpAMP client is not started.");
			return;
		}

		OpampClient newClient = null;
		try {
			newClient = clientFactory.apply(buildSettings(newConfig));
			// Report a complete first message
			newClient.setAgentDescription(OpAmpAgentDescriptionMapper.map(agentContext.getAgentInfo()));
			newClient.setHealth(OpAmpHealthMapper.map(applicationStatusService.reportApplicationStatus()));
			newClient.start();
			client = newClient;
		} catch (Exception e) {
			// Release the resources of a client whose startup failed: the retry below builds a
			// fresh one, and leaking an executor and HTTP client on every attempt must not happen.
			closeQuietly(newClient);
			// Clear the active configuration so the next supervisor tick retries the startup:
			// a transient failure (e.g. missing CA file) must not disable OpAMP until the
			// configuration changes or the agent restarts.
			activeConfig = null;
			log.error("Failed to start the OpAMP client on {}: {}", endpoint, e.getMessage());
			log.debug("Failed to start the OpAMP client:", e);
		}
	}

	/**
	 * Stops a client best-effort, swallowing any secondary failure.
	 *
	 * @param failedClient the client to stop; {@code null} is ignored
	 */
	private static void closeQuietly(final OpampClient failedClient) {
		if (failedClient == null) {
			return;
		}
		try {
			failedClient.stop("OpAMP client startup failed");
		} catch (Exception stopError) {
			log.debug("Failed to release the OpAMP client that could not start:", stopError);
		}
	}

	/**
	 * Builds the OpAMP client settings from the agent configuration. Header values encrypted with
	 * the MetricsHub keystore are decrypted; plain values pass through unchanged.
	 *
	 * @param config the OpAMP configuration
	 * @return the client settings
	 */
	OpampClientSettings buildSettings(final OpAmpConfig config) {
		final Map<String, String> headers = new HashMap<>();
		config.getHeaders().forEach((key, value) -> headers.put(key, decrypt(value)));

		return OpampClientSettings.builder()
			.withEndpoint(URI.create(config.getEndpoint().trim()))
			.withHeaders(headers)
			.withCertificateFile(config.getCertificateFile())
			.withPollInterval(
				Duration.ofSeconds(
					atLeastOneSecond("pollInterval", config.getPollInterval(), OpAmpConfig.DEFAULT_POLL_INTERVAL)
				)
			)
			.withRequestTimeout(
				Duration.ofSeconds(
					atLeastOneSecond("requestTimeout", config.getRequestTimeout(), OpAmpConfig.DEFAULT_REQUEST_TIMEOUT)
				)
			)
			.withInstanceUidFile(resolveInstanceUidFile())
			.withReportHealth(config.isReportHealth())
			.build();
	}

	/**
	 * Guards a duration setting against zero or negative values (e.g. {@code pollInterval: 0} or
	 * a sub-second duration collapsing to zero seconds), which would turn the polling loop into a
	 * tight loop hammering the OpAMP server.
	 *
	 * @param settingName  the name of the setting, used for logging
	 * @param seconds      the configured value in seconds
	 * @param defaultValue the default value in seconds applied when the configured value is invalid
	 * @return a positive number of seconds
	 */
	private static long atLeastOneSecond(final String settingName, final long seconds, final long defaultValue) {
		if (seconds < 1) {
			log.warn(
				"Invalid OpAMP {} ({} seconds); using the default value of {} seconds.",
				settingName,
				seconds,
				defaultValue
			);
			return defaultValue;
		}
		return seconds;
	}

	/**
	 * Resolves the file persisting the OpAMP instance UID: it lives next to the MetricsHub
	 * keystore in the security directory, which survives upgrades on all platforms.
	 *
	 * @return the instance UID file path
	 */
	static Path resolveInstanceUidFile() {
		return PasswordEncrypt.getKeyStoreFile(true)
			.toPath()
			.toAbsolutePath()
			.getParent()
			.resolve(OPAMP_INSTANCE_UID_FILENAME);
	}

	/**
	 * Decrypts a configuration value with the MetricsHub keystore; plain values are returned
	 * unchanged.
	 *
	 * @param value the raw configuration value
	 * @return the decrypted value
	 */
	private static String decrypt(final String value) {
		if (value == null) {
			return null;
		}
		return new String(ConfigHelper.decrypt(value.toCharArray()));
	}

	/**
	 * Callbacks logging the OpAMP connectivity transitions.
	 */
	static class LoggingCallbacks implements OpampClientCallbacks {

		@Override
		public void onConnect() {
			log.info("Connected to the OpAMP server.");
		}

		@Override
		public void onConnectFailed(final Throwable error, final Duration nextAttemptDelay) {
			log.warn(
				"Connection to the OpAMP server failed ({}); next attempt in {} seconds.",
				error == null ? "unknown error" : error.getMessage(),
				nextAttemptDelay.toSeconds()
			);
		}
	}
}
