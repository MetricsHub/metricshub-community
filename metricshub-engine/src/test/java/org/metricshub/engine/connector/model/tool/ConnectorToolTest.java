package org.metricshub.engine.connector.model.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepOnlyMatchingLines;
import org.metricshub.engine.tool.ToolParameter;

class ConnectorToolTest {

	private static final Map<String, ToolParameter> PARAMETERS = Map.of(
		"service",
		new ToolParameter(null, "A systemd unit", true, null),
		"lines",
		new ToolParameter("integer", "How many lines", false, "100"),
		"unit",
		new ToolParameter(null, "A constrained unit", false, null, "[\\w@.-]+", null),
		"loose",
		new ToolParameter(null, "A loosely constrained value", false, null, ".*", null)
	);

	private static ConnectorTool tool(final Source source) {
		return ConnectorTool.builder()
			.sources(Map.of("logs", source))
			.parameters(PARAMETERS)
			.result(new ToolResult("${source::logs}", null))
			.build();
	}

	private static void assertAccepted(final Source source) {
		assertDoesNotThrow(() -> tool(source).toDefinition("logs"));
	}

	private static String assertRefused(final Source source) {
		return assertThrows(IllegalArgumentException.class, () -> tool(source).toDefinition("logs")).getMessage();
	}

	private static CommandLineSource commandLine(final String commandLine) {
		return CommandLineSource.builder().commandLine(commandLine).build();
	}

	@Test
	void testEscapedOrConstrainedParametersAreAccepted() {
		assertAccepted(commandLine("journalctl -n ${param::lines} -u '${esc(bash)::param::service}' ${param::unit}"));
		assertAccepted(commandLine("echo 'unit: ${esc(bash)::param::service}' \"$HOME\""));
		assertAccepted(commandLine("Get-Service -Name '${esc(powershell)::param::service}'"));
		assertAccepted(
			SqlSource.builder().query("SELECT * FROM t WHERE name LIKE '%${esc(sql)::param::service}%'").build()
		);
		assertAccepted(
			WmiSource.builder().query("SELECT Name FROM Win32_Service WHERE Name=\"${esc(wql)::param::service}\"").build()
		);
		assertAccepted(HttpSource.builder().body("{\"name\": \"${esc(json)::param::service}\"}").build());
	}

	@Test
	void testUnescapedParameterIsRefused() {
		final String message = assertRefused(commandLine("journalctl -u ${param::service}"));
		assertTrue(message.contains("${esc(TYPE)::param::service}"), message);
		// A pattern that lets the shell metacharacters through is no constraint
		assertRefused(commandLine("journalctl -u ${param::loose}"));
	}

	@Test
	void testUnescapedParameterInAnAwkScriptIsRefused() {
		final CommandLineSource source = commandLine("journalctl -n 10");
		source.setComputes(List.of(Awk.builder().script("/${param::service}/ { print }").build()));

		assertRefused(source);
	}

	@Test
	void testUnescapedParameterInADataComputeIsAccepted() {
		final CommandLineSource source = commandLine("journalctl -n 10");
		source.setComputes(List.of(KeepOnlyMatchingLines.builder().column(1).regExp("${param::service}").build()));

		assertAccepted(source);
	}

	@Test
	void testEscapeOutsideItsQuotesIsRefused() {
		final String message = assertRefused(commandLine("echo ${esc(bash)::param::service}"));
		assertTrue(message.contains("between ' quotes"), message);
		// Single quotes inside double quotes are just characters
		assertRefused(commandLine("sh -c \"journalctl -u '${esc(bash)::param::service}'\""));
		// A quote escaped outside of any string does not open one
		assertRefused(commandLine("echo \\'${esc(bash)::param::service}'"));
		assertRefused(SqlSource.builder().query("SELECT * FROM t WHERE id = ${esc(sql)::param::service}").build());
		assertRefused(HttpSource.builder().body("{\"name\": ${esc(json)::param::service}}").build());
	}

	@Test
	void testEscapeOfAnotherLanguageIsRefused() {
		final String message = assertRefused(commandLine("echo \"${esc(json)::param::service}\""));
		assertTrue(message.contains("BASH"), message);
		assertRefused(SqlSource.builder().query("SELECT * FROM t WHERE name = '${esc(bash)::param::service}'").build());
		assertRefused(
			WmiSource.builder().query("SELECT * FROM Win32_Service WHERE Name = '${esc(sql)::param::service}'").build()
		);
	}

	@Test
	void testUnknownEscapeTypeIsRefused() {
		final String message = assertRefused(commandLine("journalctl -n '${esc(bsh)::param::lines}'"));
		assertTrue(message.contains("bsh"), message);
	}
}
