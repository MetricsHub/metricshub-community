package org.metricshub.extension.snmpv3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.tool.ToolDefinition;

class SnmpV3ExtensionToolsTest {

	@Test
	void testGetToolsAreTheSnmpOnes() {
		assertEquals(
			List.of("get", "getNext", "walk", "table"),
			new SnmpV3Extension().getTools().stream().map(ToolDefinition::name).toList()
		);
	}
}
