package org.metricshub.extension.wbem;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Wbem Extension
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
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

@Slf4j
public class WbemExtension implements IProtocolExtension {

	/**
	 * INTEROP WBEM namespace
	 */
	private static final String INTEROP_LOWER_CASE = "interop";

	/**
	 * List of WBEM protocol health check test Namespaces
	 */
	public static final List<String> WBEM_UP_TEST_NAMESPACES = List.of(
		"root/Interop",
		INTEROP_LOWER_CASE,
		"root/PG_Interop",
		"PG_Interop"
	);

	/**
	 * WQL Query to test WBEM protocol health check
	 */
	public static final String WBEM_TEST_QUERY = "SELECT Name FROM CIM_NameSpace";

	/**
	 * The identifier for the Wbem protocol.
	 */
	private static final String IDENTIFIER = "wbem";

	private WbemRequestExecutor wbemRequestExecutor;

	/**
	 * Creates a new instance of the {@link WbemExtension} implementation.
	 */
	public WbemExtension() {
		wbemRequestExecutor = new WbemRequestExecutor();
	}

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof WbemConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(WbemSource.class);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(WbemConfiguration.class, Set.of(WbemSource.class));
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(WbemCriterion.class);
	}

	@Override
	public Optional<Boolean> checkProtocol(TelemetryManager telemetryManager) {
		// Retrieve the hostname from the WbemConfiguration, otherwise from the telemetryManager.
		final String hostname = telemetryManager.getHostname(List.of(WbemConfiguration.class));

		// Create and set the WBEM result to null
		List<List<String>> wbemResult = null;

		// Retrieve WBEM configuration from the telemetry manager
		final WbemConfiguration wbemConfiguration = (WbemConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(WbemConfiguration.class);

		// Stop the WBEM health check if there is not an WBEM configuration
		if (wbemConfiguration == null) {
			return Optional.empty();
		}

		log.info("Hostname {} - Performing {} protocol health check.", hostname, getIdentifier());
		log.info(
			"Hostname {} - Checking WBEM protocol status. Sending a WQL SELECT request on different namespaces.",
			hostname
		);

		for (final String wbemNamespace : WBEM_UP_TEST_NAMESPACES) {
			try {
				log.info(
					"Hostname {} - Checking WBEM protocol status. Sending a WQL SELECT request on {} namespace.",
					hostname,
					wbemNamespace
				);
				// The query on the WBEM namespace returned a result
				wbemResult =
					wbemRequestExecutor.executeWbem(
						hostname,
						wbemConfiguration,
						WBEM_TEST_QUERY,
						wbemNamespace,
						telemetryManager
					);
			} catch (Exception e) {
				if (wbemRequestExecutor.isAcceptableException(e)) {
					return Optional.of(true);
				}
				log.debug(
					"Hostname {} - Checking WBEM protocol status. WBEM exception when performing a WQL SELECT query on '{}' namespace: ",
					hostname,
					wbemNamespace,
					e
				);
			}
		}
		return Optional.of(wbemResult != null);
	}

	@Override
	public CriterionTestResult processCriterion(
		Criterion criterion,
		String connectorId,
		TelemetryManager telemetryManager
	) {
		if (criterion instanceof WbemCriterion wbemCriterion) {
			return new WbemCriterionProcessor(wbemRequestExecutor, connectorId).process(wbemCriterion, telemetryManager);
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
		if (source instanceof WbemSource wbemSource) {
			return new WbemSourceProcessor(wbemRequestExecutor, connectorId).process(wbemSource, telemetryManager);
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
	public IConfiguration buildConfiguration(String configurationType, JsonNode jsonNode, UnaryOperator<char[]> decrypt)
		throws InvalidConfigurationException {
		try {
			final WbemConfiguration wbemConfiguration = newObjectMapper().treeToValue(jsonNode, WbemConfiguration.class);

			if (decrypt != null) {
				// Decrypt the password
				final char[] passwordDecrypted = decrypt.apply(wbemConfiguration.getPassword());
				wbemConfiguration.setPassword(passwordDecrypted);
			}

			return wbemConfiguration;
		} catch (Exception e) {
			final String errorMessage = String.format("Error while reading WBEM Configuration. Error: %s", e.getMessage());
			log.error(errorMessage);
			log.debug("Error while reading WBEM Configuration. Stack trace:", e);
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
	public String executeQuery(
		final IConfiguration configuration,
		final JsonNode queryNode,
		final String emulationInputFilePath
	) throws Exception {
		final WbemConfiguration wbemConfiguration = (WbemConfiguration) configuration;
		final String query = queryNode.get("query").asText();
		// execute Wbem query
		final List<List<String>> result = wbemRequestExecutor.executeWbem(
			configuration.getHostname(),
			wbemConfiguration,
			query,
			wbemConfiguration.getNamespace(),
			new TelemetryManager()
		);

		// return a text table containing the WBEM query result.
		final String[] columns = StringHelper.extractColumns(query);
		if (columns.length == 1 && columns[0].equals("*")) {
			return TextTableHelper.generateTextTable(result);
		} else {
			return TextTableHelper.generateTextTable(columns, result);
		}
	}
}
