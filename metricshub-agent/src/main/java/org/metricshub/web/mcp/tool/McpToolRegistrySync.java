package org.metricshub.web.mcp.tool;

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

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.web.mcp.transport.LegacySseMcpServer;
import org.metricshub.web.service.AgentLifecycleService;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Keeps the MCP servers' tool lists in step with {@link ToolCallbackProvider}. The servers take their tools once,
 * at startup; the connector and protocol tools change with every configuration reload, so after each restart the
 * tools that went away are removed and the new or changed ones added, which notifies the connected clients.
 */
@Slf4j
@Service
public class McpToolRegistrySync {

	private final ToolCallbackProvider toolCallbackProvider;
	private final List<McpSyncServer> servers;

	/**
	 * @param toolCallbackProvider  the tools the agent exposes
	 * @param servers               the Streamable HTTP MCP server, when enabled
	 * @param legacySseServer       the legacy SSE MCP server, when enabled
	 * @param agentLifecycleService where to register for the restarts
	 */
	@Autowired
	public McpToolRegistrySync(
		final ToolCallbackProvider toolCallbackProvider,
		final ObjectProvider<McpSyncServer> servers,
		final ObjectProvider<LegacySseMcpServer> legacySseServer,
		final AgentLifecycleService agentLifecycleService
	) {
		this(
			toolCallbackProvider,
			Stream.concat(servers.stream(), legacySseServer.stream().map(LegacySseMcpServer::getServer)).toList()
		);
		agentLifecycleService.addPostRestartHook(context -> refresh());
	}

	McpToolRegistrySync(final ToolCallbackProvider toolCallbackProvider, final List<McpSyncServer> servers) {
		this.toolCallbackProvider = toolCallbackProvider;
		this.servers = servers;
	}

	/**
	 * Brings every server's tool list to what the provider exposes now.
	 */
	public void refresh() {
		final Map<String, ToolCallback> current = new LinkedHashMap<>();
		for (final ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
			current.put(callback.getToolDefinition().name(), callback);
		}
		for (final McpSyncServer server : servers) {
			try {
				sync(server, current);
			} catch (Exception e) {
				log.error("Failed to refresh the MCP tools: {}", e.getMessage());
				log.debug("Failed to refresh the MCP tools:", e);
			}
		}
	}

	private static void sync(final McpSyncServer server, final Map<String, ToolCallback> current) {
		final Map<String, McpSchema.Tool> advertised = new LinkedHashMap<>();
		server.listTools().forEach(tool -> advertised.put(tool.name(), tool));
		final Map<String, SyncToolSpecification> specifications = new LinkedHashMap<>();
		current.forEach((name, callback) -> specifications.put(name, McpToolUtils.toSyncToolSpecification(callback)));

		int removed = 0;
		int added = 0;
		for (final Map.Entry<String, McpSchema.Tool> entry : advertised.entrySet()) {
			final ToolCallback callback = current.get(entry.getKey());
			if (callback == null || mustReplace(callback, specifications.get(entry.getKey()), entry.getValue())) {
				server.removeTool(entry.getKey());
				removed++;
			}
		}
		for (final Map.Entry<String, ToolCallback> entry : current.entrySet()) {
			final McpSchema.Tool tool = advertised.get(entry.getKey());
			if (tool == null || mustReplace(entry.getValue(), specifications.get(entry.getKey()), tool)) {
				server.addTool(specifications.get(entry.getKey()));
				added++;
			}
		}
		if (removed > 0 || added > 0) {
			log.info("MCP tools refreshed: {} removed, {} added, {} advertised.", removed, added, current.size());
		}
	}

	/**
	 * A dynamic tool is always re-registered: its body captures the connector or extension of the context it was
	 * built from, which a reload replaces even when the advertised schema stays the same. A static tool is
	 * re-registered only when its schema changed.
	 *
	 * @param callback      the tool as the provider exposes it now
	 * @param specification its MCP specification
	 * @param advertised    what the server advertises under that name
	 * @return whether the server's registration must be replaced
	 */
	private static boolean mustReplace(
		final ToolCallback callback,
		final SyncToolSpecification specification,
		final McpSchema.Tool advertised
	) {
		return callback instanceof DynamicToolCallback || !specification.tool().equals(advertised);
	}
}
