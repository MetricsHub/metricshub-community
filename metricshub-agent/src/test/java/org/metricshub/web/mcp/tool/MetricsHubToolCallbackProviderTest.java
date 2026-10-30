package org.metricshub.web.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class MetricsHubToolCallbackProviderTest {

	private static ToolCallback callback(final String name) {
		return new DynamicToolCallback(
			ToolDefinition.builder().name(name).description(name).inputSchema("{\"type\":\"object\"}").build(),
			arguments -> Map.of()
		);
	}

	private static List<String> names(final ToolCallback[] callbacks) {
		return List.of(callbacks)
			.stream()
			.map(callback -> callback.getToolDefinition().name())
			.toList();
	}

	@Test
	void testDynamicToolsFollowTheContextGeneration() {
		final AgentContextHolder holder = new AgentContextHolder(mock(AgentContext.class));
		final AtomicInteger builds = new AtomicInteger();
		final MetricsHubToolCallbackProvider provider = new MetricsHubToolCallbackProvider(
			new ToolCallback[] { callback("ListHosts") },
			holder,
			List.of(ignored -> List.of(callback("ipmi_sel_" + builds.incrementAndGet())))
		);

		assertEquals(List.of("ListHosts", "ipmi_sel_1"), names(provider.getToolCallbacks()));
		// Same generation: built once
		assertEquals(List.of("ListHosts", "ipmi_sel_1"), names(provider.getToolCallbacks()));

		holder.setAgentContext(mock(AgentContext.class));
		assertEquals(List.of("ListHosts", "ipmi_sel_2"), names(provider.getToolCallbacks()));
	}

	@Test
	void testADuplicateNameKeepsTheFirstDeclaration() {
		final AgentContextHolder holder = new AgentContextHolder(mock(AgentContext.class));
		final ToolCallback product = callback("http_get");
		final MetricsHubToolCallbackProvider provider = new MetricsHubToolCallbackProvider(
			new ToolCallback[] { product },
			holder,
			// A duplicate differing only by case is one too: the chat resolves names ignoring case
			List.of(ignored -> List.of(callback("http_get"), callback("HTTP_Get"), callback("http_request")))
		);

		final ToolCallback[] callbacks = provider.getToolCallbacks();

		assertEquals(List.of("http_get", "http_request"), names(callbacks));
		assertEquals(product, callbacks[0]);
	}

	@Test
	void testAFailingFactoryLeavesTheStaticTools() {
		final AgentContextHolder holder = new AgentContextHolder(mock(AgentContext.class));
		final MetricsHubToolCallbackProvider provider = new MetricsHubToolCallbackProvider(
			new ToolCallback[] { callback("ListHosts") },
			holder,
			List.of(
				ignored -> {
					throw new IllegalStateException("broken connector");
				},
				ignored -> List.of(callback("Windows_listProcesses"))
			)
		);

		assertEquals(List.of("ListHosts", "Windows_listProcesses"), names(provider.getToolCallbacks()));
	}

	@Test
	void testDestructiveToolsAreExposedOnlyWithTheFlagButAlwaysAdvertisedToCentral() {
		final AgentContextHolder holder = new AgentContextHolder(mock(AgentContext.class));
		final ToolCallback reboot = new DynamicToolCallback(
			ToolDefinition.builder().name("ilo_reboot").description("Reboots.").inputSchema("{\"type\":\"object\"}").build(),
			arguments -> Map.of(),
			true
		);
		final MetricsHubToolCallbackProvider provider = new MetricsHubToolCallbackProvider(
			new ToolCallback[] { callback("ListHosts") },
			holder,
			List.of(ignored -> List.of(callback("ipmi_sel"), reboot))
		);
		try {
			System.clearProperty(HostTools.DESTRUCTIVE_TOOLS_FLAG);
			assertEquals(List.of("ListHosts", "ipmi_sel"), names(provider.getToolCallbacks()));
			assertEquals(List.of("ListHosts", "ipmi_sel", "ilo_reboot"), names(provider.getAllToolCallbacks()));

			System.setProperty(HostTools.DESTRUCTIVE_TOOLS_FLAG, "true");
			assertEquals(List.of("ListHosts", "ipmi_sel", "ilo_reboot"), names(provider.getToolCallbacks()));
		} finally {
			System.clearProperty(HostTools.DESTRUCTIVE_TOOLS_FLAG);
		}
	}
}
