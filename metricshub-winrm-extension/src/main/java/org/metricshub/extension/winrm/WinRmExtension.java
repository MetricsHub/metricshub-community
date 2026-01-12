package org.metricshub.extension.winrm;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WinRm Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WinCommandService;
import org.metricshub.extension.win.detection.WinCommandLineCriterionProcessor;
import org.metricshub.extension.win.detection.WinIpmiCriterionProcessor;
import org.metricshub.extension.win.detection.WinServiceCriterionProcessor;
import org.metricshub.extension.win.detection.WmiCriterionProcessor;
import org.metricshub.extension.win.detection.WmiDetectionService;
import org.metricshub.extension.win.source.EventLogSourceProcessor;
import org.metricshub.extension.win.source.WinCommandLineSourceProcessor;
import org.metricshub.extension.win.source.WinIpmiSourceProcessor;
import org.metricshub.extension.win.source.WmiSourceProcessor;

/**
 * This class implements the {@link IProtocolExtension} contract, reports the supported features,
 * processes WMI sources and criteria through WinRm.
 */
@Slf4j
public class WinRmExtension implements IProtocolExtension {

	/**
	 * WinRm Test Query
	 */
	public static final String WINRM_TEST_QUERY = "Select Name FROM Win32_ComputerSystem";

	/**
	 * WinRm namespace
	 */
	public static final String WINRM_TEST_NAMESPACE = "root\\cimv2";

	/**
	 * The identifier for the WinRm protocol.
	 */
	public static final String IDENTIFIER = "winrm";

	private WinRmRequestExecutor winRmRequestExecutor;
	private WmiDetectionService wmiDetectionService;
	private WinCommandService winCommandService;

	/**
	 * Creates a new instance of the {@link WinRmExtension} implementation.
	 */
	public WinRmExtension() {
		winRmRequestExecutor = new WinRmRequestExecutor();
		wmiDetectionService = new WmiDetectionService(winRmRequestExecutor);
		winCommandService = new WinCommandService(winRmRequestExecutor);
	}

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof WinRmConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(WmiSource.class, CommandLineSource.class, IpmiSource.class, EventLogSource.class);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(WinRmConfiguration.class, Set.of(WmiSource.class, EventLogSource.class));
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(WmiCriterion.class, ServiceCriterion.class, CommandLineCriterion.class, IpmiCriterion.class);
	}

	@Override
	public Optional<Boolean> checkProtocol(TelemetryManager telemetryManager) {
		// Retrieve WinRM Configuration from the telemetry manager host configuration
		final WinRmConfiguration winRmConfiguration = (WinRmConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(WinRmConfiguration.class);

		// Stop the health check if there is not an WinRM configuration
		if (winRmConfiguration == null) {
			return Optional.empty();
		}

		// Create and set the WinRM result to null
		List<List<String>> winRmResult = null;

		// Retrieve the hostname from the WinRmConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(WinRmConfiguration.class));

		log.info("Hostname {} - Performing {} protocol health check.", hostname, getIdentifier());
		log.info(
			"Hostname {} - Checking WinRM protocol status. Sending a WQL SELECT request on {} namespace.",
			hostname,
			WINRM_TEST_NAMESPACE
		);

		try {
			winRmResult =
				winRmRequestExecutor.executeWmi(hostname, winRmConfiguration, WINRM_TEST_QUERY, WINRM_TEST_NAMESPACE);
		} catch (Exception e) {
			if (winRmRequestExecutor.isAcceptableException(e)) {
				return Optional.of(true);
			}
			log.debug(
				"Hostname {} - Checking WinRM protocol status. WinRM exception when performing a WQL SELECT request on {} namespace: ",
				hostname,
				WINRM_TEST_NAMESPACE,
				e
			);
		}

		return Optional.of(winRmResult != null);
	}

	@Override
	public CriterionTestResult processCriterion(
		Criterion criterion,
		String connectorId,
		TelemetryManager telemetryManager
	) {
		final Function<TelemetryManager, IWinConfiguration> configurationRetriever = manager ->
			(IWinConfiguration) manager.getHostConfiguration().getConfigurations().get(WinRmConfiguration.class);

		if (criterion instanceof WmiCriterion wmiCriterion) {
			return new WmiCriterionProcessor(wmiDetectionService, configurationRetriever, connectorId)
				.process(wmiCriterion, telemetryManager);
		} else if (criterion instanceof ServiceCriterion serviceCriterion) {
			return new WinServiceCriterionProcessor(wmiDetectionService, configurationRetriever)
				.process(serviceCriterion, telemetryManager);
		} else if (criterion instanceof CommandLineCriterion commandLineCriterion) {
			return new WinCommandLineCriterionProcessor(winCommandService, configurationRetriever, connectorId)
				.process(commandLineCriterion, telemetryManager);
		} else if (criterion instanceof IpmiCriterion ipmiCriterion) {
			return new WinIpmiCriterionProcessor(wmiDetectionService, configurationRetriever)
				.process(ipmiCriterion, telemetryManager);
		}

		throw new IllegalArgumentException(
			String.format(
				"Hostname %s - Cannot process criterion %s.",
				telemetryManager.getHostname(),
				criterion != null ? criterion.getClass().getSimpleName() : "<null>"
			)
		);
	}

	@Override
	public SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		final Function<TelemetryManager, IWinConfiguration> configurationRetriever = manager ->
			(IWinConfiguration) manager.getHostConfiguration().getConfigurations().get(WinRmConfiguration.class);

		if (source instanceof WmiSource wmiSource) {
			return new WmiSourceProcessor(winRmRequestExecutor, configurationRetriever, connectorId)
				.process(wmiSource, telemetryManager);
		} else if (source instanceof IpmiSource ipmiSource) {
			return new WinIpmiSourceProcessor(winRmRequestExecutor, configurationRetriever, connectorId)
				.process(ipmiSource, telemetryManager);
		} else if (source instanceof CommandLineSource commandLineSource) {
			return new WinCommandLineSourceProcessor(winCommandService, configurationRetriever, connectorId)
				.process(commandLineSource, telemetryManager);
		} else if (source instanceof EventLogSource eventLogSource) {
			return new EventLogSourceProcessor(winRmRequestExecutor, configurationRetriever, connectorId)
				.process(eventLogSource, telemetryManager);
		}

		throw new IllegalArgumentException(
			String.format(
				"Hostname %s - Cannot process source %s.",
				telemetryManager.getHostname(),
				source != null ? source.getClass().getSimpleName() : "<null>"
			)
		);
	}

	@Override
	public boolean isSupportedConfigurationType(String configurationType) {
		return IDENTIFIER.equalsIgnoreCase(configurationType);
	}

	@Override
	public IConfiguration buildConfiguration(
		@NonNull String configurationType,
		@NonNull JsonNode jsonNode,
		UnaryOperator<char[]> decrypt
	) throws InvalidConfigurationException {
		try {
			final WinRmConfiguration winRmConfiguration = newObjectMapper().treeToValue(jsonNode, WinRmConfiguration.class);

			if (decrypt != null) {
				final char[] password = winRmConfiguration.getPassword();
				if (password != null) {
					// Decrypt the password
					winRmConfiguration.setPassword(decrypt.apply(password));
				}
			}

			return winRmConfiguration;
		} catch (Exception e) {
			final String errorMessage = String.format("Error while reading WinRm Configuration. Error: %s", e.getMessage());
			log.error(errorMessage);
			log.debug("Error while reading WinRm Configuration. Stack trace:", e);
			throw new InvalidConfigurationException(errorMessage, e);
		}
	}

	/**
	 * Creates and configures a new instance of the Jackson ObjectMapper for handling YAML data.
	 *
	 * @return A configured ObjectMapper instance.
	 */
	public static JsonMapper newObjectMapper() {
		return JsonMapper
			.builder(new YAMLFactory())
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(SerializationFeature.INDENT_OUTPUT)
			.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
			.build();
	}

	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	@Override
	public String executeQuery(final IConfiguration configuration, final JsonNode queryNode) throws Exception {
		final String query = queryNode.get("query").asText();
		final String queryType = queryNode.get("queryType").asText();
		final WinRmConfiguration winRmConfiguration = (WinRmConfiguration) configuration;
		final String namespace = winRmConfiguration.getNamespace();
		final String hostname = configuration.getHostname();

		return queryType.equals("wmi")
			? executeWmiQuery(hostname, winRmConfiguration, query, namespace)
			: winRmRequestExecutor.executeWinRemoteCommand(hostname, winRmConfiguration, query, null);
	}

	/**
	 * Executes a WMI query on the specified host via WinRM and returns the result as a formatted text table.
	 * The method extracts columns from the query and formats the results accordingly. If the query
	 * selects all columns (using '*'), the result table is generated without column headers.
	 *
	 * @param hostname            The hostname or IP address of the target Windows system.
	 * @param winRmConfiguration   The WinRM configuration containing authentication credentials and settings.
	 * @param query               The WQL (WMI Query Language) query to execute.
	 * @param namespace           The WMI namespace where the query should be executed (e.g., "root\\cimv2").
	 * @return A formatted text table containing the query results, or {@code null} if an error occurs during execution.
	 */
	private String executeWmiQuery(
		final String hostname,
		final WinRmConfiguration winRmConfiguration,
		final String query,
		final String namespace
	) {
		List<List<String>> resultList;
		try {
			resultList = winRmRequestExecutor.executeWmi(hostname, winRmConfiguration, query, namespace);
		} catch (ClientException e) {
			log.error("Hostname {}. Error while executing WMI query. Stack trace: {}", hostname, e.getMessage());
			log.debug("Hostname {}. Error while executing WMI query. Stack trace: {}", hostname, e);
			return null;
		}
		final String[] columns = StringHelper.extractColumns(query);
		if (columns.length == 1 && columns[0].equals("*")) {
			return TextTableHelper.generateTextTable(resultList);
		} else {
			return TextTableHelper.generateTextTable(columns, resultList);
		}
	}
}
