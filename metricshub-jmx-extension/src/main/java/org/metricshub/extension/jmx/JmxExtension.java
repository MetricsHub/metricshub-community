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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.JmxCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * This class implements the {@link IProtocolExtension} contract for JMX protocol
 */
@Slf4j
public class JmxExtension implements IProtocolExtension {

	public static final String IDENTIFIER = "jmx";

	private JmxRequestExecutor jmxRequestExecutor;

	/**
	 * Default constructor initializing the JmxRequestExecutor.
	 */
	public JmxExtension() {
		this.jmxRequestExecutor = new JmxRequestExecutor();
	}

	@Override
	public boolean isValidConfiguration(final IConfiguration configuration) {
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
	public Optional<Boolean> checkProtocol(final TelemetryManager telemetryManager) {
		final JmxConfiguration configuration = (JmxConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(JmxConfiguration.class);

		// Stop the health check if there is not an JMX configuration
		if (configuration == null) {
			return Optional.empty();
		}

		log.info("Hostname {} - Performing {} protocol health check.", configuration.getHostname(), getIdentifier());

		try {
			return Optional.of(jmxRequestExecutor.checkConnection(configuration));
		} catch (Exception e) {
			log.error("Hostname {} - JMX protocol check failed: {}", configuration.getHostname(), e.getMessage());
			log.debug("Hostname {} - JMX protocol check failed.", configuration.getHostname(), e);
			return Optional.of(false);
		}
	}

	@Override
	public SourceTable processSource(
		final Source source,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (source instanceof JmxSource jmxSource) {
			return new JmxSourceProcessor(jmxRequestExecutor).process(jmxSource, telemetryManager);
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
		final Criterion criterion,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (criterion instanceof JmxCriterion jmxCriterion) {
			return new JmxCriterionProcessor(jmxRequestExecutor).process(jmxCriterion, connectorId, telemetryManager);
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
	public boolean isSupportedConfigurationType(final String configurationType) {
		return IDENTIFIER.equalsIgnoreCase(configurationType);
	}

	@Override
	public IConfiguration buildConfiguration(
		final String configurationType,
		final JsonNode jsonNode,
		final UnaryOperator<char[]> decrypt
	) throws InvalidConfigurationException {
		try {
			final JmxConfiguration jmxConfiguration = newObjectMapper().treeToValue(jsonNode, JmxConfiguration.class);

			if (decrypt != null) {
				char[] password = jmxConfiguration.getPassword();
				if (password != null) {
					jmxConfiguration.setPassword(decrypt.apply(password));
				}
			}
			return jmxConfiguration;
		} catch (Exception e) {
			final var message = String.format("Error reading JMX Configuration: %s", e.getMessage());
			log.error(message, e);
			throw new InvalidConfigurationException(message, e);
		}
	}

	/**
	 * Creates a new instance of JsonMapper configured for YAML processing. This
	 * mapper is used to deserialize YAML configurations into Java objects.
	 *
	 * @return a configured JsonMapper instance
	 */
	public JsonMapper newObjectMapper() {
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
		if (!(configuration instanceof JmxConfiguration)) {
			throw new IllegalArgumentException("Invalid configuration type for JMX query execution.");
		}
		// Expected JSON structure:
		// {
		//   "objectName": "com.example:type=Example,scope=*,name=*",
		//   "attributes": ["Attribute1", "Attribute2"],
		//   "keyProperties": ["scope", "name"]
		// }

		final var objectNameNode = query.get("objectName");
		if (objectNameNode == null || objectNameNode.isNull()) {
			throw new IllegalArgumentException("Object name must be specified for JMX query.");
		}
		final String objectName = objectNameNode.asText();
		if (objectName.isBlank()) {
			throw new IllegalArgumentException("Object name cannot be blank for JMX query.");
		}

		final JsonNode attributesNodes = query.get("attributes");
		final List<String> attributes = new ArrayList<>();
		if (attributesNodes != null && !attributesNodes.isNull()) {
			attributesNodes.forEach(node -> attributes.add(node.asText()));
		}
		final JsonNode keyPropertiesNode = query.get("keyProperties");
		final List<String> keyProperties = new ArrayList<>();
		if (keyPropertiesNode != null && !keyPropertiesNode.isNull()) {
			keyPropertiesNode.forEach(node -> keyProperties.add(node.asText()));
		}

		if (attributes.isEmpty() && keyProperties.isEmpty()) {
			throw new IllegalArgumentException("At least one attribute or key property must be specified for JMX query.");
		}

		final List<List<String>> result = jmxRequestExecutor.fetchMBean(
			(JmxConfiguration) configuration,
			objectName,
			attributes,
			keyProperties
		);
		final List<String> columns = new ArrayList<>();
		columns.addAll(keyProperties);
		columns.addAll(attributes);

		return TextTableHelper.generateTextTable(columns, result);
	}
}
