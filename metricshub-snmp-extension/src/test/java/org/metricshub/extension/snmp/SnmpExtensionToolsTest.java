package org.metricshub.extension.snmp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.engine.tool.ToolResult;
import org.metricshub.engine.tool.ToolTable;
import org.metricshub.engine.tool.ToolText;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnmpExtensionToolsTest {

	@Mock
	private SnmpRequestExecutor snmpRequestExecutorMock;

	@InjectMocks
	private SnmpExtension snmpExtension;

	private static SnmpConfiguration configuration() {
		final SnmpConfiguration configuration = SnmpConfiguration.builder().build();
		configuration.setHostname("host");
		return configuration;
	}

	@Test
	void testGetTools() {
		final List<ToolDefinition> tools = snmpExtension.getTools();
		assertEquals(List.of("get", "getNext", "walk", "table"), tools.stream().map(ToolDefinition::name).toList());
		assertTrue(tools.stream().noneMatch(ToolDefinition::destructive));
	}

	@Test
	void testExecuteToolGetNext() throws Exception {
		final SnmpConfiguration configuration = configuration();
		when(snmpRequestExecutorMock.executeSNMPGetNext("1.3.6.1.2.1.1", configuration, "host", false, null)).thenReturn(
			"1.3.6.1.2.1.1.1.0 OCTET_STRING Linux"
		);

		assertEquals(
			new ToolText("1.3.6.1.2.1.1.1.0 OCTET_STRING Linux"),
			snmpExtension.executeTool(
				"getNext",
				configuration,
				JsonNodeFactory.instance.objectNode().put("oid", "1.3.6.1.2.1.1")
			)
		);
	}

	@Test
	void testExecuteToolTableSplitsTheColumns() throws Exception {
		final SnmpConfiguration configuration = configuration();
		final ArgumentCaptor<String[]> columns = ArgumentCaptor.forClass(String[].class);
		when(
			snmpRequestExecutorMock.executeSNMPTable(
				eq("1.3.6.1.2.1.2.2.1"),
				columns.capture(),
				eq(configuration),
				eq("host"),
				eq(false),
				isNull()
			)
		).thenReturn(List.of(List.of("1", "eth0")));

		final ToolResult result = snmpExtension.executeTool(
			"table",
			configuration,
			JsonNodeFactory.instance.objectNode().put("oid", "1.3.6.1.2.1.2.2.1").put("columns", "ID, 2")
		);

		assertArrayEquals(new String[] { "ID", "2" }, columns.getValue());
		assertEquals(new ToolTable(List.of("ID", "2"), List.of(List.of("1", "eth0"))), result);
	}

	@Test
	void testExecuteToolWalkIsTheTextOfTheWalk() throws Exception {
		final SnmpConfiguration configuration = configuration();
		// A value on two lines: the lines of a walk are not the rows of a table
		final String walk = "1.3.6.1.2.1.1.1.0\tASN_OCTET_STR\tCisco IOS\nVersion 15\n1.3.6.1.2.1.1.3.0\tASN_TIMETICKS\t42";
		when(snmpRequestExecutorMock.executeSNMPWalk("1.3.6.1.2.1.1", configuration, "host", false, null)).thenReturn(walk);

		assertEquals(
			new ToolText(walk),
			snmpExtension.executeTool(
				"walk",
				configuration,
				JsonNodeFactory.instance.objectNode().put("oid", "1.3.6.1.2.1.1")
			)
		);
	}

	@Test
	void testExecuteToolFailsWhenTheRequestFails() throws Exception {
		// The executor returns nothing for a request that failed: an error for the host, not an empty result
		when(snmpRequestExecutorMock.executeSNMPTable(any(), any(), any(), any(), eq(false), isNull())).thenReturn(null);
		for (final String tool : List.of("get", "getNext", "walk", "table")) {
			assertThrows(
				IllegalStateException.class,
				() -> snmpExtension.executeTool(
					tool,
					configuration(),
					JsonNodeFactory.instance.objectNode().put("oid", "1.3.6.1.2.1.1").put("columns", "ID")
				),
				tool
			);
		}
	}

	@Test
	void testExecuteToolRejectsUnknownTool() {
		assertThrows(
			IllegalArgumentException.class,
			() -> snmpExtension.executeTool("nope", configuration(), JsonNodeFactory.instance.objectNode().put("oid", "1"))
		);
	}
}
