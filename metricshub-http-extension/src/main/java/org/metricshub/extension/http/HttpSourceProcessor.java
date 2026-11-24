package org.metricshub.extension.http;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub HTTP Extension
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.extension.HttpResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.utils.HttpRequest;

/**
 * This class is responsible for executing HTTP request based on the provided
 * {@link HttpSource} and generating {@link SourceTable} based on the
 * outcome of the HTTP response. It utilizes an {@link HttpRequestExecutor} to perform
 * the actual HTTP requests.
 */
@Slf4j
public class HttpSourceProcessor {

	private HttpRequestExecutor httpRequestExecutor;
	private Function<TelemetryManager, HttpConfiguration> httpConfigurationProvider;

	/**
	 * Default HTTP configuration provider that retrieves the {@link HttpConfiguration}
	 * from the telemetry manager's configuration map.
	 */
	private static final Function<TelemetryManager, HttpConfiguration> DEFAULT_HTTP_CONFIGURATION_PROVIDER = telemetryManager -> (HttpConfiguration) telemetryManager
		.getHostConfiguration()
		.getConfigurations()
		.get(HttpConfiguration.class);

	/**
	 * Creates a new {@link HttpSourceProcessor} with the given executor and the
	 * default HTTP configuration provider.
	 *
	 * @param httpRequestExecutor The executor to perform HTTP requests.
	 */
	public HttpSourceProcessor(final HttpRequestExecutor httpRequestExecutor) {
		this(httpRequestExecutor, DEFAULT_HTTP_CONFIGURATION_PROVIDER);
	}

	/**
	 * Creates a new {@link HttpSourceProcessor} with the given executor and a
	 * custom HTTP configuration provider.
	 *
	 * @param httpRequestExecutor        The executor to perform HTTP requests.
	 * @param httpConfigurationProvider   A function that retrieves the {@link HttpConfiguration}
	 *                                    from the given {@link TelemetryManager}.
	 */
	public HttpSourceProcessor(
		final HttpRequestExecutor httpRequestExecutor,
		final Function<TelemetryManager, HttpConfiguration> httpConfigurationProvider
	) {
		this.httpRequestExecutor = httpRequestExecutor;
		this.httpConfigurationProvider = httpConfigurationProvider;
	}

	/**
	 * Fetches data using HTTP based on the provided {@link HttpSource} connector's directive,
	 * and returns it as a text.
	 * If the request fails, the server answers with an error status, or HTTP is not configured, the source fails.
	 *
	 * @param httpSource       The {@link HttpSource} defining HTTP request.
	 * @param connectorId      The connector identifier used for logging purposes.
	 * @param telemetryManager The telemetry manager providing access to host configuration and HTTP credentials.
	 * @return a {@link SourceTable} containing the fetched HTTP raw data, or a failure.
	 */
	public SourceTable process(
		final HttpSource httpSource,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		final String request = String.format("HTTP %s %s", httpSource.getMethod(), httpSource.getUrl());
		final SourceTable result = execute(
			httpSource,
			connectorId,
			telemetryManager,
			httpRequest -> httpRequestExecutor.executeHttpSource(httpRequest, true, telemetryManager),
			error -> SourceTable.failed(StringHelper.getStackMessages(error).strip())
		);
		if (result == null) {
			return SourceTable.failed(request + ": HTTP is not configured on the host.");
		}
		return result.isFailed() ? SourceTable.failed(request + ": " + result.getError()) : result;
	}

	/**
	 * Executes the HTTP request of the given {@link HttpSource} and gives the response itself: its status code, its
	 * header and its body, whatever the <code>resultContent</code> of the source.
	 *
	 * @param httpSource       The {@link HttpSource} defining HTTP request.
	 * @param connectorId      The connector identifier used for logging purposes.
	 * @param telemetryManager The telemetry manager providing access to host configuration and HTTP credentials.
	 * @return The response, <code>null</code> when the request got none or when HTTP is not configured.
	 */
	public HttpResult executeRequest(
		final HttpSource httpSource,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		return execute(
			httpSource,
			connectorId,
			telemetryManager,
			request -> httpRequestExecutor.executeHttpRequest(request, true, telemetryManager),
			error -> null
		);
	}

	/**
	 * Builds the HTTP request of the source and executes it.
	 *
	 * @param <T>              What the executor gives.
	 * @param httpSource       The {@link HttpSource} defining HTTP request.
	 * @param connectorId      The connector identifier used for logging purposes.
	 * @param telemetryManager The telemetry manager providing access to host configuration and HTTP credentials.
	 * @param executor         Executes the request.
	 * @param onError          What the request gives when it throws an exception, which is logged.
	 * @return What the executor gives, <code>null</code> when HTTP is not configured.
	 */
	private <T> T execute(
		final HttpSource httpSource,
		final String connectorId,
		final TelemetryManager telemetryManager,
		final Function<HttpRequest, T> executor,
		final Function<Exception, T> onError
	) {
		final HttpConfiguration httpConfiguration = httpConfigurationProvider.apply(telemetryManager);

		if (httpConfiguration == null) {
			log.debug(
				"Hostname {} - The HTTP credentials are not configured. Cannot process HttpSource {}.",
				telemetryManager.getHostname(),
				httpSource
			);

			return null;
		}

		// Retrieve the hostname from the HttpConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(HttpConfiguration.class));

		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles = telemetryManager.getEmbeddedFiles(connectorId);

		try {
			return executor.apply(
				HttpRequest.builder()
					.hostname(hostname)
					.method(httpSource.getMethod().toString())
					.url(httpSource.getUrl())
					.path(httpSource.getPath())
					.header(httpSource.getHeader(), connectorEmbeddedFiles, connectorId, hostname)
					.body(httpSource.getBody(), connectorEmbeddedFiles, connectorId, hostname)
					.resultContent(httpSource.getResultContent())
					.authenticationToken(httpSource.getAuthenticationToken())
					.httpConfiguration(httpConfiguration)
					.build()
			);
		} catch (Exception e) {
			LoggingHelper.logSourceError(
				connectorId,
				httpSource.getKey(),
				String.format("HTTP %s %s", httpSource.getMethod(), httpSource.getUrl()),
				hostname,
				e
			);
			return onError.apply(e);
		}
	}
}
