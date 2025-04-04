package org.metricshub.extension.http.utils;

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

import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.strategy.utils.EmbeddedFileHelper;
import org.metricshub.extension.http.HttpConfiguration;

/**
 * Represents an HTTP request.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HttpRequest {

	@NonNull
	private HttpConfiguration httpConfiguration;

	@NonNull
	private String hostname;

	private String method;

	private String path;

	private String url;

	private Header header;

	private Body body;

	@Builder.Default
	@NonNull
	private ResultContent resultContent = ResultContent.BODY;

	/**
	 * The authentication token for the request.
	 */
	private String authenticationToken;

	/**
	 * Builder for creating instances of {@code HttpRequest}.
	 */
	public static class HttpRequestBuilder {

		/**
		 * Set the {@link Header} object.
		 *
		 * @param value                  String value that can reference an embedded file.
		 * @param connectorEmbeddedFiles All the embedded files referenced in the connector.
		 * @param connectorId            The identifier of the connector.
		 * @param hostname               The hostname of the host being monitored.
		 * @return This builder.
		 */
		public HttpRequestBuilder header(
			final String value,
			final Map<Integer, EmbeddedFile> connectorEmbeddedFiles,
			final String connectorId,
			final String hostname
		) {
			if (value != null) {
				final Optional<EmbeddedFile> maybeEmbeddedFile = EmbeddedFileHelper.findEmbeddedFile(
					value,
					connectorEmbeddedFiles,
					hostname,
					connectorId
				);
				if (maybeEmbeddedFile.isPresent()) {
					this.header = new EmbeddedFileHeader(maybeEmbeddedFile.get());
				} else {
					this.header = new StringHeader(value);
				}
			}
			return this;
		}

		/**
		 * Set the {@link Body} object.
		 *
		 * @param value                  String value that can reference an embedded file.
		 * @param connectorEmbeddedFiles All the embedded files referenced in the connector.
		 * @param connectorId            The identifier of the connector.
		 * @param hostname               The hostname of the host being monitored.
		 * @return This builder.
		 */
		public HttpRequestBuilder body(
			final String value,
			final Map<Integer, EmbeddedFile> connectorEmbeddedFiles,
			final String connectorId,
			final String hostname
		) {
			if (value != null) {
				final Optional<EmbeddedFile> maybeEmbeddedFile = EmbeddedFileHelper.findEmbeddedFile(
					value,
					connectorEmbeddedFiles,
					hostname,
					connectorId
				);
				if (maybeEmbeddedFile.isPresent()) {
					this.body = new EmbeddedFileBody(maybeEmbeddedFile.get());
				} else {
					this.body = new StringBody(value);
				}
			}

			return this;
		}
	}
}
