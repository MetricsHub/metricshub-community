package org.metricshub.web.mcp.transport;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerChangeNotificationProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties.ApiType;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Registers the hardened legacy MCP "HTTP with SSE" transport ({@link LegacySseServerTransportProvider}).
 * <p>
 * Two modes are supported, depending on {@code spring.ai.mcp.server.protocol}:
 * </p>
 * <ul>
 * <li>{@code STREAMABLE} (the shipped default): Spring AI auto-configures the Streamable HTTP transport and its MCP
 * server on {@code /mcp}; this configuration adds a second MCP server, exposing the same tools, resources, prompts and
 * completions, on the legacy SSE endpoints so that existing clients keep working.</li>
 * <li>{@code SSE}: the hardened transport is exposed as the {@code McpServerTransportProvider} bean, which makes the
 * Spring AI SSE auto-configuration back off, and Spring AI binds its auto-configured MCP server to it.</li>
 * </ul>
 * <p>
 * Set {@code mcp.sse.enabled=false} to stop serving the legacy transport.
 * </p>
 */
@Configuration
@EnableConfigurationProperties({ McpSseProperties.class, McpServerSseProperties.class })
@ConditionalOnProperty(prefix = McpSseProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
// Same guard as the Spring AI HTTP transports: the MCP server must be enabled and not in stdio mode, otherwise the
// Spring AI beans this configuration depends on do not exist
@Conditional(McpServerStdioDisabledCondition.class)
@Slf4j
public class LegacySseMcpServerConfiguration {

	private static final String SPRING_AI_MCP_SERVER_PREFIX = "spring.ai.mcp.server";

	/**
	 * Builds the hardened legacy SSE transport provider from the Spring AI SSE properties and the MetricsHub
	 * hardening properties.
	 *
	 * @param mcpServerObjectMapper the object mapper dedicated to the MCP server
	 * @param sseProperties         the Spring AI SSE properties (endpoints, base URL, keep-alive interval)
	 * @param legacySseProperties   the MetricsHub hardening properties
	 * @return the transport provider
	 */
	static LegacySseServerTransportProvider createTransportProvider(
		final ObjectMapper mcpServerObjectMapper,
		final McpServerSseProperties sseProperties,
		final McpSseProperties legacySseProperties
	) {
		return LegacySseServerTransportProvider.builder()
			.jsonMapper(new JacksonMcpJsonMapper(mcpServerObjectMapper))
			.baseUrl(sseProperties.getBaseUrl())
			.sseEndpoint(sseProperties.getSseEndpoint())
			.messageEndpoint(sseProperties.getSseMessageEndpoint())
			.keepAliveInterval(sseProperties.getKeepAliveInterval())
			.messageTimeout(legacySseProperties.getMessageTimeout())
			.pingTimeout(legacySseProperties.getPingTimeout())
			.initializationTimeout(legacySseProperties.getInitializationTimeout())
			.build();
	}

	/**
	 * Serves the legacy SSE transport alongside the Streamable HTTP transport auto-configured by Spring AI.
	 */
	@Configuration
	@ConditionalOnProperty(prefix = SPRING_AI_MCP_SERVER_PREFIX, name = "protocol", havingValue = "STREAMABLE")
	static class AlongsideStreamableHttpConfiguration {

		/**
		 * Builds a second MCP server, bound to the legacy SSE transport, exposing the same specifications as the
		 * auto-configured Streamable HTTP server.
		 *
		 * @param mcpServerObjectMapper       the object mapper dedicated to the MCP server
		 * @param sseProperties               the Spring AI SSE properties
		 * @param legacySseProperties         the MetricsHub hardening properties
		 * @param serverProperties            the Spring AI MCP server properties
		 * @param changeNotificationProperties the Spring AI change notification properties
		 * @param tools                       the tool specifications
		 * @param resources                   the resource specifications
		 * @param resourceTemplates           the resource template specifications
		 * @param prompts                     the prompt specifications
		 * @param completions                 the completion specifications
		 * @return the holder of the legacy SSE MCP server
		 */
		@Bean
		LegacySseMcpServer legacySseMcpServer(
			@Qualifier("mcpServerObjectMapper") final ObjectMapper mcpServerObjectMapper,
			final McpServerSseProperties sseProperties,
			final McpSseProperties legacySseProperties,
			final McpServerProperties serverProperties,
			final McpServerChangeNotificationProperties changeNotificationProperties,
			final ObjectProvider<List<SyncToolSpecification>> tools,
			final ObjectProvider<List<SyncResourceSpecification>> resources,
			final ObjectProvider<List<SyncResourceTemplateSpecification>> resourceTemplates,
			final ObjectProvider<List<SyncPromptSpecification>> prompts,
			final ObjectProvider<List<SyncCompletionSpecification>> completions
		) {
			if (serverProperties.getType() != ApiType.SYNC) {
				throw new IllegalStateException(
					"The legacy MCP SSE transport only supports spring.ai.mcp.server.type=SYNC. " +
						"Use the SYNC server type or disable the legacy transport with mcp.sse.enabled=false."
				);
			}

			final LegacySseServerTransportProvider transportProvider = createTransportProvider(
				mcpServerObjectMapper,
				sseProperties,
				legacySseProperties
			);

			final ServerCapabilities.Builder capabilities = ServerCapabilities.builder();
			final SyncSpecification<?> serverBuilder = McpServer.sync(transportProvider).serverInfo(
				serverProperties.getName(),
				serverProperties.getVersion()
			);

			if (serverProperties.getCapabilities().isTool()) {
				capabilities.tools(changeNotificationProperties.isToolChangeNotification());
				final List<SyncToolSpecification> toolSpecifications = flatten(tools);
				if (!toolSpecifications.isEmpty()) {
					serverBuilder.tools(toolSpecifications);
				}
			}

			if (serverProperties.getCapabilities().isResource()) {
				capabilities.resources(false, changeNotificationProperties.isResourceChangeNotification());
				final List<SyncResourceSpecification> resourceSpecifications = flatten(resources);
				if (!resourceSpecifications.isEmpty()) {
					serverBuilder.resources(resourceSpecifications);
				}
				final List<SyncResourceTemplateSpecification> resourceTemplateSpecifications = flatten(resourceTemplates);
				if (!resourceTemplateSpecifications.isEmpty()) {
					serverBuilder.resourceTemplates(resourceTemplateSpecifications);
				}
			}

			if (serverProperties.getCapabilities().isPrompt()) {
				capabilities.prompts(changeNotificationProperties.isPromptChangeNotification());
				final List<SyncPromptSpecification> promptSpecifications = flatten(prompts);
				if (!promptSpecifications.isEmpty()) {
					serverBuilder.prompts(promptSpecifications);
				}
			}

			if (serverProperties.getCapabilities().isCompletion()) {
				capabilities.completions();
				final List<SyncCompletionSpecification> completionSpecifications = flatten(completions);
				if (!completionSpecifications.isEmpty()) {
					serverBuilder.completions(completionSpecifications);
				}
			}

			final McpSyncServer server = serverBuilder
				.capabilities(capabilities.build())
				.instructions(serverProperties.getInstructions())
				.requestTimeout(serverProperties.getRequestTimeout())
				// Same execution model as the Spring AI auto-configuration in a servlet environment
				.immediateExecution(true)
				.build();

			log.info(
				"Legacy MCP SSE transport served on GET {} and POST {} alongside the Streamable HTTP transport",
				sseProperties.getSseEndpoint(),
				sseProperties.getSseMessageEndpoint()
			);

			return new LegacySseMcpServer(transportProvider, server);
		}

		/**
		 * Exposes the SSE and message endpoints of the legacy transport.
		 *
		 * @param legacySseMcpServer the holder of the legacy SSE MCP server
		 * @return the router function
		 */
		@Bean
		RouterFunction<ServerResponse> legacySseRouterFunction(final LegacySseMcpServer legacySseMcpServer) {
			return legacySseMcpServer.getRouterFunction();
		}

		/**
		 * Concatenates all the lists provided by an {@link ObjectProvider}.
		 *
		 * @param provider the provider
		 * @param <T>      the element type
		 * @return the concatenated list, never {@code null}
		 */
		private static <T> List<T> flatten(final ObjectProvider<List<T>> provider) {
			return provider.stream().flatMap(List::stream).toList();
		}
	}

	/**
	 * Replaces the Spring AI SSE transport provider with the hardened one when the MCP protocol is {@code SSE}.
	 */
	@Configuration
	@ConditionalOnProperty(
		prefix = SPRING_AI_MCP_SERVER_PREFIX,
		name = "protocol",
		havingValue = "SSE",
		matchIfMissing = true
	)
	static class ReplaceSdkSseTransportConfiguration {

		/**
		 * Exposes the hardened transport provider as the {@code McpServerTransportProvider} bean consumed by the
		 * Spring AI auto-configured MCP server.
		 *
		 * @param mcpServerObjectMapper the object mapper dedicated to the MCP server
		 * @param sseProperties         the Spring AI SSE properties
		 * @param legacySseProperties   the MetricsHub hardening properties
		 * @return the transport provider
		 */
		@Bean
		LegacySseServerTransportProvider legacySseServerTransportProvider(
			@Qualifier("mcpServerObjectMapper") final ObjectMapper mcpServerObjectMapper,
			final McpServerSseProperties sseProperties,
			final McpSseProperties legacySseProperties
		) {
			log.info(
				"Hardened legacy MCP SSE transport served on GET {} and POST {}",
				sseProperties.getSseEndpoint(),
				sseProperties.getSseMessageEndpoint()
			);
			return createTransportProvider(mcpServerObjectMapper, sseProperties, legacySseProperties);
		}

		/**
		 * Exposes the SSE and message endpoints of the legacy transport.
		 *
		 * @param transportProvider the transport provider
		 * @return the router function
		 */
		@Bean
		RouterFunction<ServerResponse> legacySseRouterFunction(final LegacySseServerTransportProvider transportProvider) {
			return transportProvider.getRouterFunction();
		}
	}
}
