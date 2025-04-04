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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
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
@AllArgsConstructor
public class HttpSourceProcessor {

	private HttpRequestExecutor httpRequestExecutor;

	/**
	 * Fetches data using HTTP based on the provided {@link HttpSource} connector's directive,
	 * put data into a raw table format, and returns it.
	 * If any errors occur during the fetch or processing, or if the provided configurations are invalid,
	 * an empty {@link SourceTable} is returned.
	 *
	 * @param httpSource       The {@link HttpSource} defining HTTP request.
	 * @param connectorId      The connector identifier used for logging purposes.
	 * @param telemetryManager The telemetry manager providing access to host configuration and HTTP credentials.
	 * @return a {@link SourceTable} containing the fetched HTTP raw data, or an empty table if processing fails.
	 */
	public SourceTable process(
		final HttpSource httpSource,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		final HttpConfiguration httpConfiguration = (HttpConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(HttpConfiguration.class);

		if (httpConfiguration == null) {
			log.debug(
				"Hostname {} - The HTTP credentials are not configured. Returning an empty table for HttpSource {}.",
				telemetryManager.getHostname(),
				httpSource
			);

			return SourceTable.empty();
		}

		// Retrieve the hostname from the HttpConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(HttpConfiguration.class));

		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles = telemetryManager.getEmbeddedFiles(connectorId);

		try {
			final String result = httpRequestExecutor.executeHttp(
				HttpRequest
					.builder()
					.hostname(hostname)
					.method(httpSource.getMethod().toString())
					.url(httpSource.getUrl())
					.path(httpSource.getPath())
					.header(httpSource.getHeader(), connectorEmbeddedFiles, connectorId, hostname)
					.body(httpSource.getBody(), connectorEmbeddedFiles, connectorId, hostname)
					.resultContent(httpSource.getResultContent())
					.authenticationToken(httpSource.getAuthenticationToken())
					.httpConfiguration(httpConfiguration)
					.build(),
				true,
				telemetryManager
			);

			if (result != null && !result.isEmpty()) {
				return SourceTable.builder().rawData(result).build();
			}
		} catch (Exception e) {
			LoggingHelper.logSourceError(
				connectorId,
				httpSource.getKey(),
				String.format("HTTP %s %s", httpSource.getMethod(), httpSource.getUrl()),
				hostname,
				e
			);
		}

		return SourceTable.empty();
	}
}
