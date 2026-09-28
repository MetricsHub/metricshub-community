package org.metricshub.web.service;

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

import java.util.function.BiFunction;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.process.runtime.ProcessControl;
import org.metricshub.agent.tunnel.TunnelService;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Starts the central tunnel supervision once the web application is ready. Runs in both editions
 * through {@link AgentStartupRunner}.
 */
@Service
@Slf4j
public class TunnelStartupHook implements StartupHook {

	private final AgentContextHolder agentContextHolder;
	private final ToolCallbackProvider toolCallbackProvider;
	private final BiFunction<AgentContextHolder, ToolCallbackProvider, TunnelService> serviceFactory;
	private final Consumer<Runnable> shutdownHookRegistrar;

	/**
	 * @param agentContextHolder   the running agent context
	 * @param toolCallbackProvider the tools the agent exposes
	 */
	@Autowired
	public TunnelStartupHook(
		final AgentContextHolder agentContextHolder,
		final ToolCallbackProvider toolCallbackProvider
	) {
		this(agentContextHolder, toolCallbackProvider, TunnelService::new, ProcessControl::addShutdownHook);
	}

	TunnelStartupHook(
		final AgentContextHolder agentContextHolder,
		final ToolCallbackProvider toolCallbackProvider,
		final BiFunction<AgentContextHolder, ToolCallbackProvider, TunnelService> serviceFactory,
		final Consumer<Runnable> shutdownHookRegistrar
	) {
		this.agentContextHolder = agentContextHolder;
		this.toolCallbackProvider = toolCallbackProvider;
		this.serviceFactory = serviceFactory;
		this.shutdownHookRegistrar = shutdownHookRegistrar;
	}

	@Override
	public void onStartup() {
		final TunnelService service = serviceFactory.apply(agentContextHolder, toolCallbackProvider);
		service.start();
		shutdownHookRegistrar.accept(service::shutdown);
		log.debug("Central tunnel service started.");
	}
}
