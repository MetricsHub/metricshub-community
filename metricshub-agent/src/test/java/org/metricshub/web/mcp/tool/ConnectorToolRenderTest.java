package org.metricshub.web.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.web.mcp.HostToolResponse;
import org.metricshub.web.mcp.MultiHostToolResponse;
import org.metricshub.web.mcp.QueryResponse;
import org.metricshub.web.mcp.tool.ConnectorToolCallbacks.ToolTable;
import org.metricshub.web.mcp.tool.ConnectorToolCallbacks.ToolText;

/**
 * What a client of the agent's MCP servers reads for a connector tool.
 */
class ConnectorToolRenderTest {

	private static HostToolResponse<Object> host(final String hostname, final Object response) {
		return HostToolResponse.<Object>builder().hostname(hostname).response(response).build();
	}

	@SafeVarargs
	private static List<String> render(final HostToolResponse<Object>... hosts) {
		return ConnectorToolCallbacks.render(MultiHostToolResponse.<Object>builder().hosts(List.of(hosts)).build());
	}

	@Test
	void testTableWithColumnNames() {
		assertEquals(
			"| pid | name |\n| --- | --- |\n| 4212 | java.exe |\n| 4 | System |",
			new ToolTable(List.of("pid", "name"), List.of(List.of("4212", "java.exe"), List.of("4", "System"))).toMarkdown()
		);
	}

	@Test
	void testMissingNamesAndShorterRowsAreCompleted() {
		// No name at all
		assertEquals(
			"| Column 1 | Column 2 |\n| --- | --- |\n| a | 1 |",
			new ToolTable(List.of(), List.of(List.of("a", "1"))).toMarkdown()
		);
		// Fewer names than cells, and rows of different widths
		assertEquals(
			"| name | Column 2 | Column 3 |\n| --- | --- | --- |\n| a | 1 | x |\n| b |  |  |",
			new ToolTable(List.of("name"), List.of(List.of("a", "1", "x"), List.of("b"))).toMarkdown()
		);
		// More names than cells
		assertEquals(
			"| name | value |\n| --- | --- |\n| a |  |",
			new ToolTable(List.of("name", "value"), List.of(List.of("a"))).toMarkdown()
		);
	}

	@Test
	void testCellStaysOnOneLineOfTheTable() {
		assertEquals(
			"| a\\|b |\n| --- |\n| x \\| y |\n| one<br>two<br>three<br>four |\n|  |",
			new ToolTable(
				List.of("a|b"),
				List.of(List.of("x | y"), List.of("one\r\ntwo\nthree\rfour"), Arrays.asList((String) null))
			).toMarkdown()
		);
	}

	@Test
	void testEmptyTable() {
		assertEquals("| pid | name |\n| --- | --- |", new ToolTable(List.of("pid", "name"), List.of()).toMarkdown());
		assertEquals("(empty)", new ToolTable(List.of(), List.of()).toMarkdown());
	}

	@Test
	void testTextIsKeptAsItIs() {
		final String text = "# not a heading\n| not | a table |\nfirst; second \"quoted\" back\\slash\n\n  indented\n";
		assertEquals(List.of("## srv-01\n\n" + text), render(host("srv-01", new ToolText(text))));
	}

	@Test
	void testOneTextPerHostInTheOrderOfTheHosts() {
		assertEquals(
			List.of(
				"## win-01\n\n| pid |\n| --- |\n| 4212 |",
				"## srv-01\n\nPING 10.0.0.1",
				"## nope\n\nError: Host nope is not configured.",
				"Error: Hostname must not be null"
			),
			render(
				host("win-01", new ToolTable(List.of("pid"), List.of(List.of("4212")))),
				host("srv-01", new ToolText("PING 10.0.0.1")),
				host("nope", QueryResponse.builder().error("Host nope is not configured.").build()),
				host(null, QueryResponse.builder().error("Hostname must not be null").build())
			)
		);
	}

	@Test
	void testErrorOfTheWholeCallIsItsMessage() {
		assertEquals(
			List.of("The hostname parameter is required."),
			ConnectorToolCallbacks.render(MultiHostToolResponse.buildError("The hostname parameter is required."))
		);
	}
}
