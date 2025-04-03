package org.metricshub.extension.wmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub WMI Extension
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
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.common.helpers.StringHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.Source;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.sentrysoftware.metricshub.engine.extension.IProtocolExtension;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.extension.win.IWinConfiguration;
import org.sentrysoftware.metricshub.extension.win.WinCommandService;
import org.sentrysoftware.metricshub.extension.win.detection.WinCommandLineCriterionProcessor;
import org.sentrysoftware.metricshub.extension.win.detection.WinIpmiCriterionProcessor;
import org.sentrysoftware.metricshub.extension.win.detection.WinProcessCriterionProcessor;
import org.sentrysoftware.metricshub.extension.win.detection.WinServiceCriterionProcessor;
import org.sentrysoftware.metricshub.extension.win.detection.WmiCriterionProcessor;
import org.sentrysoftware.metricshub.extension.win.detection.WmiDetectionService;
import org.sentrysoftware.metricshub.extension.win.source.WinCommandLineSourceProcessor;
import org.sentrysoftware.metricshub.extension.win.source.WinIpmiSourceProcessor;
import org.sentrysoftware.metricshub.extension.win.source.WmiSourceProcessor;

/**
 * This class implements the {@link IProtocolExtension} contract, reports the supported features,
 * processes WMI sources and criteria.
 */
@Slf4j
public class WmiExtension implements IProtocolExtension {

	/**
	 * WMI namespace
	 */
	public static final String WMI_TEST_NAMESPACE = "root\\cimv2";

	/**
	 * WMI Query used by the protocol health check
	 */
	public static final String WMI_TEST_QUERY = "SELECT Name FROM Win32_ComputerSystem";

	/**
	 * The identifier for the Wmi protocol.
	 */
	private static final String IDENTIFIER = "wmi";

	private WmiRequestExecutor wmiRequestExecutor;
	private WmiDetectionService wmiDetectionService;
	private WinCommandService winCommandService;

	/**
	 * Creates a new instance of the {@link WmiExtension} implementation.
	 */
	public WmiExtension() {
		wmiRequestExecutor = new WmiRequestExecutor();
		wmiDetectionService = new WmiDetectionService(wmiRequestExecutor);
		winCommandService = new WinCommandService(wmiRequestExecutor);
	}

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof WmiConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(WmiSource.class, CommandLineSource.class, IpmiSource.class);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(WmiConfiguration.class, Set.of(WmiSource.class));
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(WmiCriterion.class, ServiceCriterion.class, CommandLineCriterion.class, IpmiCriterion.class);
	}

	@Override
	public Optional<Boolean> checkProtocol(TelemetryManager telemetryManager) {
		// Create and set the WMI result to null
		List<List<String>> wmiResult = null;

		// Retrieve WMI Configuration from the telemetry manager host configuration
		final WmiConfiguration wmiConfiguration = (WmiConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(WmiConfiguration.class);

		// Stop the health check if there is not an WMI configuration
		if (wmiConfiguration == null) {
			return Optional.empty();
		}

		// Retrieve the hostname from the WmiConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(WmiConfiguration.class));

		log.info("Hostname {} - Performing {} protocol health check.", hostname, getIdentifier());
		log.info(
			"Hostname {} - Checking WMI protocol status. Sending a WQL SELECT request on {} namespace.",
			hostname,
			WMI_TEST_NAMESPACE
		);

		try {
			wmiResult = wmiRequestExecutor.executeWmi(hostname, wmiConfiguration, WMI_TEST_QUERY, WMI_TEST_NAMESPACE);
		} catch (Exception e) {
			if (wmiRequestExecutor.isAcceptableException(e)) {
				return Optional.of(true);
			}
			log.debug(
				"Hostname {} - Checking WMI protocol status. WMI exception when performing a WQL SELECT request on {} namespace: ",
				hostname,
				WMI_TEST_NAMESPACE,
				e
			);
		}
		return Optional.of(wmiResult != null);
	}

	@Override
	public CriterionTestResult processCriterion(
		Criterion criterion,
		String connectorId,
		TelemetryManager telemetryManager
	) {
		final Function<TelemetryManager, IWinConfiguration> configurationRetriever = manager ->
			(IWinConfiguration) manager.getHostConfiguration().getConfigurations().get(WmiConfiguration.class);

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
		} else if (criterion instanceof ProcessCriterion processCriterion) {
			return new WinProcessCriterionProcessor(wmiDetectionService)
				.process(processCriterion, WmiConfiguration.builder().username(null).password(null).timeout(30L).build());
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
			(IWinConfiguration) manager.getHostConfiguration().getConfigurations().get(WmiConfiguration.class);

		if (source instanceof WmiSource wmiSource) {
			return new WmiSourceProcessor(wmiRequestExecutor, configurationRetriever, connectorId)
				.process(wmiSource, telemetryManager);
		} else if (source instanceof IpmiSource ipmiSource) {
			return new WinIpmiSourceProcessor(wmiRequestExecutor, configurationRetriever, connectorId)
				.process(ipmiSource, telemetryManager);
		} else if (source instanceof CommandLineSource commandLineSource) {
			return new WinCommandLineSourceProcessor(winCommandService, configurationRetriever, connectorId)
				.process(commandLineSource, telemetryManager);
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
			final WmiConfiguration wmiConfiguration = newObjectMapper().treeToValue(jsonNode, WmiConfiguration.class);

			if (decrypt != null) {
				final char[] password = wmiConfiguration.getPassword();
				if (password != null) {
					// Decrypt the password
					wmiConfiguration.setPassword(decrypt.apply(password));
				}
			}

			return wmiConfiguration;
		} catch (Exception e) {
			final String errorMessage = String.format("Error while reading WMI Configuration. Error: %s", e.getMessage());
			log.error(errorMessage);
			log.debug("Error while reading WMI Configuration. Stack trace:", e);
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
		final WmiConfiguration wmiConfiguration = (WmiConfiguration) configuration;
		final String namespace = wmiConfiguration.getNamespace();
		final String hostname = configuration.getHostname();
		final List<List<String>> resultList = wmiRequestExecutor.executeWmi(hostname, wmiConfiguration, query, namespace);
		final String[] columns = StringHelper.extractColumns(query);
		if (columns.length == 1 && columns[0].equals("*")) {
			return TextTableHelper.generateTextTable(resultList);
		} else {
			return TextTableHelper.generateTextTable(columns, resultList);
		}
	}
}
