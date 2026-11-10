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

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.OpAmpConfig;
import org.metricshub.agent.config.UpgradeConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.fleet.AgentCredentialStore;
import org.metricshub.agent.fleet.AgentInstanceUid;
import org.metricshub.agent.fleet.EnrollmentCoordinator;
import org.metricshub.agent.fleet.FleetHeaders;
import org.metricshub.agent.upgrade.opamp.OpampUpgradeAdapter;
import org.metricshub.agent.upgrade.runner.DeploymentDetector;
import org.metricshub.agent.upgrade.runner.DeploymentKind;
import org.metricshub.opamp.client.OpampClient;
import org.metricshub.opamp.client.OpampClientCallbacks;
import org.metricshub.opamp.client.OpampClientSettings;
import org.metricshub.opamp.client.impl.HttpPollingOpampClient;
import org.metricshub.opamp.client.packages.OpampPackagesHandler;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.service.ApplicationStatusService;

/**
 * Application-level service owning the embedded OpAMP client.
 * <p>
 * The service lives outside the restartable {@link AgentContext}: a small supervisor tick
 * periodically re-reads the {@code central.opamp} configuration from the current context, starts, stops
 * or rebuilds the OpAMP client only when that configuration changes, and refreshes the agent
 * description and health reported to the OpAMP server. The client connection therefore survives
 * configuration reloads that do not touch the {@code central.opamp} channel.
 * </p>
 */
@Slf4j
public class OpAmpService {

	/**
	 * Period in seconds of the supervisor tick.
	 */
	static final long SUPERVISOR_PERIOD_SECONDS = 30;

	private final AgentContextHolder agentContextHolder;
	private final ApplicationStatusService applicationStatusService;
	private final Function<OpampClientSettings, OpampClient> clientFactory;
	private final ScheduledExecutorService supervisor;
	private final OpampPackagesHandler packagesHandler;

	/**
	 * Detects the deployment kind reported as the {@code installer.type} attribute. The detector
	 * caches its verdict, so the probes run at most once per process. Tests may replace it.
	 */
	private DeploymentDetector deploymentDetector = new DeploymentDetector();

	/**
	 * Whether an enrolled identity is persisted in the security directory. Tests may replace it.
	 */
	private BooleanSupplier enrolledIdentity = () -> hasReadyIdentity(AgentCredentialStore.standard());

	/**
	 * @param store the credential store
	 * @return whether it holds a ready identity, the uid of which the secret belongs to (a pending attempt holds no uid
	 *         yet); when it cannot be read, {@code true}: the uid is never put at risk
	 */
	static boolean hasReadyIdentity(final AgentCredentialStore store) {
		try {
			final AgentCredentialStore.LocalIdentity identity = store.read();
			if (identity.secret() != null) {
				Arrays.fill(identity.secret(), '\0');
			}
			if (identity.recoverySecret() != null) {
				Arrays.fill(identity.recoverySecret(), (byte) 0);
			}
			return identity.status() == AgentCredentialStore.Status.READY;
		} catch (IOException e) {
			return true;
		}
	}

	/**
	 * Says whether the agent may connect to Central. Tests may replace it.
	 */
	private EnrollmentCoordinator enrollment = EnrollmentCoordinator.global();

	private OpampClient client;
	private OpAmpConfig activeConfig;
	/**
	 * What of {@code central.auth} the client was started with ({@link EnrollmentCoordinator#authState}): a change
	 * reconnects through the enrollment.
	 */
	private List<String> activeAuth;
	private Boolean activeUpgradeEnabled;

	/**
	 * Creates the service with the default OpAMP client factory and no packages handler.
	 *
	 * @param agentContextHolder the holder of the current agent context
	 */
	public OpAmpService(final AgentContextHolder agentContextHolder) {
		this(agentContextHolder, (OpampPackagesHandler) null);
	}

	/**
	 * Creates the service with the default OpAMP client factory.
	 *
	 * @param agentContextHolder the holder of the current agent context
	 * @param packagesHandler    the handler receiving OpAMP package offers; {@code null} when
	 *                           automatic upgrades are not supported on this deployment
	 */
	public OpAmpService(final AgentContextHolder agentContextHolder, final OpampPackagesHandler packagesHandler) {
		this(
			agentContextHolder,
			packagesHandler,
			settings -> HttpPollingOpampClient.builder().withSettings(settings).withCallbacks(new LoggingCallbacks()).build()
		);
	}

	/**
	 * Creates the service with a caller-provided client factory and no packages handler (used by
	 * tests).
	 *
	 * @param agentContextHolder the holder of the current agent context
	 * @param clientFactory      the factory building an {@link OpampClient} from settings
	 */
	OpAmpService(
		final AgentContextHolder agentContextHolder,
		final Function<OpampClientSettings, OpampClient> clientFactory
	) {
		this(agentContextHolder, null, clientFactory);
	}

	/**
	 * Creates the service with caller-provided collaborators.
	 *
	 * @param agentContextHolder the holder of the current agent context
	 * @param packagesHandler    the handler receiving OpAMP package offers; may be {@code null}
	 * @param clientFactory      the factory building an {@link OpampClient} from settings
	 */
	OpAmpService(
		final AgentContextHolder agentContextHolder,
		final OpampPackagesHandler packagesHandler,
		final Function<OpampClientSettings, OpampClient> clientFactory
	) {
		this.agentContextHolder = agentContextHolder;
		this.packagesHandler = packagesHandler;
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

		final OpAmpConfig config = agentContext.getAgentConfig().getCentral().opamp();
		final boolean upgradeEnabled = isUpgradeEnabled(agentContext);
		final List<String> auth = EnrollmentCoordinator.authState(
			agentContext.getAgentConfig().getCentral(),
			enrollment.isReady()
		);
		if (
			!Objects.equals(config, activeConfig) ||
				!Objects.equals(upgradeEnabled, activeUpgradeEnabled) ||
				!Objects.equals(auth, activeAuth)
		) {
			reconfigure(agentContext, config, upgradeEnabled);
			// After it: the reconfiguration may have found the agent enrolled, which drops the key from the state
			activeAuth = EnrollmentCoordinator.authState(agentContext.getAgentConfig().getCentral(), enrollment.isReady());
		}

		if (client != null && client.isStarted()) {
			client.setAgentDescription(buildAgentDescription(agentContext));
			client.setHealth(OpAmpHealthMapper.map(applicationStatusService.reportApplicationStatus()));
		}
	}

	/**
	 * Builds the agent description reported to the OpAMP server: the pre-built {@code AgentInfo}
	 * attributes overridden by the agent-level {@code attributes:} and then by the
	 * {@code central.attributes}, plus the detected deployment kind.
	 *
	 * @param agentContext the current agent context
	 * @return the agent description to report
	 */
	private AgentDescription buildAgentDescription(final AgentContext agentContext) {
		return OpAmpAgentDescriptionMapper.map(
			agentContext.getAgentInfo(),
			agentContext.getAgentConfig(),
			detectDeploymentKindSafely()
		);
	}

	/**
	 * Detects the deployment kind, never letting a detection failure break the supervision: the
	 * {@code installer.type} attribute is then simply omitted and the OpAMP server treats the
	 * artifact type as unknown.
	 *
	 * @return the detected deployment kind, or {@code null} when detection failed
	 */
	private DeploymentKind detectDeploymentKindSafely() {
		try {
			return deploymentDetector.detect();
		} catch (Exception e) {
			log.warn("Cannot detect the MetricsHub deployment kind: {}", e.getMessage());
			log.debug("Cannot detect the MetricsHub deployment kind:", e);
			return null;
		}
	}

	/**
	 * Replaces the deployment detector (used by tests).
	 *
	 * @param deploymentDetector the detector to use
	 */
	void setDeploymentDetector(final DeploymentDetector deploymentDetector) {
		this.deploymentDetector = deploymentDetector;
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
	 * Replaces the check of a persisted enrolled identity (used by tests).
	 *
	 * @param enrolledIdentity whether one exists
	 */
	void setEnrolledIdentity(final BooleanSupplier enrolledIdentity) {
		this.enrolledIdentity = enrolledIdentity;
	}

	/**
	 * Applies a new {@code central.opamp} configuration: stops the running client and starts a new one
	 * when the configuration enables OpAMP.
	 *
	 * @param agentContext the current agent context
	 * @param newConfig    the new OpAMP configuration
	 */
	private void reconfigure(final AgentContext agentContext, final OpAmpConfig newConfig, final boolean upgradeEnabled) {
		if (client != null) {
			client.stop("OpAMP configuration changed");
			client = null;
		}
		activeConfig = newConfig;
		activeUpgradeEnabled = upgradeEnabled;

		// Asked first, whatever the channel: it also stops an enrollment a reload no longer configures
		final boolean enrolled = enrollment.ready(agentContext);
		if (newConfig == null || !newConfig.isEnabled()) {
			log.info("OpAMP is disabled.");
			return;
		}
		final String endpoint = newConfig.getEndpoint();
		if (endpoint == null || endpoint.isBlank()) {
			log.warn("OpAMP is enabled but no endpoint is configured; the OpAMP client is not started.");
			return;
		}
		if (!enrolled) {
			// Retried on the next tick: with central.auth, nothing connects before the agent is enrolled
			activeConfig = null;
			log.debug("OpAMP waits for the enrollment of the agent into MetricsHub Central.");
			return;
		}

		OpampClient newClient = null;
		try {
			newClient = clientFactory.apply(buildSettings(newConfig, EnrollmentCoordinator.usesToken(agentContext)));
			if (packagesHandler != null && upgradeEnabled) {
				// Advertises the AcceptsPackages/ReportsPackageStatuses capabilities
				newClient.setPackagesHandler(packagesHandler);
				if (packagesHandler instanceof OpampUpgradeAdapter adapter) {
					// Rebind the status sink to the new client so upgrade transitions occurring
					// before the next package offer still reach the live client
					adapter.bindSink(newClient.packageStatusSink());
				}
			}
			// Report a complete first message
			newClient.setAgentDescription(buildAgentDescription(agentContext));
			newClient.setHealth(OpAmpHealthMapper.map(applicationStatusService.reportApplicationStatus()));
			newClient.start();
			if (packagesHandler instanceof OpampUpgradeAdapter adapter && upgradeEnabled) {
				// The client seeded its package statuses from a pre-start snapshot: re-report the
				// latest snapshot so a transition that raced the handoff is not lost
				adapter.republishSnapshot();
			}
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
	 * Indicates whether automatic upgrades are enabled in the agent configuration.
	 *
	 * @param agentContext the current agent context
	 * @return {@code true} when {@code central.upgrade} is enabled
	 */
	private static boolean isUpgradeEnabled(final AgentContext agentContext) {
		final UpgradeConfig upgrade = agentContext.getAgentConfig().getCentral().getUpgrade();
		return upgrade != null && upgrade.isEnabled();
	}

	/**
	 * Builds the OpAMP client settings from the agent configuration. Header values encrypted with
	 * the MetricsHub keystore are decrypted; plain values pass through unchanged.
	 *
	 * @param config the OpAMP configuration
	 * @return the client settings
	 */
	OpampClientSettings buildSettings(final OpAmpConfig config) {
		return buildSettings(config, false);
	}

	/**
	 * Builds the OpAMP client settings. With {@code central.auth}, every POST carries the current token, a
	 * {@code 401} drops it so the next POST carries a new one, and the uid is the enrolled one: a
	 * {@code new_instance_uid} from the server is refused.
	 *
	 * @param config    the OpAMP configuration
	 * @param usesToken whether the agent authenticates with its enrolled identity's token
	 * @return the client settings
	 */
	OpampClientSettings buildSettings(final OpAmpConfig config, final boolean usesToken) {
		final Map<String, String> headers = FleetHeaders.decrypt(config.getHeaders(), "OpAMP");
		final EnrollmentCoordinator coordinator = enrollment;
		final long generation = coordinator.tokenGeneration();
		return OpampClientSettings.builder()
			.withEndpoint(URI.create(config.getEndpoint().trim()))
			.withHeaders(headers)
			.withHeadersSupplier(usesToken ? () -> coordinator.headers(headers, generation) : null)
			.withOnUnauthorized(usesToken ? coordinator::invalidateToken : () -> {})
			// Never once an identity was enrolled, even with central.auth removed: the uid is the one its secret belongs to
			.withAcceptsNewInstanceUid(!usesToken && !enrolledIdentity.getAsBoolean())
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
			.withInstanceUidFile(AgentInstanceUid.file())
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
	 * Callbacks logging that the OpAMP server answered. A failure is not logged here: the client logs
	 * every one itself, with its cause and the next attempt.
	 */
	static class LoggingCallbacks implements OpampClientCallbacks {

		@Override
		public void onConnect() {
			log.info("Connected to the OpAMP server.");
		}
	}
}
