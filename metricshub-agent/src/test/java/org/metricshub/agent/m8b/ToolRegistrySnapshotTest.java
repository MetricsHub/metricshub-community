package org.metricshub.agent.m8b;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.m8b.protocol.ToolDescriptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

class ToolRegistrySnapshotTest {

	private static final String HOST_SCHEMA =
		"{\"type\":\"object\",\"properties\":{\"hostname\":{\"type\":\"string\"}},\"required\":[\"hostname\"]}";

	private static ToolCallback callback(final String name, final String description, final String schema) {
		// Mocked rather than built: the Spring AI builder rejects blank schemas, which is exactly the
		// case the snapshot must tolerate
		final ToolDefinition definition = mock(ToolDefinition.class);
		when(definition.name()).thenReturn(name);
		when(definition.description()).thenReturn(description);
		when(definition.inputSchema()).thenReturn(schema);
		final ToolCallback callback = mock(ToolCallback.class);
		when(callback.getToolDefinition()).thenReturn(definition);
		return callback;
	}

	private static ToolCallbackProvider provider(final ToolCallback... callbacks) {
		final ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
		when(provider.getToolCallbacks()).thenReturn(callbacks);
		return provider;
	}

	@Test
	void shouldSortToolsAndKeepCallbacks() {
		final ToolCallback ping = callback("PingHost", "Pings hosts", HOST_SCHEMA);
		final ToolCallback list = callback("ListHosts", "Lists hosts", "");

		final ToolRegistrySnapshot snapshot = ToolRegistrySnapshot.from(provider(ping, list), Set.of());

		assertEquals(List.of("ListHosts", "PingHost"), snapshot.tools().stream().map(ToolDescriptor::name).toList());
		assertSame(ping, snapshot.callbacks().get("PingHost"));
		assertSame(list, snapshot.callbacks().get("ListHosts"));
		assertTrue(snapshot.revision().startsWith("sha256:"));
	}

	@Test
	void shouldDefaultBlankSchemaAndDescription() {
		final ToolRegistrySnapshot snapshot = ToolRegistrySnapshot.from(provider(callback("ListHosts", "", "")), Set.of());

		final ToolDescriptor tool = snapshot.tools().get(0);
		assertEquals("ListHosts", tool.description(), "A blank description falls back to the tool name");
		assertEquals("object", tool.inputSchema().get("type").asText());
		assertTrue(tool.inputSchema().get("properties").isEmpty());
	}

	@Test
	void shouldExcludeConfiguredTools() {
		final ToolCallbackProvider provider = provider(
			callback("ExecuteSshCommandline", "Runs commands", HOST_SCHEMA),
			callback("ListHosts", "Lists hosts", "")
		);

		final ToolRegistrySnapshot all = ToolRegistrySnapshot.from(provider, Set.of());
		final ToolRegistrySnapshot filtered = ToolRegistrySnapshot.from(provider, Set.of("ExecuteSshCommandline"));

		assertEquals(List.of("ListHosts"), filtered.tools().stream().map(ToolDescriptor::name).toList());
		assertTrue(filtered.callbacks().containsKey("ListHosts"));
		assertTrue(!filtered.callbacks().containsKey("ExecuteSshCommandline"));
		assertNotEquals(all.revision(), filtered.revision(), "Excluding a tool changes the advertised registry");
	}

	@Test
	void revisionShouldNotDependOnCallbackOrder() {
		final ToolCallback ping = callback("PingHost", "Pings hosts", HOST_SCHEMA);
		final ToolCallback list = callback("ListHosts", "Lists hosts", "");

		assertEquals(
			ToolRegistrySnapshot.from(provider(ping, list), Set.of()).revision(),
			ToolRegistrySnapshot.from(provider(list, ping), Set.of()).revision()
		);
	}

	@Test
	void revisionShouldChangeWhenASchemaChanges() {
		final ToolRegistrySnapshot v1 = ToolRegistrySnapshot.from(
			provider(callback("PingHost", "Pings", HOST_SCHEMA)),
			Set.of()
		);
		final ToolRegistrySnapshot v2 = ToolRegistrySnapshot.from(
			provider(
				callback("PingHost", "Pings", "{\"type\":\"object\",\"properties\":{\"hostname\":{\"type\":\"array\"}}}")
			),
			Set.of()
		);

		assertNotEquals(v1.revision(), v2.revision());
	}

	@Test
	void shouldRejectInvalidSchema() {
		final ToolCallbackProvider provider = provider(callback("Broken", "Broken", "{not json"));

		assertThrows(IllegalStateException.class, () -> ToolRegistrySnapshot.from(provider, Set.of()));
	}
}
