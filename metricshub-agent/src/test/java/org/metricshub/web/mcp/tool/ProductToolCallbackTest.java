package org.metricshub.web.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.web.mcp.IMCPToolService;
import org.metricshub.web.mcp.ToolAccess;
import org.metricshub.web.mcp.ToolAccess.Access;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

class ProductToolCallbackTest {

	/** A tool whose author forgot its class. */
	static class Unclassified {

		@Tool(name = "Forgotten", description = "A tool without an access class")
		public String forgotten() {
			return "";
		}
	}

	/** A tool with its class. */
	static class Classified {

		@ToolAccess(Access.READ)
		@Tool(name = "Listed", description = "A read tool")
		public String listed() {
			return "ran";
		}
	}

	@Test
	void everyAgentToolDeclaresReadAccess() throws ClassNotFoundException {
		// Every @Tool method of every IMCPToolService the agent ships, found on the classpath rather than listed
		// here: a tool added tomorrow is checked without anyone remembering to add it to this test.
		final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AssignableTypeFilter(IMCPToolService.class));
		final List<String> tools = new ArrayList<>();
		for (final BeanDefinition candidate : scanner.findCandidateComponents("org.metricshub.web.mcp")) {
			for (final Method method : Class.forName(candidate.getBeanClassName()).getMethods()) {
				if (method.isAnnotationPresent(Tool.class)) {
					final ToolAccess access = method.getAnnotation(ToolAccess.class);
					assertTrue(access != null, method + " has no @ToolAccess");
					// Every tool of the agent itself reads, CollectMetricsForHost and TestAvailableConnectorsForHost
					// included: they make the agent collect, and change nothing on the host (owner, 2026-11-04)
					assertEquals(Access.READ, access.value(), method.toString());
					tools.add(method.getName());
				}
			}
		}
		assertTrue(tools.size() >= 9, "the scan found the agent's tools: " + tools);
	}

	@Test
	void aToolWithoutAnAccessClassStopsTheAgent() {
		final Unclassified tool = new Unclassified();
		final ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(tool).build().getToolCallbacks();

		final IllegalStateException refused = assertThrows(
			IllegalStateException.class,
			() -> ProductToolCallback.classify(callbacks, List.of(tool))
		);
		assertTrue(refused.getMessage().contains("forgotten"), refused.getMessage());
	}

	@Test
	void aClassifiedToolKeepsItsClassAndStillRuns() {
		final Classified tool = new Classified();
		final ToolCallback[] callbacks = ProductToolCallback.classify(
			MethodToolCallbackProvider.builder().toolObjects(tool).build().getToolCallbacks(),
			List.of(tool)
		);

		assertEquals(1, callbacks.length);
		assertEquals(Access.READ, ((ProductToolCallback) callbacks[0]).access());
		assertEquals("Listed", callbacks[0].getToolDefinition().name());
		assertTrue(callbacks[0].call("{}").contains("ran"), "the classified tool still runs its method");
	}
}
