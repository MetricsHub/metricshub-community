package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;
import org.metricshub.engine.tool.ToolDefinition;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IpmiExtensionToolsTest {

	@Mock
	private IpmiRequestExecutor ipmiRequestExecutorMock;

	@InjectMocks
	private IpmiExtension ipmiExtension;

	private static IpmiConfiguration configuration() {
		final IpmiConfiguration configuration = IpmiConfiguration.builder().build();
		configuration.setHostname("bmc");
		return configuration;
	}

	@Test
	void testGetTools() {
		final List<ToolDefinition> tools = ipmiExtension.getTools();
		assertEquals(
			List.of("sensors", "sel", "chassisStatus", "chassisAction", "raw"),
			tools.stream().map(ToolDefinition::name).toList()
		);
		assertEquals(
			List.of("chassisAction", "raw"),
			tools.stream().filter(ToolDefinition::destructive).map(ToolDefinition::name).toList()
		);
	}

	@Test
	void testExecuteToolReads() throws Exception {
		final IpmiConfiguration configuration = configuration();
		when(ipmiRequestExecutorMock.executeIpmiGetSensors("bmc", configuration)).thenReturn("sensors");
		when(ipmiRequestExecutorMock.executeIpmiDetection("bmc", configuration)).thenReturn("on");

		assertEquals("sensors", ipmiExtension.executeTool("sensors", configuration, JsonNodeFactory.instance.objectNode()));
		assertEquals(
			"on",
			ipmiExtension.executeTool("chassisStatus", configuration, JsonNodeFactory.instance.objectNode())
		);
	}

	@Test
	void testExecuteToolChassisActionAndRaw() throws Exception {
		final IpmiConfiguration configuration = configuration();
		when(ipmiRequestExecutorMock.executeIpmiChassisIdentify("bmc", configuration, 30, false)).thenReturn("identify");
		when(ipmiRequestExecutorMock.executeIpmiChassisControl(eq("bmc"), eq(configuration), any())).thenReturn("off");
		when(ipmiRequestExecutorMock.executeIpmiRaw(eq("bmc"), eq(configuration), any(IpmiRawSource.class))).thenReturn(
			"00;01"
		);

		assertEquals(
			"identify",
			ipmiExtension.executeTool(
				"chassisAction",
				configuration,
				JsonNodeFactory.instance.objectNode().put("action", "identify").put("seconds", "30")
			)
		);
		assertEquals(
			"off",
			ipmiExtension.executeTool(
				"chassisAction",
				configuration,
				JsonNodeFactory.instance.objectNode().put("action", "off")
			)
		);
		assertEquals(
			"00;01",
			ipmiExtension.executeTool(
				"raw",
				configuration,
				JsonNodeFactory.instance.objectNode().put("netFn", "0x06").put("command", "0x01")
			)
		);
		final ArgumentCaptor<IpmiRawSource> raw = ArgumentCaptor.forClass(IpmiRawSource.class);
		verify(ipmiRequestExecutorMock).executeIpmiRaw(eq("bmc"), eq(configuration), raw.capture());
		assertEquals("0x06", raw.getValue().getNetFn());
		assertEquals("0x01", raw.getValue().getCommand());
	}

	@Test
	void testExecuteToolRejectsUnknownTool() {
		assertThrows(IllegalArgumentException.class, () ->
			ipmiExtension.executeTool("nope", configuration(), JsonNodeFactory.instance.objectNode())
		);
	}
}
