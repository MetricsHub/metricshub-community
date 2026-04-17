package org.metricshub.extension.emulation;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SqlCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.http.EmulationHttpRequestExecutor;
import org.metricshub.extension.emulation.jdbc.EmulationSqlRequestExecutor;
import org.metricshub.extension.emulation.oscommand.EmulationOsCommandService;
import org.metricshub.extension.emulation.snmp.EmulationSnmpRequestExecutor;
import org.metricshub.extension.emulation.wbem.EmulationWbemRequestExecutor;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.HttpCriterionProcessor;
import org.metricshub.extension.http.HttpExtension;
import org.metricshub.extension.http.HttpSourceProcessor;
import org.metricshub.extension.jdbc.JdbcConfiguration;
import org.metricshub.extension.jdbc.JdbcExtension;
import org.metricshub.extension.jdbc.SqlCriterionProcessor;
import org.metricshub.extension.jdbc.SqlSourceProcessor;
import org.metricshub.extension.oscommand.CommandLineCriterionProcessor;
import org.metricshub.extension.oscommand.CommandLineSourceProcessor;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.snmp.ISnmpConfiguration;
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.extension.snmp.SnmpExtension;
import org.metricshub.extension.snmp.detection.SnmpGetCriterionProcessor;
import org.metricshub.extension.snmp.detection.SnmpGetNextCriterionProcessor;
import org.metricshub.extension.snmp.source.SnmpGetSourceProcessor;
import org.metricshub.extension.snmp.source.SnmpTableSourceProcessor;
import org.metricshub.extension.wbem.WbemConfiguration;
import org.metricshub.extension.wbem.WbemCriterionProcessor;
import org.metricshub.extension.wbem.WbemExtension;
import org.metricshub.extension.wbem.WbemSourceProcessor;

/**
 * Protocol extension that provides file-based emulation for offline testing and
 * development.
 *
 * <p>It replays pre-recorded protocol responses from emulation input files instead
 * of making real network calls.
 */
@Slf4j
public class EmulationExtension implements IProtocolExtension {

	/**
	 * Identifier of the emulation protocol extension.
	 */
	public static final String IDENTIFIER = "emulation";

	/**
	 * Protocols that can be emulated by this extension.
	 */
	protected static final Set<String> EMULATED_PROTOCOLS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	static {
		EMULATED_PROTOCOLS.add(HttpExtension.IDENTIFIER);
		EMULATED_PROTOCOLS.addAll(OsCommandExtension.SUPPORTED_CONFIGURATION_TYPES);
		EMULATED_PROTOCOLS.add(SnmpExtension.IDENTIFIER);
		EMULATED_PROTOCOLS.add(WbemExtension.IDENTIFIER);
		EMULATED_PROTOCOLS.add(JdbcExtension.IDENTIFIER);
	}

	/**
	 * Round-robin index manager shared by all emulation protocol services to rotate
	 * among multiple recorded responses.
	 */
	private final EmulationRoundRobinManager roundRobinManager = new EmulationRoundRobinManager();

	/**
	 * HTTP request executor that replays responses from emulation files.
	 */
	private final EmulationHttpRequestExecutor httpRequestExecutor = new EmulationHttpRequestExecutor(roundRobinManager);

	/**
	 * OS command service that replays command outputs from emulation files.
	 */
	private final EmulationOsCommandService osCommandService = new EmulationOsCommandService(roundRobinManager);

	/**
	 * WBEM request executor that replays query results from emulation files.
	 */
	private final EmulationWbemRequestExecutor wbemRequestExecutor = new EmulationWbemRequestExecutor(roundRobinManager);

	/**
	 * SQL request executor that replays query results from emulation files.
	 */
	private final EmulationSqlRequestExecutor sqlRequestExecutor = new EmulationSqlRequestExecutor(roundRobinManager);

	/**
	 * Retrieves the emulation configuration from the telemetry manager.
	 */
	private static final Function<TelemetryManager, EmulationConfiguration> EMULATION_CONFIGURATION_PROVIDER =
		telemetryManager ->
			(EmulationConfiguration) telemetryManager
				.getHostConfiguration()
				.getConfigurations()
				.get(EmulationConfiguration.class);

	/**
	 * Provides the HTTP configuration used by emulation processors.
	 *
	 * <p>The returned configuration always has its hostname aligned with the telemetry manager hostname.
	 */
	private static final Function<TelemetryManager, HttpConfiguration> EMULATION_HTTP_CONFIGURATION_PROVIDER =
		telemetryManager -> {
			final var emulationConfiguration = EMULATION_CONFIGURATION_PROVIDER.apply(telemetryManager);
			if (emulationConfiguration == null) {
				return HttpConfiguration.builder().hostname(telemetryManager.getHostname()).build();
			}
			final var httpConfiguration = emulationConfiguration.getHttp();
			final String hostname = telemetryManager.getHostname();
			if (httpConfiguration == null) {
				return HttpConfiguration.builder().hostname(hostname).build();
			} else {
				httpConfiguration.setHostname(hostname);
				return httpConfiguration;
			}
		};

	/**
	 * Provides the SNMP configuration used by emulation processors.
	 *
	 * <p>The returned configuration always has its hostname aligned with the telemetry manager hostname.
	 */
	private static final Function<TelemetryManager, ISnmpConfiguration> EMULATION_SNMP_CONFIGURATION_PROVIDER =
		telemetryManager -> {
			final var emulationConfiguration = EMULATION_CONFIGURATION_PROVIDER.apply(telemetryManager);
			if (emulationConfiguration == null) {
				return SnmpConfiguration.builder().hostname(telemetryManager.getHostname()).build();
			}
			final var snmpConfiguration = emulationConfiguration.getSnmp();
			final String hostname = telemetryManager.getHostname();
			if (snmpConfiguration == null) {
				return SnmpConfiguration.builder().hostname(hostname).build();
			} else {
				snmpConfiguration.setHostname(hostname);
				return snmpConfiguration;
			}
		};

	/**
	 * Provides the WBEM configuration used by emulation processors.
	 *
	 * <p>The returned configuration always has its hostname aligned with the telemetry manager hostname.
	 */
	private static final Function<TelemetryManager, WbemConfiguration> EMULATION_WBEM_CONFIGURATION_PROVIDER =
		telemetryManager -> {
			final var emulationConfiguration = EMULATION_CONFIGURATION_PROVIDER.apply(telemetryManager);
			if (emulationConfiguration == null) {
				return null;
			}
			final var wbemConfiguration = emulationConfiguration.getWbem();
			final String hostname = telemetryManager.getHostname();
			if (wbemConfiguration == null) {
				return null;
			} else {
				wbemConfiguration.setHostname(hostname);
				return wbemConfiguration;
			}
		};

	/**
	 * Provides the JDBC configuration used by emulation processors.
	 *
	 * <p>The returned configuration always has its hostname aligned with the telemetry manager hostname.
	 */
	private static final Function<TelemetryManager, JdbcConfiguration> EMULATION_JDBC_CONFIGURATION_PROVIDER =
		telemetryManager -> {
			final var emulationConfiguration = EMULATION_CONFIGURATION_PROVIDER.apply(telemetryManager);
			if (emulationConfiguration == null) {
				return null;
			}
			final var jdbcConfiguration = emulationConfiguration.getJdbc();
			final String hostname = telemetryManager.getHostname();
			if (jdbcConfiguration == null) {
				return null;
			} else {
				jdbcConfiguration.setHostname(hostname);
				return jdbcConfiguration;
			}
		};

	@Override
	public boolean isValidConfiguration(final IConfiguration configuration) {
		return configuration instanceof EmulationConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(
			CommandLineSource.class,
			HttpSource.class,
			SnmpGetSource.class,
			SnmpTableSource.class,
			SqlSource.class,
			WbemSource.class
		);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(EmulationConfiguration.class, getSupportedSources());
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(
			CommandLineCriterion.class,
			HttpCriterion.class,
			SnmpGetCriterion.class,
			SnmpGetNextCriterion.class,
			SqlCriterion.class,
			WbemCriterion.class
		);
	}

	@Override
	public Optional<Boolean> checkProtocol(final TelemetryManager telemetryManager) {
		final EmulationConfiguration configuration = (EmulationConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(EmulationConfiguration.class);

		if (configuration == null) {
			return Optional.empty();
		}

		// Emulation is always considered "up" if configured
		return Optional.of(true);
	}

	@Override
	public SourceTable processSource(
		final Source source,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (source instanceof HttpSource httpSource) {
			return new HttpSourceProcessor(httpRequestExecutor, EMULATION_HTTP_CONFIGURATION_PROVIDER)
				.process(httpSource, connectorId, telemetryManager);
		} else if (source instanceof WbemSource wbemSource) {
			return new WbemSourceProcessor(wbemRequestExecutor, connectorId, EMULATION_WBEM_CONFIGURATION_PROVIDER)
				.process(wbemSource, telemetryManager);
		} else if (source instanceof CommandLineSource commandLineSource) {
			return new CommandLineSourceProcessor(osCommandService).process(commandLineSource, connectorId, telemetryManager);
		} else if (source instanceof SqlSource sqlSource) {
			return new SqlSourceProcessor(sqlRequestExecutor, connectorId, EMULATION_JDBC_CONFIGURATION_PROVIDER)
				.process(sqlSource, telemetryManager);
		}

		final EmulationConfiguration emulationConfiguration = EMULATION_CONFIGURATION_PROVIDER.apply(telemetryManager);
		final EmulationSnmpRequestExecutor snmpExecutor = new EmulationSnmpRequestExecutor(
			emulationConfiguration != null && emulationConfiguration.getSnmp() != null
				? emulationConfiguration.getSnmp().getDirectory()
				: null
		);

		if (source instanceof SnmpTableSource snmpTableSource) {
			return new SnmpTableSourceProcessor(snmpExecutor, EMULATION_SNMP_CONFIGURATION_PROVIDER)
				.process(snmpTableSource, connectorId, telemetryManager);
		} else if (source instanceof SnmpGetSource snmpGetSource) {
			return new SnmpGetSourceProcessor(snmpExecutor, EMULATION_SNMP_CONFIGURATION_PROVIDER)
				.process(snmpGetSource, connectorId, telemetryManager);
		}

		return SourceTable.empty();
	}

	@Override
	public CriterionTestResult processCriterion(
		final Criterion criterion,
		final String connectorId,
		final TelemetryManager telemetryManager,
		final boolean logMode
	) {
		if (criterion instanceof HttpCriterion httpCriterion) {
			return new HttpCriterionProcessor(httpRequestExecutor, logMode, EMULATION_HTTP_CONFIGURATION_PROVIDER)
				.process(httpCriterion, connectorId, telemetryManager);
		} else if (criterion instanceof WbemCriterion wbemCriterion) {
			return new WbemCriterionProcessor(
				wbemRequestExecutor,
				connectorId,
				logMode,
				EMULATION_WBEM_CONFIGURATION_PROVIDER
			)
				.process(wbemCriterion, telemetryManager);
		} else if (criterion instanceof CommandLineCriterion commandLineCriterion) {
			return new CommandLineCriterionProcessor(connectorId, osCommandService)
				.process(commandLineCriterion, telemetryManager);
		} else if (criterion instanceof SqlCriterion sqlCriterion) {
			return new SqlCriterionProcessor(sqlRequestExecutor, logMode, EMULATION_JDBC_CONFIGURATION_PROVIDER)
				.process(sqlCriterion, telemetryManager);
		}

		final EmulationConfiguration emulationConfiguration = EMULATION_CONFIGURATION_PROVIDER.apply(telemetryManager);
		final EmulationSnmpRequestExecutor snmpExecutor = new EmulationSnmpRequestExecutor(
			emulationConfiguration != null && emulationConfiguration.getSnmp() != null
				? emulationConfiguration.getSnmp().getDirectory()
				: null
		);

		if (criterion instanceof SnmpGetCriterion snmpGetCriterion) {
			return new SnmpGetCriterionProcessor(snmpExecutor, EMULATION_SNMP_CONFIGURATION_PROVIDER, logMode)
				.process(snmpGetCriterion, connectorId, telemetryManager);
		} else if (criterion instanceof SnmpGetNextCriterion snmpGetNextCriterion) {
			return new SnmpGetNextCriterionProcessor(snmpExecutor, EMULATION_SNMP_CONFIGURATION_PROVIDER, logMode)
				.process(snmpGetNextCriterion, connectorId, telemetryManager);
		}

		return CriterionTestResult.empty();
	}

	@Override
	public boolean isSupportedConfigurationType(final String configurationType) {
		return IDENTIFIER.equalsIgnoreCase(configurationType);
	}

	@Override
	public IConfiguration buildConfiguration(String configurationType, JsonNode jsonNode, UnaryOperator<char[]> decrypt)
		throws InvalidConfigurationException {
		try {
			return newObjectMapper().treeToValue(normalizeProtocolConfigurationNodes(jsonNode), EmulationConfiguration.class);
		} catch (Exception e) {
			final String errorMessage = String.format(
				"Error while reading Emulation Configuration. Error: %s",
				e.getMessage()
			);
			log.error(errorMessage);
			log.debug("Error while reading Emulation Configuration. Stack trace:", e);
			throw new InvalidConfigurationException(errorMessage, e);
		}
	}

	/**
	 * Flattens nested protocol configuration nodes into the emulation protocol nodes before deserialization.
	 *
	 * <p>The CLI builds each emulated protocol as a node containing a {@code directory} field and a nested
	 * {@code configuration} object. The extension model remains flat, so this method merges the nested
	 * configuration object back into the protocol node while preserving explicit top-level emulation fields.</p>
	 *
	 * @param jsonNode raw emulation configuration node
	 * @return normalized emulation configuration node ready for deserialization
	 */
	private JsonNode normalizeProtocolConfigurationNodes(final JsonNode jsonNode) {
		if (!(jsonNode instanceof ObjectNode objectNode)) {
			return jsonNode;
		}

		final ObjectNode normalizedNode = objectNode.deepCopy();
		EMULATED_PROTOCOLS.forEach(protocol -> flattenProtocolConfiguration(normalizedNode, protocol));
		return normalizedNode;
	}

	/**
	 * Merges a nested {@code configuration} node into its parent emulated protocol node.
	 *
	 * @param rootNode root emulation configuration node
	 * @param protocol protocol identifier to normalize
	 */
	private void flattenProtocolConfiguration(final ObjectNode rootNode, final String protocol) {
		final JsonNode protocolNode = rootNode.get(protocol);
		if (!(protocolNode instanceof ObjectNode protocolObjectNode)) {
			return;
		}

		final JsonNode nestedConfigurationNode = protocolObjectNode.remove("configuration");
		if (!(nestedConfigurationNode instanceof ObjectNode configurationObjectNode)) {
			return;
		}

		for (Map.Entry<String, JsonNode> entry : configurationObjectNode.properties()) {
			if (!protocolObjectNode.has(entry.getKey())) {
				protocolObjectNode.set(entry.getKey(), entry.getValue());
			}
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
		throw new UnsupportedOperationException("Emulation extension does not support direct query execution.");
	}
}
