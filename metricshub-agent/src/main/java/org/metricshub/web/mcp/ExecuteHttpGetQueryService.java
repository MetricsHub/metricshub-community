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
 * It resolves a valid HTTP configuration for a target host from the agent context,
 * applies runtime parameters (hostname, timeout), builds a minimal HTTP request payload
 * (method, URL, headers, body, resultContent), and delegates execution to the HTTP extension.
 * </p>
 * <p>
 * By default, requests are executed with method {@code GET} and a timeout of {@value #DEFAULT_QUERY_TIMEOUT} seconds
 * if the provided timeout is {@code null} or not positive.
 * </p>
 */
public class ExecuteHttpGetQueryService {

	/**
	 * Default timeout in seconds used when executing the HTTP request.
	 */
	private static final long DEFAULT_QUERY_TIMEOUT = 10L;

	/**
	 * HTTP GET method
	 */
	private static final String HTTP_GET = "GET";

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
	 * @param hostname target host used to resolve configuration
	 * @param url      target URL (e.g. http://example.com/health)
	 * @param headers  headers as newline-separated "key: value" pairs (use "\n")
	 * @param body     optional request body (often ignored for GET)
	 * @param timeout  timeout in seconds; defaults to {@value #DEFAULT_QUERY_TIMEOUT} if null/≤0
	 * @return provider response or error
	 */
	@Tool(
		name = "ExecuteHttpGetQuery",
		description = """
		Execute an HTTP GET request on a host using the agent HTTP extension.
		Resolve a valid configuration from context, set hostname and timeout (default 10s),
		build the request (method=GET, url, optional headers/body), execute, and return the result or an error.

		Headers must be provided as a single string where each header is "key: value",
		separated by a line break (newline, "\n"). Example:
		Accept: application/json\\nAuthorization: Bearer abc123
		"""
	)
	public QueryResponse executeQuery(
		@ToolParam(description = "The hostname to execute HTTP Get query on.", required = true) final String hostname,
		@ToolParam(description = "The url to execute HTTP GET query on.", required = true) final String url,
		@ToolParam(
			description = "Headers as newline-separated \"key: value\" pairs",
			required = false
		) final String headers,
		@ToolParam(description = "Optional request body", required = false) final String body,
		@ToolParam(description = "Optional timeout in seconds (default: 10s).", required = false) final Long timeout
	) {
		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType("http")
			.map((IProtocolExtension extension) ->
				executeHttpQueryWithExtensionSafe(extension, hostname, HTTP_GET, url, headers, body, timeout)
			)
			.orElse(QueryResponse.builder().isError("No Extension found for HTTP.").build());
	}

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
				QueryResponse.builder().isError("No valid configuration found for HTTP on %s.".formatted(hostname)).build()
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
				.isError(
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
