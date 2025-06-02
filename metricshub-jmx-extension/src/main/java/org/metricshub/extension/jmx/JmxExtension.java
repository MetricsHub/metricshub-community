package org.metricshub.extension.jmx;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Implements IProtocolExtension for JMX.
 */
@Slf4j
public class JmxExtension implements IProtocolExtension {

	private static final String IDENTIFIER = "jmx";

	public JmxExtension() {
		// No additional state required here
	}

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof JmxConfiguration;
	}

	@Override
	public Set<Class<? extends org.metricshub.engine.connector.model.monitor.task.source.Source>> getSupportedSources() {
		return Set.of(JmxSource.class);
	}

	// @formatter:off
	// @CHECKSTYLE:OFF
	@Override
	public java.util.Map<
		Class<? extends IConfiguration>,
		Set<Class<? extends org.metricshub.engine.connector.model.monitor.task.source.Source>>
	> getConfigurationToSourceMapping() {
		return java.util.Map.of(JmxConfiguration.class, Set.of(JmxSource.class));
	}
	// @CHECKSTYLE:ON
	// @formatter:on
	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		// Support both JmxCriterion and none
		return Set.of(JmxCriterion.class);
	}

	/**
	 * Opens and immediately closes a JMX connection to verify reachability.
	 */
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

		try (JMXConnector jmxc = JMXConnectorFactory.connect(new JMXServiceURL(url))) {
			return Optional.of(true);
		} catch (Exception e) {
			log.debug("JMX health check failed for {}:{} → {}", host, port, e.getMessage());
			return Optional.of(false);
		}
	}

	@Override
	public SourceTable processSource(
		org.metricshub.engine.connector.model.monitor.task.source.Source source,
		String connectorId,
		TelemetryManager telemetryManager
	) {
		if (!(source instanceof JmxSource jmxSource)) {
			throw new IllegalArgumentException(
				String.format(
					"Hostname %s - Cannot process source %s under JMX extension.",
					telemetryManager.getHostname(),
					source != null ? source.getClass().getSimpleName() : "<null>"
				)
			);
		}
		return JmxSourceProcessor.process(jmxSource);
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
	public IConfiguration buildConfiguration(
		String configurationType,
		JsonNode jsonNode,
		java.util.function.UnaryOperator<char[]> decrypt
	) throws InvalidConfigurationException {
		try {
			JmxConfiguration cfg = newObjectMapper().treeToValue(jsonNode, JmxConfiguration.class);
			return cfg;
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
	public String executeQuery(final IConfiguration configuration, final JsonNode query) {
		if (!(configuration instanceof JmxConfiguration jmxConfig)) {
			throw new IllegalArgumentException("executeQuery requires JmxConfiguration");
		}

		String host = jmxConfig.getHost();
		int port = jmxConfig.getPort();

		String objectName = query.get("objectName").asText();
		java.util.List<String> attrs = new java.util.ArrayList<>();
		for (JsonNode node : query.withArray("attributes")) {
			attrs.add(node.asText());
		}

		// Use JmxRequestExecutor to fetch all attributes at once
		Map<String, String> fetched;
		try {
			fetched =
				new JmxRequestExecutor()
					.fetchAttributes(
						host,
						port,
						objectName,
						attrs,
						0L // use default timeout
					);
		} catch (Exception e) {
			return "";
		}

		// Build a semicolon‐separated result
		java.util.List<String> pairs = new java.util.ArrayList<>();
		for (String a : attrs) {
			String v = fetched.get(a);
			pairs.add(String.format("%s=%s", a, (v == null ? "<null>" : v)));
		}
		return String.join("; ", pairs);
	}
}
