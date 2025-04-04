package org.metricshub.engine.connector.model.identity.criterion;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.StringJoiner;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.ResultContent;

/**
 * Connector detection criterion using HTTP protocol.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HttpCriterion extends Criterion {

	private static final long serialVersionUID = 1L;

	/**
	 * HTTP method for the criterion.
	 */
	@JsonSetter(nulls = SKIP)
	private HttpMethod method = HttpMethod.GET;

	/**
	 * URL for the HTTP criterion.
	 */
	@JsonSetter(nulls = SKIP)
	private String url;

	/**
	 * Path for the HTTP criterion.
	 */
	@JsonSetter(nulls = SKIP)
	private String path;

	/**
	 * String or EmbeddedFile reference for the HTTP criterion.
	 */
	private String header;

	/**
	 * Body for the HTTP criterion.
	 */
	private String body;

	/**
	 * Expected result for the HTTP criterion.
	 */
	private String expectedResult;

	/**
	 * Error message for the HTTP criterion.
	 */
	private String errorMessage;

	/**
	 * Result content for the HTTP criterion.
	 */
	@JsonSetter(nulls = SKIP)
	private ResultContent resultContent = ResultContent.BODY;

	/**
	 * Authentication token for the HTTP criterion.
	 */
	private String authenticationToken;

	/**
	 * Constructor with builder for creating an instance of HttpCriterion.
	 *
	 * @param type                Type of the criterion.
	 * @param forceSerialization Flag indicating whether serialization should be forced.
	 * @param method              HTTP method for the test.
	 * @param url                 URL for the HTTP test.
	 * @param header              Header for the HTTP test.
	 * @param body                Body for the HTTP test.
	 * @param expectedResult      Expected result for the HTTP test.
	 * @param errorMessage        Error message for the HTTP test.
	 * @param resultContent       Result content for the HTTP test.
	 * @param authenticationToken Authentication token for the HTTP test.
	 */
	@Builder
	@JsonCreator
	public HttpCriterion(
		@JsonProperty("type") String type,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty("method") HttpMethod method,
		@JsonProperty(value = "url") String url,
		@JsonProperty("path") String path,
		@JsonProperty("header") String header,
		@JsonProperty("body") String body,
		@JsonProperty("expectedResult") String expectedResult,
		@JsonProperty("errorMessage") String errorMessage,
		@JsonProperty("resultContent") ResultContent resultContent,
		@JsonProperty("authenticationToken") String authenticationToken
	) {
		super(type, forceSerialization);
		this.method = method == null ? HttpMethod.GET : method;
		this.url = url;
		this.path = path;
		this.header = header;
		this.body = body;
		this.expectedResult = expectedResult;
		this.errorMessage = errorMessage;
		this.resultContent = resultContent == null ? ResultContent.BODY : resultContent;
		this.authenticationToken = authenticationToken;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		if (method != null) {
			stringJoiner.add(new StringBuilder("- Method: ").append(method));
		}
		if (url != null) {
			stringJoiner.add(new StringBuilder("- URL: ").append(url));
		}
		if (path != null) {
			stringJoiner.add(new StringBuilder("- Path: ").append(path));
		}
		if (header != null) {
			stringJoiner.add(new StringBuilder("- Header: ").append(header));
		}
		if (body != null) {
			stringJoiner.add(new StringBuilder("- Body: ").append(body));
		}
		if (expectedResult != null) {
			stringJoiner.add(new StringBuilder("- ExpectedResult: ").append(expectedResult));
		}
		if (errorMessage != null) {
			stringJoiner.add(new StringBuilder("- ErrorMessage: ").append(errorMessage));
		}
		if (resultContent != null) {
			stringJoiner.add(new StringBuilder("- ResultContent: ").append(resultContent));
		}
		if (authenticationToken != null) {
			stringJoiner.add(new StringBuilder("- AuthenticationToken: ").append(authenticationToken));
		}
		return stringJoiner.toString();
	}
}
