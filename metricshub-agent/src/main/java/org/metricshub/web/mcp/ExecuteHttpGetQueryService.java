package org.metricshub.web.mcp;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
/**
 * Service that executes HTTP requests (GET by default) through the MetricsHub agent.
 * <p>
 * It resolves a valid HTTP configuration for target URLs from the agent context,
 * extracts the hostname, applies runtime parameters (timeout), builds a minimal HTTP request payload
 * (method, URL, headers, body, resultContent), and delegates execution to the HTTP extension.
 * </p>
 * <p>
 * By default, requests are executed with method {@code GET} and a timeout of {@value #DEFAULT_QUERY_TIMEOUT} seconds
 * if the provided timeout is {@code null} or not positive.
 * </p>
 */
public class ExecuteHttpGetQueryService implements IMCPToolService {

	/**
	 * Default timeout in seconds used when executing the HTTP request.
	 */
	private static final long DEFAULT_QUERY_TIMEOUT = 10L;

	/**
	 * HTTP GET method
	 */
	private static final String HTTP_GET = "GET";

	/**
	 * Default pool size for HTTP queries.
	 */
	private static final int DEFAULT_HTTP_POOL_SIZE = 60;

	/**
	 * Error message returned when an URL entry is missing from the request payload.
	 */
	private static final String NULL_URL_ERROR = "URL must not be null";

	/**
	 * Error message returned when an URL entry is blank.
	 */
	private static final String BLANK_URL_ERROR = "URL must not be blank";

	/**
	 * HTTP protocol prefix.
	 */
	private static final String HTTP_PROTOCOL_PREFIX = "http://";

	/**
	 * HTTPS protocol prefix.
	 */
	private static final String HTTPS_PROTOCOL_PREFIX = "https://";

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ExecuteHttpGetQueryService
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public ExecuteHttpGetQueryService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Run an HTTP GET on the resolved host configuration.
	 *
	 * @param urls     target URLs (e.g. http://example.com/health)
	 * @param headers  headers as newline-separated "key: value" pairs (use "\n")
	 * @param body     optional request body (often ignored for GET)
	 * @param timeout  timeout in seconds; defaults to {@value #DEFAULT_QUERY_TIMEOUT} if null/≤0
	 * @return provider response or error
	 */
	@Tool(
		name = "ExecuteHttpGetQuery",
		description = """
		Execute an HTTP GET request on the provided URL(s) using the agent HTTP extension.
		Resolve a valid configuration from context, derive the hostname, and set timeout (default 10s),
		build the request (method=GET, url, optional headers/body), execute, and return the result or an error.

		Headers must be provided as a single string where each header is "key: value",
		separated by a line break (newline, "\n"). Example:
		Accept: application/json\\nAuthorization: Bearer abc123
		"""
	)
	public MultiHostToolResponse<QueryResponse> executeQuery(
		@ToolParam(description = "The URL(s) to execute HTTP GET query on.", required = true) final List<String> urls,
		@ToolParam(
			description = "Headers as newline-separated \"key: value\" pairs",
			required = false
		) final String headers,
		@ToolParam(description = "Optional request body", required = false) final String body,
		@ToolParam(description = "Optional timeout in seconds (default: 10s).", required = false) final Long timeout,
		@ToolParam(
			description = "Optional pool size for concurrent HTTP queries. Defaults to 60.",
			required = false
		) final Integer poolSize
	) {
		final int resolvedPoolSize = resolvePoolSize(poolSize, DEFAULT_HTTP_POOL_SIZE);

		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType("http")
			.map((IProtocolExtension extension) ->
				executeForHosts(
					urls,
					this::buildNullUrlResponse,
					targetUrl -> executeForUrl(extension, targetUrl, headers, body, timeout),
					resolvedPoolSize
				)
			)
			.orElseGet(() -> MultiHostToolResponse.buildError("The HTTP extension is not available"));
	}

	/**
	 * Builds the {@link HostToolResponse} returned when an URL entry is missing from the input list.
	 *
	 * @return host response containing the {@link #NULL_URL_ERROR} message
	 */
	private HostToolResponse<QueryResponse> buildNullUrlResponse() {
		return buildUrlErrorResponse(NULL_URL_ERROR);
	}

	/**
	 * Wraps the provided error message in a {@link HostToolResponse} containing a {@link QueryResponse}.
	 *
	 * @param errorMessage message describing the URL validation failure
	 * @return host response encapsulating the error message
	 */
	private HostToolResponse<QueryResponse> buildUrlErrorResponse(final String errorMessage) {
		return HostToolResponse
			.<QueryResponse>builder()
			.response(QueryResponse.builder().error(errorMessage).build())
			.build();
	}

	/**
	 * Resolves the hostname and normalized URL for the provided raw value and executes the HTTP query.
	 *
	 * @param extension HTTP protocol extension used to perform the request
	 * @param targetUrl raw URL provided in the tool invocation
	 * @param headers newline-separated header entries; may be {@code null}
	 * @param body optional request body; may be {@code null}
	 * @param timeout timeout in seconds, falling back to {@value #DEFAULT_QUERY_TIMEOUT} when {@code null} or non-positive
	 * @return host response containing either the successful query response or the URL validation error
	 */
	private HostToolResponse<QueryResponse> executeForUrl(
		final IProtocolExtension extension,
		final String targetUrl,
		final String headers,
		final String body,
		final Long timeout
	) {
		try {
			final HttpTarget httpTarget = resolveTarget(targetUrl);
			final QueryResponse response = executeHttpQueryWithExtensionSafe(
				extension,
				httpTarget.hostname(),
				HTTP_GET,
				httpTarget.url(),
				headers,
				body,
				timeout
			);

			return HostToolResponse.<QueryResponse>builder().hostname(httpTarget.hostname()).response(response).build();
		} catch (IllegalArgumentException exception) {
			return buildUrlErrorResponse(exception.getMessage());
		}
	}

	/**
	 * Validates the supplied URL string, normalizes the scheme, and extracts the hostname.
	 *
	 * @param rawUrl user-provided URL value
	 * @return {@link HttpTarget} describing the resolved hostname and normalized URL
	 * @throws IllegalArgumentException when the URL is {@code null}, blank, malformed, or missing a hostname
	 */
	private HttpTarget resolveTarget(final String rawUrl) {
		if (rawUrl == null) {
			throw new IllegalArgumentException(NULL_URL_ERROR);
		}

		if (rawUrl.isBlank()) {
			throw new IllegalArgumentException(BLANK_URL_ERROR);
		}

		final String trimmedUrl = rawUrl.trim();
		final String normalizedUrl = normalizeUrl(trimmedUrl);

		try {
			final URL parsedUrl = new URL(normalizedUrl);
			parsedUrl.toURI();

			final String hostname = parsedUrl.getHost();
			if (hostname == null || hostname.isBlank()) {
				throw new IllegalArgumentException("Hostname could not be resolved from URL: %s".formatted(trimmedUrl));
			}

			return new HttpTarget(hostname, normalizedUrl);
		} catch (MalformedURLException malformedURLException) {
			throw new IllegalArgumentException(
				"Malformed URL '%s': %s".formatted(trimmedUrl, malformedURLException.getMessage())
			);
		} catch (URISyntaxException uriSyntaxException) {
			throw new IllegalArgumentException(
				"URL '%s' contains invalid characters: %s".formatted(trimmedUrl, uriSyntaxException.getMessage())
			);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Invalid URL '%s': %s".formatted(trimmedUrl, exception.getMessage()));
		}
	}

	/**
	 * Ensures the provided URL includes a supported scheme, defaulting to HTTPS when missing.
	 *
	 * @param url URL to normalize
	 * @return original URL when already prefixed with HTTP/HTTPS, otherwise the HTTPS-prefixed variant
	 */
	private String normalizeUrl(final String url) {
		final String lowerCaseUrl = url.toLowerCase(Locale.ROOT);
		if (lowerCaseUrl.startsWith(HTTP_PROTOCOL_PREFIX) || lowerCaseUrl.startsWith(HTTPS_PROTOCOL_PREFIX)) {
			return url;
		}
		return HTTPS_PROTOCOL_PREFIX + url;
	}

	private record HttpTarget(String hostname, String url) {}

	/**
	 * Resolves a valid HTTP configuration for {@code hostname}, prepares the query payload,
	 * and executes it via the provided HTTP extension.
	 *
	 * @param extension HTTP protocol extension used to run the request
	 * @param hostname target host
	 * @param method HTTP method to use (typically {@code GET})
	 * @param url target URL
	 * @param header newline-separated headers string ({@code key: value\\nkey2: value2}); may be {@code null}
	 * @param body optional request body; may be {@code null}
	 * @param timeout timeout in seconds; defaults to {@value #DEFAULT_QUERY_TIMEOUT} when {@code null} or ≤ 0
	 * @return {@link QueryResponse} containing the provider result or an error
	 */
	QueryResponse executeHttpQueryWithExtensionSafe(
		final IProtocolExtension extension,
		final String hostname,
		final String method,
		final String url,
		final String header,
		final String body,
		final Long timeout
	) {
		// Try to retrieve a HTTP configuration for the host
		return MCPConfigHelper
			.resolveAllHostConfigurationCopiesFromContext(hostname, agentContextHolder)
			.stream()
			.filter(extension::isValidConfiguration)
			.findFirst()
			.map((IConfiguration configurationCopy) ->
				executeQuerySafe(
					extension,
					configurationCopy,
					createQueryNode(method, url, header, body),
					hostname,
					method,
					timeout
				)
			)
			.orElseGet(() ->
				QueryResponse.builder().error("No valid configuration found for HTTP on %s.".formatted(hostname)).build()
			);
	}

	/**
	 * Executes the HTTP request with the prepared configuration and handles errors.
	 *
	 * @param extension HTTP protocol extension
	 * @param configurationCopy validated configuration to use
	 * @param queryNode request payload (method, url, optional header/body, resultContent)
	 * @param hostname target host
	 * @param method HTTP method
	 * @param timeout timeout in seconds; defaults when null/≤0
	 * @return response on success, or an error message
	 */
	private static QueryResponse executeQuerySafe(
		final IProtocolExtension extension,
		final IConfiguration configurationCopy,
		final JsonNode queryNode,
		final String hostname,
		final String method,
		final Long timeout
	) {
		// add hostname and timeout to the valid configuration
		configurationCopy.setHostname(hostname);
		configurationCopy.setTimeout(NumberHelper.getPositiveOrDefault(timeout, DEFAULT_QUERY_TIMEOUT).longValue());

		try {
			return QueryResponse.builder().response(extension.executeQuery(configurationCopy, queryNode)).build();
		} catch (Exception e) {
			return QueryResponse
				.builder()
				.error(
					"An error has occurred when executing the HTTP %s request on %s: %s.".formatted(
							method,
							hostname,
							e.getMessage()
						)
				)
				.build();
		}
	}

	/**
	 * Builds the HTTP request JsonNode with the provided parameters.
	 * <p>
	 * The resulting node contains:
	 * <ul>
	 *   <li>{@code method}: the HTTP method (defaults to {@code GET} if {@code method} is null/blank)</li>
	 *   <li>{@code url}: the target URL</li>
	 *   <li>{@code header}: optional raw header string (newline-separated {@code key: value} pairs)</li>
	 *   <li>{@code body}: optional request body</li>
	 *   <li>{@code resultContent}: selection strategy for the extension (defaults to {@code all_with_status})</li>
	 * </ul>
	 * </p>
	 *
	 * @param method        HTTP method (e.g., GET)
	 * @param url           target URL
	 * @param header        newline-separated headers string ({@code key: value\\n...}); may be {@code null}
	 * @param body          optional request body; may be {@code null}
	 * @param resultContent optional result content selector; defaults to {@code all_with_status} when {@code null}
	 * @return a JSON node ready for the extension call
	 */
	static JsonNode createQueryNode(final String method, final String url, final String header, final String body) {
		final var queryNode = JsonNodeFactory.instance.objectNode();

		// Set the specified HTTP method, or GET otherwise.
		queryNode.set("method", new TextNode(StringHelper.getValue(() -> method, HTTP_GET).toUpperCase()));

		queryNode.set("url", new TextNode(url));

		if (header != null) {
			queryNode.set("header", new TextNode(header));
		}

		if (body != null) {
			queryNode.set("body", new TextNode(body));
		}

		queryNode.set("resultContent", new TextNode("all_with_status"));

		return queryNode;
	}
}
