package org.sentrysoftware.metricshub.agent.context;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.metricshub.agent.config.AgentConfig;
import org.sentrysoftware.metricshub.agent.context.ApplicationProperties.Project;
import org.sentrysoftware.metricshub.agent.deserialization.PostConfigDeserializer;
import org.sentrysoftware.metricshub.agent.helper.AgentConstants;
import org.sentrysoftware.metricshub.agent.helper.ConfigHelper;
import org.sentrysoftware.metricshub.agent.helper.OtelConfigHelper;
import org.sentrysoftware.metricshub.agent.helper.PostConfigDeserializeHelper;
import org.sentrysoftware.metricshub.agent.service.OtelCollectorProcessService;
import org.sentrysoftware.metricshub.agent.service.TaskSchedulingService;
import org.sentrysoftware.metricshub.engine.common.helpers.JsonHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.connector.parser.EnvironmentProcessor;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * AgentContext represents the context of the MetricsHub agent, containing various components such as agent information,
 * configuration, telemetry managers, OpenTelemetry SDK configuration, OtelCollector process service, task scheduling service,
 * and metric definitions. It also includes methods for building the context and logging product information.
 */
@Data
@Slf4j
public class AgentContext {

	private AgentInfo agentInfo;
	private File configFile;
	private AgentConfig agentConfig;
	private ConnectorStore connectorStore;
	private String pid;
	private Map<String, String> otelSdkConfiguration;
	private Map<String, Map<String, TelemetryManager>> telemetryManagers;
	private OtelCollectorProcessService otelCollectorProcessService;
	private TaskSchedulingService taskSchedulingService;
	private MetricDefinitions hostMetricDefinitions;
	protected ExtensionManager extensionManager;

	/**
	 * Instantiate the global context
	 *
	 * @param alternateConfigFile Alternation configuration file passed by the user
	 * @param extensionManager    Manages and aggregates various types of extensions used within MetricsHub.
	 * @throws IOException Signals that an I/O exception has occurred
	 */
	public AgentContext(final String alternateConfigFile, final ExtensionManager extensionManager) throws IOException {
		this.extensionManager = extensionManager;
		build(alternateConfigFile, true);
	}

	/**
	 * Builds the agent context
	 * @param alternateConfigFile Alternation configuration file passed by the user
	 * @param createConnectorStore Whether we should create a new connector store
	 * @throws IOException Signals that an I/O exception has occurred
	 */
	public void build(final String alternateConfigFile, final boolean createConnectorStore) throws IOException {
		final long startTime = System.nanoTime();

		// Find the configuration file
		configFile = ConfigHelper.findConfigFile(alternateConfigFile);

		// Load the pre configuration (logging configuration & connectors patch path)
		// before starting any processing because we want to log any potential error
		// at the start up of the application.
		final PreConfig preConfig = loadPreConfig();

		// Configure the global logger
		ConfigHelper.configureGlobalLogger(preConfig.getLoggerLevel(), preConfig.getOutputDirectory());

		log.info("Starting MetricsHub Agent...");

		// Set the current PID
		pid = findPid();

		if (createConnectorStore) {
			connectorStore = ConfigHelper.buildConnectorStore(extensionManager, preConfig.getPatchDirectory());
		}

		// Initialize agent information
		agentInfo = new AgentInfo();

		// Read the agent configuration file (Default: metricshub.yaml)
		agentConfig = loadConfiguration();

		logProductInformation();

		// Normalizes the agent configuration, configurations from parent will be set in children configuration
		// to ease data retrieval in the scheduler
		ConfigHelper.normalizeAgentConfiguration(agentConfig);

		telemetryManagers = ConfigHelper.buildTelemetryManagers(agentConfig, connectorStore);

		// Build OpenTelemetry SDK configuration
		otelSdkConfiguration = OtelConfigHelper.buildOtelSdkConfiguration(agentConfig);

		// Build the OpenTelemetry Collector Service
		otelCollectorProcessService = new OtelCollectorProcessService(agentConfig);

		// Build the Host Metric Definitions
		hostMetricDefinitions = ConfigHelper.readHostMetricDefinitions();

		// Build the TaskScheduling Service
		taskSchedulingService =
			TaskSchedulingService
				.builder()
				.withAgentConfig(agentConfig)
				.withAgentInfo(agentInfo)
				.withConfigFile(configFile)
				.withOtelCollectorProcessService(otelCollectorProcessService)
				.withTaskScheduler(TaskSchedulingService.newScheduler(agentConfig.getJobPoolSize()))
				.withTelemetryManagers(telemetryManagers)
				.withSchedules(new HashMap<>())
				.withOtelSdkConfiguration(otelSdkConfiguration)
				.withHostMetricDefinitions(hostMetricDefinitions)
				.withExtensionManager(extensionManager)
				.build();

		final Duration startupDuration = Duration.ofNanos(System.nanoTime() - startTime);

		log.info("Started MetricsHub Agent in {} seconds.", startupDuration.toMillis() / 1000.0);
	}

	/**
	 * Load the {@link PreConfig} instance
	 * @return new {@link PreConfig} instance.
	 * @throws IOException  If an I/O error occurs during the initial reading of the YAML file.
	 */
	private PreConfig loadPreConfig() throws IOException {
		final ObjectMapper objectMapper = ConfigHelper.newObjectMapper();
		return JsonHelper.deserialize(objectMapper, new FileInputStream(configFile), PreConfig.class);
	}

	/**
	 * Loads the agent configuration from a YAML configuration file into an {@link AgentConfig} instance.
	 *
	 * @return {@link AgentConfig} instance.
	 * @throws IOException If an I/O error occurs during the initial reading of the YAML file, during
	 *         the processing phase with {@link EnvironmentProcessor} or at the final deserialization
	 *		   into an {@link AgentConfig}.
	 */
	private AgentConfig loadConfiguration() throws IOException {
		final ObjectMapper objectMapper = newAgentConfigObjectMapper(extensionManager);

		JsonNode configNode = objectMapper.readTree(new FileInputStream(configFile));

		new EnvironmentProcessor().process(configNode);

		return JsonHelper.deserialize(objectMapper, configNode, AgentConfig.class);
	}

	/**
	 * Create a new {@link ObjectMapper} instance then add to it the
	 * {@link PostConfigDeserializer}
	 *
	 * @param extensionManager Manages and aggregates various types of extensions used within MetricsHub.
	 * @return new {@link ObjectMapper} instance
	 */
	public static ObjectMapper newAgentConfigObjectMapper(final ExtensionManager extensionManager) {
		final ObjectMapper objectMapper = ConfigHelper.newObjectMapper();

		PostConfigDeserializeHelper.addPostDeserializeSupport(objectMapper);

		// Inject the extension manager in the deserialization context
		final InjectableValues.Std injectableValues = new InjectableValues.Std();
		injectableValues.addValue(ExtensionManager.class, extensionManager);
		objectMapper.setInjectableValues(injectableValues);

		return objectMapper;
	}

	/**
	 * Log information about MetricsHub application
	 */
	public void logProductInformation() {
		if (isLogInfoEnabled()) {
			// Log product information
			final ApplicationProperties applicationProperties = agentInfo.getApplicationProperties();

			final Project project = applicationProperties.project();

			log.info(
				"Product information:" + // NOSONAR
				"\nName: {}" +
				"\nVersion: {}" +
				"\nBuild number: {}" +
				"\nBuild date: {}" +
				"\nCommunity Connector Library version: {}" +
				"\nJava version: {}" +
				"\nJava Runtime Environment directory: {}" +
				"\nOperating System: {} {}" +
				"\nUser working directory: {}" +
				"\nPID: {}",
				project.name(),
				project.version(),
				applicationProperties.buildNumber(),
				applicationProperties.buildDate(),
				applicationProperties.ccVersion(),
				System.getProperty("java.version"),
				System.getProperty("java.home"),
				System.getProperty("os.name"),
				System.getProperty("os.arch"),
				System.getProperty("user.dir"),
				pid
			);
		}
	}

	/**
	 * Whether the log info is enabled or not
	 *
	 * @return boolean value
	 */
	static boolean isLogInfoEnabled() {
		return log.isInfoEnabled();
	}

	/**
	 * Get the application PID.
	 *
	 * @return PID as {@link String} value
	 */
	private static String findPid() {
		try {
			final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
			return jvmName.split("@")[0];
		} catch (Throwable ex) { // NOSONAR
			return MetricsHubConstants.EMPTY;
		}
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PreConfig {

		@Default
		@JsonSetter(nulls = SKIP)
		private String loggerLevel = "error";

		@Default
		@JsonSetter(nulls = SKIP)
		private String outputDirectory = AgentConstants.DEFAULT_OUTPUT_DIRECTORY.toString();

		@JsonSetter(nulls = SKIP)
		private String patchDirectory;
	}
}
