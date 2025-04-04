package org.metricshub.extension.ping;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Ping Extension
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
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * This class implements the {@link IProtocolExtension} contract, reports the supported features,
 * checks the reachability of a host through a ICMP Ping.
 */
@Slf4j
public class PingExtension implements IProtocolExtension {

	/**
	 * The identifier for ping.
	 */
	private static final String IDENTIFIER = "ping";

	private PingRequestExecutor pingRequestExecutor;

	public PingExtension() {
		pingRequestExecutor = new PingRequestExecutor();
	}

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof PingConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of();
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of();
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of();
	}

	@Override
	public Optional<Boolean> checkProtocol(TelemetryManager telemetryManager) {
		// Retrieve Ping configuration from the telemetry manager
		final PingConfiguration pingConfiguration = (PingConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(PingConfiguration.class);

		// Stop the Ping check if there is not a Ping configuration
		if (pingConfiguration == null) {
			return Optional.empty();
		}

		// Retrieve the hostname from the PingConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(PingConfiguration.class));

		// Create and set the Ping result to null
		boolean pingResult = false;

		log.info("Hostname {} - Performing {} protocol health check.", hostname, getIdentifier());
		log.info("Hostname {} - Checking Ping protocol status. Sending a ping to '/'.", hostname);

		// Execute a Ping request
		try {
			pingResult = pingRequestExecutor.ping(hostname, (int) pingConfiguration.getTimeout().longValue() * 1000);
		} catch (Exception e) {
			log.debug("Hostname {} - Checking Ping protocol status. Exception when performing a Ping request: ", hostname, e);
		}
		return Optional.of(pingResult);
	}

	@Override
	public SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		return SourceTable.empty();
	}

	@Override
	public CriterionTestResult processCriterion(
		Criterion criterion,
		String connectorId,
		TelemetryManager telemetryManager
	) {
		return CriterionTestResult.empty();
	}

	@Override
	public boolean isSupportedConfigurationType(String configurationType) {
		return IDENTIFIER.equalsIgnoreCase(configurationType);
	}

	@Override
	public IConfiguration buildConfiguration(String configurationType, JsonNode jsonNode, UnaryOperator<char[]> decrypt)
		throws InvalidConfigurationException {
		try {
			return newObjectMapper().treeToValue(jsonNode, PingConfiguration.class);
		} catch (Exception e) {
			final String errorMessage = String.format("Error while reading Ping Configuration. Error: %s", e.getMessage());
			log.error(errorMessage);
			log.debug("Error while reading Ping Configuration. Stack trace:", e);
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
	public String executeQuery(final IConfiguration configuration, final JsonNode query) throws Exception {
		final PingConfiguration pingConfiguration = (PingConfiguration) configuration;
		return String.valueOf(
			pingRequestExecutor.ping(pingConfiguration.getHostname(), (int) pingConfiguration.getTimeout().longValue() * 1000)
		);
	}
}
