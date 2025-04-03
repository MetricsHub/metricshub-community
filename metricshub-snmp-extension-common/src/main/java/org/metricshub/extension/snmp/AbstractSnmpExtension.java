package org.metricshub.extension.snmp;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub SNMP Extension Common
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.extension.snmp.detection.SnmpGetCriterionProcessor;
import org.metricshub.extension.snmp.detection.SnmpGetNextCriterionProcessor;
import org.metricshub.extension.snmp.source.SnmpGetSourceProcessor;
import org.metricshub.extension.snmp.source.SnmpTableSourceProcessor;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.Source;
import org.sentrysoftware.metricshub.engine.extension.IProtocolExtension;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * An abstract base class for SNMP protocol extensions.
 * Provides common methods and functionality for handling SNMP sources, criteria, and configurations.
 */
@Slf4j
public abstract class AbstractSnmpExtension implements IProtocolExtension {

	/**
	 * The SNMP OID value to use in the health check test
	 */
	public static final String SNMP_OID = "1.3.6.1";

	public static final String GET = "get";
	public static final String GET_NEXT = "getNext";
	public static final String WALK = "walk";
	public static final String TABLE = "table";

	/**
	 * Returns the SNMP request executor used for executing SNMP requests.
	 * @return The SNMP request executor.
	 */
	protected abstract AbstractSnmpRequestExecutor getRequestExecutor();

	/**
	 * Returns the class type of the SNMP configuration.
	 * @return The SNMP configuration class type.
	 */
	protected abstract Class<? extends ISnmpConfiguration> getConfigurationClass();

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(SnmpTableSource.class, SnmpGetSource.class);
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(SnmpGetCriterion.class, SnmpGetNextCriterion.class);
	}

	@Override
	public Optional<Boolean> checkProtocol(TelemetryManager telemetryManager) {
		// Retrieve the SNMP configuration class type
		final Class<? extends ISnmpConfiguration> configurationClass = getConfigurationClass();

		// Retrieve the hostname from the Snmp Configuration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(configurationClass));

		// Create and set the SNMP result to null
		String result = null;

		// Retrieve SNMP Configuration from the telemetry manager host configuration
		final ISnmpConfiguration configuration = (ISnmpConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(configurationClass);

		// Stop the SNMP health check if there is not an SNMP configuration
		if (configuration == null) {
			return Optional.empty();
		}

		log.info("Hostname {} - Performing {} protocol health check.", hostname, getIdentifier());
		log.info("Hostname {} - Checking SNMP protocol status. Sending Get Next request on {}.", hostname, SNMP_OID);

		// Execute SNMP test command
		try {
			result = getRequestExecutor().executeSNMPGetNext(SNMP_OID, configuration, hostname, true);
		} catch (Exception e) {
			log.debug(
				"Hostname {} - Checking SNMP protocol status. SNMP exception when performing a SNMP Get Next query on {}: ",
				hostname,
				SNMP_OID,
				e
			);
		}
		return Optional.of(result != null);
	}

	@Override
	public SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		final Function<TelemetryManager, ISnmpConfiguration> configurationRetriever = manager ->
			(ISnmpConfiguration) manager.getHostConfiguration().getConfigurations().get(getConfigurationClass());

		if (source instanceof SnmpTableSource snmpTableSource) {
			return new SnmpTableSourceProcessor(getRequestExecutor(), configurationRetriever)
				.process(snmpTableSource, connectorId, telemetryManager);
		} else if (source instanceof SnmpGetSource snmpGetSource) {
			return new SnmpGetSourceProcessor(getRequestExecutor(), configurationRetriever)
				.process(snmpGetSource, connectorId, telemetryManager);
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
	public CriterionTestResult processCriterion(
		Criterion criterion,
		String connectorId,
		TelemetryManager telemetryManager
	) {
		final Function<TelemetryManager, ISnmpConfiguration> configurationRetriever = manager ->
			(ISnmpConfiguration) manager.getHostConfiguration().getConfigurations().get(getConfigurationClass());

		if (criterion instanceof SnmpGetCriterion snmpGetCriterion) {
			return new SnmpGetCriterionProcessor(getRequestExecutor(), configurationRetriever)
				.process(snmpGetCriterion, connectorId, telemetryManager);
		} else if (criterion instanceof SnmpGetNextCriterion snmpGetNextCriterion) {
			return new SnmpGetNextCriterionProcessor(getRequestExecutor(), configurationRetriever)
				.process(snmpGetNextCriterion, connectorId, telemetryManager);
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
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(getConfigurationClass(), Set.of(SnmpTableSource.class, SnmpGetSource.class));
	}

	@Override
	public boolean isSupportedConfigurationType(String configurationType) {
		return getIdentifier().equalsIgnoreCase(configurationType);
	}

	@Override
	public String executeQuery(final IConfiguration configuration, final JsonNode queryNode) {
		final ISnmpConfiguration snmpConfiguration = (ISnmpConfiguration) configuration;
		final String hostname = configuration.getHostname();
		String result = "Failed Executing SNMP query";
		final String action = queryNode.get("action").asText();
		final String oId = queryNode.get("oid").asText();

		final AbstractSnmpRequestExecutor executor = getRequestExecutor();
		try {
			switch (action) {
				case GET:
					result = executor.executeSNMPGet(oId, snmpConfiguration, hostname, false);
					break;
				case GET_NEXT:
					result = executor.executeSNMPGetNext(oId, snmpConfiguration, hostname, false);
					break;
				case WALK:
					result = executor.executeSNMPWalk(oId, snmpConfiguration, hostname, false);
					break;
				case TABLE:
					final String[] columns = new ObjectMapper().convertValue(queryNode.get("columns"), String[].class);
					final List<List<String>> resultList = executor.executeSNMPTable(
						oId,
						columns,
						snmpConfiguration,
						hostname,
						false
					);
					result = TextTableHelper.generateTextTable(columns, resultList);
					break;
				default:
					throw new IllegalArgumentException(String.format("Hostname %s - Invalid SNMP Operation", hostname));
			}
		} catch (Exception e) {
			log.debug("Hostname {} - Error while executing SNMP {} query. Message: {}", hostname, action, e);
		}
		return result;
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
}
