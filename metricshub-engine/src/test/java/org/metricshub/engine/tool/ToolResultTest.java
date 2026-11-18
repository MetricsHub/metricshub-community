package org.metricshub.engine.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolResultTest {

	private static final List<List<String>> ROWS = List.of(List.of("1", "eth0"));

	@Test
	void testJsonCarriesTheType() throws Exception {
		// Held as objects, as the response of a host is
		assertEquals(
			"[{\"type\":\"table\",\"columns\":[\"ID\",\"2\"],\"rows\":[[\"1\",\"eth0\"]]}," +
				"{\"type\":\"text\",\"text\":\"Linux\"}]",
			new ObjectMapper().writeValueAsString(
				List.<Object>of(new ToolTable(List.of("ID", "2"), ROWS), new ToolText("Linux"))
			)
		);
	}

	@Test
	void testTableOfAQueryIsNamedAfterTheSelectedColumns() {
		assertEquals(
			new ToolTable(List.of("Index", "Name"), ROWS),
			ToolTable.ofQuery("SELECT Index, Name FROM Win32_NetworkAdapter", ROWS)
		);
		// The names of a SELECT * are not known
		assertEquals(new ToolTable(List.of(), ROWS), ToolTable.ofQuery("SELECT * FROM Win32_NetworkAdapter", ROWS));
	}
}
