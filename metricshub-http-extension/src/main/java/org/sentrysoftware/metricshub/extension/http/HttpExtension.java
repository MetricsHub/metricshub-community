package org.sentrysoftware.metricshub.extension.http;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub HTTP Extension
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
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.ResultContent;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.Source;
import org.sentrysoftware.metricshub.engine.extension.IProtocolExtension;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.extension.http.utils.HttpRequest;

/**
 * This class implements the {@link IProtocolExtension} contract, reports the supported features,
 * processes HTTP sources and criteria.
 */
@Slf4j
public class HttpExtension implements IProtocolExtension {

	private static final String IDENTIFIER = "http";

	private HttpRequestExecutor httpRequestExecutor;

	/**
	 * Creates a new instance of the {@link HttpExtension} implementation.
	 */
	public HttpExtension() {
		httpRequestExecutor = new HttpRequestExecutor();
	}

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof HttpConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(HttpSource.class);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(HttpConfiguration.class, Set.of(HttpSource.class));
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(HttpCriterion.class);
	}

	@Override
	public boolean checkProtocol(TelemetryManager telemetryManager) {
		// Retrieve the hostname
		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		log.info("Hostname {} - Performing protocol health check.", hostname);

		// Create and set the HTTP result to null
		String httpResult = null;

		// Retrieve HTTP configuration from the telemetry manager
		final HttpConfiguration httpConfiguration = (HttpConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(HttpConfiguration.class);

		// Stop the HTTP health check if there is not an HTTP configuration
		if (httpConfiguration == null) {
			return false;
		}

		log.info("Hostname {} - Checking HTTP protocol status. Sending GET request to '/'.", hostname);

		// Execute HTTP test request
		try {
			// Create an Http request
			final HttpRequest request = HttpRequest
				.builder()
				.hostname(hostname)
				.path("/")
				.httpConfiguration(httpConfiguration)
				.resultContent(ResultContent.ALL)
				.build();

			// Execute Http test request
			httpResult = httpRequestExecutor.executeHttp(request, true, telemetryManager);
		} catch (Exception e) {
			log.debug(
				"Hostname {} - Checking HTTP protocol status. HTTP exception when performing a GET request to '/': ",
				hostname,
				e
			);
		}

		return httpResult != null;
	}

	@Override
	public SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		if (source instanceof HttpSource httpSource) {
			return new HttpSourceProcessor(httpRequestExecutor).process(httpSource, connectorId, telemetryManager);
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
		if (criterion instanceof HttpCriterion httpCriterion) {
			return new HttpCriterionProcessor(httpRequestExecutor).process(httpCriterion, connectorId, telemetryManager);
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
	public boolean isSupportedConfigurationType(String configurationType) {
		return IDENTIFIER.equalsIgnoreCase(configurationType);
	}

	@Override
	public IConfiguration buildConfiguration(String configurationType, JsonNode jsonNode, UnaryOperator<char[]> decrypt)
		throws InvalidConfigurationException {
		try {
			final HttpConfiguration httpConfiguration = newObjectMapper().treeToValue(jsonNode, HttpConfiguration.class);

			if (decrypt != null) {
				final char[] password = httpConfiguration.getPassword();
				if (password != null) {
					// Decrypt the password
					httpConfiguration.setPassword(decrypt.apply(password));
				}
			}

			return httpConfiguration;
		} catch (Exception e) {
			final String errorMessage = String.format(
				"Error while reading HTTP Configuration: %s. Error: %s",
				jsonNode,
				e.getMessage()
			);
			log.error(errorMessage);
			log.debug("Error while reading HTTP Configuration: {}. Stack trace:", jsonNode, e);
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
}
