package org.metricshub.engine.connector.model.monitor.task.source;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source that retrieves data by making an HTTP request.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HttpSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * The HTTP method for the request.
	 */
	@JsonSetter(nulls = SKIP)
	private HttpMethod method = HttpMethod.GET;

	/**
	 * The URL for the HTTP request.
	 */
	@JsonSetter(nulls = SKIP)
	private String url;

	/**
	 * The path for the HTTP request.
	 */
	@JsonSetter(nulls = SKIP)
	private String path;

	/**
	 * The header for the HTTP request. It can be either a String or an EmbeddedFile reference.
	 */
	private String header;
	/**
	 * The body of the HTTP request.
	 */
	private String body;
	/**
	 * The authentication token for the HTTP request.
	 */
	private String authenticationToken;

	/**
	 * The type of content to retrieve from the HTTP response.
	 */
	@JsonSetter(nulls = SKIP)
	private ResultContent resultContent = ResultContent.BODY;

	/**
	 * Builder for creating instances of {@code HttpSource}.
	 *
	 * @param type The type of the source.
	 * @param computes List of computations to be applied to the source.
	 * @param forceSerialization Flag indicating whether to force serialization.
	 * @param method The HTTP method for the request.
	 * @param url The URL for the HTTP request.
	 * @param path The Path for the HTTP request.
	 * @param header The header for the HTTP request.
	 * @param body The body of the HTTP request.
	 * @param authenticationToken The authentication token for the HTTP request.
	 * @param resultContent The type of content to retrieve from the HTTP response.
	 * @param key The key associated with the source.
	 * @param executeForEachEntryOf The execution context for each entry of the source.
	 */
	@Builder
	public HttpSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty("method") HttpMethod method,
		@JsonProperty("url") String url,
		@JsonProperty("path") String path,
		@JsonProperty("header") String header,
		@JsonProperty("body") String body,
		@JsonProperty("authenticationToken") String authenticationToken,
		@JsonProperty("resultContent") ResultContent resultContent,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.method = method;
		this.url = url;
		this.path = path;
		this.header = header;
		this.body = body;
		this.authenticationToken = authenticationToken;
		this.resultContent = resultContent == null ? ResultContent.BODY : resultContent;
	}

	public HttpSource copy() {
		return HttpSource
			.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.method(method)
			.url(url)
			.path(path)
			.header(header)
			.body(body)
			.authenticationToken(authenticationToken)
			.resultContent(resultContent)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		url = updater.apply(url);
		path = updater.apply(path);
		header = updater.apply(header);
		body = updater.apply(body);
		authenticationToken = updater.apply(authenticationToken);
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- method=", method);
		addNonNull(stringJoiner, "- url=", url);
		addNonNull(stringJoiner, "- path=", path);
		addNonNull(stringJoiner, "- header=", header);
		addNonNull(stringJoiner, "- body=", body);
		addNonNull(stringJoiner, "- authenticationToken=", authenticationToken);
		addNonNull(stringJoiner, "- resultContent=", resultContent != null ? resultContent.getName() : null);

		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
