package org.metricshub.extension.jmx;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub JMX Extension
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
 * GNU Affero General Public License for more details.
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
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.JmxCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

@Slf4j
public class JmxExtension implements IProtocolExtension {

	private static final String IDENTIFIER = "jmx";

	private final JmxRequestExecutor jmxRequestExecutor;

	public JmxExtension() {
		this.jmxRequestExecutor = new JmxRequestExecutor();
	}

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof JmxConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(JmxSource.class);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(JmxConfiguration.class, Set.of(JmxSource.class));
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(JmxCriterion.class);
	}

	@Override
	public Optional<Boolean> checkProtocol(TelemetryManager telemetryManager) {
		HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();
		if (hostConfiguration == null || !hostConfiguration.getConfigurations().containsKey(JmxConfiguration.class)) {
			return Optional.empty();
		}

		JmxConfiguration jmxConfig = (JmxConfiguration) hostConfiguration.getConfigurations().get(JmxConfiguration.class);
		String host = jmxConfig.getHost();
		int port = jmxConfig.getPort();
		String url = String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", host, port);

		log.info("Hostname {} - Performing {} protocol health check.", telemetryManager.getHostname(), getIdentifier());
		log.info("Hostname {} - Attempting JMX connection to {}.", telemetryManager.getHostname(), url);

		try (JMXConnector jmxc = JMXConnectorFactory.connect(new JMXServiceURL(url))) {
			return Optional.of(true);
		} catch (Exception e) {
			log.debug("JMX health check failed for {}:{} → {}", host, port, e.getMessage());
			return Optional.of(false);
		}
	}

	@Override
	public SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		if (source instanceof JmxSource jmxSource) {
			return JmxSourceProcessor.process(jmxSource);
		}
		throw new IllegalArgumentException(
			String.format(
				"Hostname %s - Cannot process source %s under JMX extension.",
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
		if (criterion instanceof JmxCriterion) {
			return new JmxCriterionProcessor().process(criterion, connectorId, telemetryManager);
		}
		throw new IllegalArgumentException(
			String.format(
				"Hostname %s - Cannot process criterion %s under JMX extension.",
				telemetryManager.getHostname(),
				criterion != null ? criterion.getClass().getSimpleName() : "<null>"
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
			JmxConfiguration jmxConfiguration = newObjectMapper().treeToValue(jsonNode, JmxConfiguration.class);

			// If any credentials are added in future, decrypt them here. Currently JmxConfiguration has no encrypted fields.
			return jmxConfiguration;
		} catch (Exception e) {
			String msg = String.format("Error reading JMX Configuration: %s", e.getMessage());
			log.error(msg, e);
			throw new InvalidConfigurationException(msg, e);
		}
	}

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

	/**
	 * Executes a single‐MBean lookup + attribute fetch. Expects JSON like:
	 * {
	 *   "objectName": "...",
	 *   "attributes": ["attr1", "attr2"]
	 * }
	 */
	@Override
	public String executeQuery(IConfiguration configuration, JsonNode query) {
		if (!(configuration instanceof JmxConfiguration jmxConfig)) {
			throw new IllegalArgumentException("executeQuery requires JmxConfiguration");
		}

		// Build a HostConfiguration and TelemetryManager similar to HTTP extension
		HostConfiguration hostConfig = HostConfiguration
			.builder()
			.configurations(Map.of(JmxConfiguration.class, configuration))
			.build();
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfig).build();

		String host = jmxConfig.getHost();
		int port = jmxConfig.getPort();
		String objectName = query.get("objectName").asText();

		List<String> attrs = new java.util.ArrayList<>();
		for (JsonNode node : query.withArray("attributes")) {
			attrs.add(node.asText());
		}

		Map<String, String> fetched;
		try {
			fetched = jmxRequestExecutor.fetchAttributes(host, port, objectName, attrs, 0L);
		} catch (Exception e) {
			log.debug(
				"Hostname {} - Error fetching JMX attributes for {} at {}:{} → {}",
				telemetryManager.getHostname(),
				objectName,
				host,
				port,
				e.getMessage()
			);
			return "";
		}

		List<String> pairs = new java.util.ArrayList<>();
		for (String a : attrs) {
			String v = fetched.get(a);
			pairs.add(String.format("%s=%s", a, (v == null ? "<null>" : v)));
		}
		return String.join("; ", pairs);
	}
}
