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
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.http.EmulationHttpRequestExecutor;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.HttpCriterionProcessor;
import org.metricshub.extension.http.HttpExtension;
import org.metricshub.extension.http.HttpSourceProcessor;

/**
 * The EmulationExtension implements {@link IProtocolExtension} to provide file-based
 * protocol emulation for offline testing and development. It replays pre-recorded
 * protocol responses from emulation input files instead of making real network calls.
 */
@Slf4j
public class EmulationExtension implements IProtocolExtension {

	/**
	 * The identifier for the emulation.
	 */
	public static final String IDENTIFIER = "emulation";

	/**
	 * The emulated protocols supported by this extension.
	 */
	protected static final Set<String> EMULATED_PROTOCOLS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	static {
		EMULATED_PROTOCOLS.add(HttpExtension.IDENTIFIER);
	}

	/**
	 * A function that provides the HTTP configuration for the emulation based on the telemetry manager's host configuration.
	 * This allows the HTTP processors to access the emulation configuration when processing sources.
	 */
	private static final Function<TelemetryManager, HttpConfiguration> EMULATION_HTTP_CONFIGURATION_PROVIDER =
		telemetryManager -> {
			final var emulationConfiguration = (EmulationConfiguration) telemetryManager
				.getHostConfiguration()
				.getConfigurations()
				.get(EmulationConfiguration.class);
			final var httpConfiguration = emulationConfiguration.getHttp();
			if (httpConfiguration == null) {
				return HttpConfiguration.builder().build();
			} else {
				return emulationConfiguration.getHttp();
			}
		};

	@Override
	public boolean isValidConfiguration(final IConfiguration configuration) {
		return configuration instanceof EmulationConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(HttpSource.class);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(EmulationConfiguration.class, getSupportedSources());
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(HttpCriterion.class);
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
			return new HttpSourceProcessor(new EmulationHttpRequestExecutor(), EMULATION_HTTP_CONFIGURATION_PROVIDER)
				.process(httpSource, connectorId, telemetryManager);
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
			return new HttpCriterionProcessor(
				new EmulationHttpRequestExecutor(),
				logMode,
				EMULATION_HTTP_CONFIGURATION_PROVIDER
			)
				.process(httpCriterion, connectorId, telemetryManager);
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
			return newObjectMapper().treeToValue(jsonNode, EmulationConfiguration.class);
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
