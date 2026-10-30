package org.metricshub.engine.connector.model.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.tool.ToolParameter;

class ConnectorToolTest {

	private static final Map<String, ToolParameter> PARAMETERS = Map.of(
		"service",
		new ToolParameter(null, "A systemd unit", true, null),
		"lines",
		new ToolParameter("integer", "How many lines", false, "100"),
		"unit",
		new ToolParameter(null, "A constrained unit", false, null, "[\\w@.-]+", null)
	);

	private static ConnectorTool tool(final Source source) {
		return ConnectorTool.builder()
			.sources(Map.of("logs", source))
			.parameters(PARAMETERS)
			.result(new ToolResult("${source::logs}", null))
			.build();
	}

	private static CommandLineSource commandLine(final String commandLine) {
		return CommandLineSource.builder().commandLine(commandLine).build();
	}

	@Test
	void testEscapedOrConstrainedParametersAreAccepted() {
		assertDoesNotThrow(() ->
			tool(commandLine("journalctl -n ${param::lines} -u '${esc(bash)::param::service}' ${param::unit}")).toDefinition(
				"serviceLogs"
			)
		);
	}

	@Test
	void testUnescapedFreeTextParameterIsRefused() {
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			tool(commandLine("journalctl -u ${param::service}")).toDefinition("serviceLogs")
		);
		assertTrue(exception.getMessage().contains("${esc(TYPE)::param::service}"), exception.getMessage());
	}

	@Test
	void testUnescapedFreeTextParameterInAComputeIsRefused() {
		final CommandLineSource source = commandLine("journalctl -n 10");
		source.setComputes(List.of(Awk.builder().script("/${param::service}/ { print }").build()));

		assertThrows(IllegalArgumentException.class, () -> tool(source).toDefinition("serviceLogs"));
	}

	@Test
	void testUnknownEscapeTypeIsRefused() {
		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			tool(commandLine("journalctl -n ${esc(bsh)::param::lines}")).toDefinition("serviceLogs")
		);
		assertTrue(exception.getMessage().contains("bsh"), exception.getMessage());
	}
}
