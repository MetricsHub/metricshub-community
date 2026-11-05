package org.metricshub.agent.tunnel;

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
import org.metricshub.agent.tunnel.protocol.ToolDescriptor;
import org.metricshub.web.mcp.ToolAccess;
import org.metricshub.web.mcp.tool.ProductToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

class ToolRegistrySnapshotTest {

	private static final String HOST_SCHEMA = "{\"type\":\"object\",\"properties\":{\"hostname\":{\"type\":\"string\"}},\"required\":[\"hostname\"]}";

	private static ToolCallback callback(final String name, final String description, final String schema) {
		// Mocked rather than built: the Spring AI builder rejects blank schemas, which is exactly the
		// case the snapshot must tolerate
		final ToolDefinition definition = mock(ToolDefinition.class);
		when(definition.name()).thenReturn(name);
		when(definition.description()).thenReturn(description);
		when(definition.inputSchema()).thenReturn(schema);
		final ToolCallback callback = mock(ToolCallback.class);
		when(callback.getToolDefinition()).thenReturn(definition);
		// The agent's own tool, as the configuration classifies it
		return new ProductToolCallback(callback, ToolAccess.Access.READ);
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

		final ToolRegistrySnapshot snapshot = ToolRegistrySnapshot.from(provider(ping, list), Set.of(), () -> null);

		assertEquals(
			List.of("ListHostTools", "ListHosts", "PingHost"),
			snapshot.tools().stream().map(ToolDescriptor::name).toList()
		);
		assertSame(ping, snapshot.callbacks().get("PingHost"));
		assertSame(list, snapshot.callbacks().get("ListHosts"));
		assertTrue(snapshot.revision().startsWith("sha256:"));
	}

	@Test
	void shouldDefaultBlankSchemaAndDescription() {
		final ToolRegistrySnapshot snapshot = ToolRegistrySnapshot.from(
			provider(callback("ListHosts", "", "")),
			Set.of(),
			() -> null
		);

		final ToolDescriptor tool = snapshot.tools()
			.stream()
			.filter(t -> t.name().equals("ListHosts"))
			.findFirst()
			.orElseThrow();
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

		final ToolRegistrySnapshot all = ToolRegistrySnapshot.from(provider, Set.of(), () -> null);
		final ToolRegistrySnapshot filtered = ToolRegistrySnapshot.from(
			provider,
			Set.of("ExecuteSshCommandline"),
			() -> null
		);

		assertEquals(List.of("ListHostTools", "ListHosts"), filtered.tools().stream().map(ToolDescriptor::name).toList());
		assertTrue(filtered.callbacks().containsKey("ListHosts"));
		assertTrue(!filtered.callbacks().containsKey("ExecuteSshCommandline"));
		assertNotEquals(all.revision(), filtered.revision(), "Excluding a tool changes the advertised registry");
	}

	@Test
	void revisionShouldNotDependOnCallbackOrder() {
		final ToolCallback ping = callback("PingHost", "Pings hosts", HOST_SCHEMA);
		final ToolCallback list = callback("ListHosts", "Lists hosts", "");

		assertEquals(
			ToolRegistrySnapshot.from(provider(ping, list), Set.of(), () -> null).revision(),
			ToolRegistrySnapshot.from(provider(list, ping), Set.of(), () -> null).revision()
		);
	}

	@Test
	void revisionShouldChangeWhenASchemaChanges() {
		final ToolRegistrySnapshot v1 = ToolRegistrySnapshot.from(
			provider(callback("PingHost", "Pings", HOST_SCHEMA)),
			Set.of(),
			() -> null
		);
		final ToolRegistrySnapshot v2 = ToolRegistrySnapshot.from(
			provider(
				callback("PingHost", "Pings", "{\"type\":\"object\",\"properties\":{\"hostname\":{\"type\":\"array\"}}}")
			),
			Set.of(),
			() -> null
		);

		assertNotEquals(v1.revision(), v2.revision());
	}

	@Test
	void shouldRejectInvalidSchema() {
		final ToolCallbackProvider provider = provider(callback("Broken", "Broken", "{not json"));

		assertThrows(IllegalStateException.class, () -> ToolRegistrySnapshot.from(provider, Set.of(), () -> null));
	}

	@Test
	void eachToolIsAdvertisedWithItsAccess() {
		final ToolCallback ssh = dynamic("ssh_runCommand", true);
		final ToolCallback snmp = dynamic("snmp_get", false);
		final ToolCallback unknown = ((ProductToolCallback) callback("Unknown", "Of no known kind", "")).delegate();
		final ToolCallback list = callback("ListHosts", "Lists hosts", "");

		final ToolRegistrySnapshot snapshot = ToolRegistrySnapshot.from(
			provider(ssh, snmp, unknown, list),
			Set.of(),
			() -> null
		);

		// The host-scoped tools are not advertised: Central learns them per host, with their access, from ListHostTools
		assertEquals(
			List.of("ListHostTools=read", "ListHosts=read"),
			snapshot.tools().stream().map(tool -> tool.name() + "=" + tool.access()).toList(),
			"one of no known kind is not advertised"
		);
		assertEquals(
			Set.of("ListHostTools", "ListHosts", "snmp_get", "ssh_runCommand"),
			snapshot.callbacks().keySet(),
			"nor can Central invoke it by name"
		);
		assertEquals("write", ToolRegistrySnapshot.describe(snapshot.callbacks().get("ssh_runCommand")).access());
		assertEquals("read", ToolRegistrySnapshot.describe(snapshot.callbacks().get("snmp_get")).access());
	}

	private static ToolCallback dynamic(final String name, final boolean destructive) {
		return new org.metricshub.web.mcp.tool.DynamicToolCallback(
			org.springframework.ai.tool.definition.ToolDefinition.builder()
				.name(name)
				.description(name)
				.inputSchema(HOST_SCHEMA)
				.build(),
			arguments -> "",
			destructive
		);
	}
}
