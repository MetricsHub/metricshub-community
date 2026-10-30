package org.metricshub.engine.connector.model.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.engine.tool.ToolParameter;

class ConnectorToolTest {

	private static final Map<String, ToolParameter> PARAMETERS = Map.of(
		"service",
		new ToolParameter(null, "A systemd unit", true, null),
		"lines",
		new ToolParameter("integer", "How many lines", false, "100"),
		"signal",
		new ToolParameter(null, "A signal", true, null, null, List.of("TERM", "KILL")),
		"path",
		new ToolParameter(null, "A log file", true, null, "/var/log/[a-z]+\\.log", null)
	);

	private static ConnectorTool tool(final Source source) {
		return ConnectorTool.builder()
			.sources(Map.of("logs", source))
			.parameters(PARAMETERS)
			.result(new ToolResult("${source::logs}", null))
			.build();
	}

	private static ToolDefinition definition(final String commandLine) {
		return tool(CommandLineSource.builder().commandLine(commandLine).build()).toDefinition("logs");
	}

	@Test
	void testPlainStringsTakeTheDefaultPattern() {
		final ToolDefinition definition = definition("journalctl -u ${param::service} -n ${param::lines}");
		assertEquals(ToolParameter.DEFAULT_PATTERN, definition.parameters().get("service").pattern());
		// A type, an enum or a pattern of the connector is left as is
		assertNull(definition.parameters().get("lines").pattern());
		assertNull(definition.parameters().get("signal").pattern());
		assertEquals("/var/log/[a-z]+\\.log", definition.parameters().get("path").pattern());

		// An argument breaking out of the command is refused before anything runs
		final Map<String, String> arguments = Map.of("signal", "TERM", "path", "/var/log/messages.log");
		final Map<String, String> breaking = new HashMap<>(arguments);
		breaking.put("service", "sshd; reboot");
		assertThrows(IllegalArgumentException.class, () -> definition.resolveArguments(breaking));
		final Map<String, String> unit = new HashMap<>(arguments);
		unit.put("service", "sshd.service");
		assertEquals("sshd.service", definition.resolveArguments(unit).get("service"));
	}

	@Test
	void testWherePlacedIsLeftToTheReview() {
		// Escaped or not, in a command, a request or a compute: what the connector does with a value is reviewed
		assertDoesNotThrow(() -> definition("kill -${param::signal} $(pgrep ${param::service})"));
		assertDoesNotThrow(() ->
			tool(HttpSource.builder().body("{\"unit\": \"${esc(json)::param::service}\"}").build()).toDefinition("logs")
		);
		final CommandLineSource source = CommandLineSource.builder().commandLine("tail ${param::path}").build();
		source.setComputes(List.of(Append.builder().column(1).value(";${attribute::service}").build()));
		assertDoesNotThrow(() -> tool(source).toDefinition("logs"));
	}

	@Test
	void testReferencesMustNameDeclaredParameters() {
		// A misspelled reference would stay in the source as is, a compute's included
		final IllegalArgumentException undeclared = assertThrows(IllegalArgumentException.class, () ->
			definition("journalctl -u ${param::servce}")
		);
		assertTrue(undeclared.getMessage().contains("servce"), undeclared.getMessage());
		final CommandLineSource source = CommandLineSource.builder().commandLine("ls").build();
		source.setComputes(List.of(Append.builder().column(1).value("${attribute::nope}").build()));
		assertThrows(IllegalArgumentException.class, () -> tool(source).toDefinition("logs"));
		// So would an optional one without default, absent from an invocation
		final ConnectorTool optional = ConnectorTool.builder()
			.sources(Map.of("logs", CommandLineSource.builder().commandLine("echo ${param::note}").build()))
			.parameters(Map.of("note", new ToolParameter(null, "A note", false, null)))
			.build();
		assertThrows(IllegalArgumentException.class, () -> optional.toDefinition("logs"));
		// And one escaped with an unknown type
		assertThrows(IllegalArgumentException.class, () -> definition("echo ${esc(power-shell)::param::service}"));
	}
}
