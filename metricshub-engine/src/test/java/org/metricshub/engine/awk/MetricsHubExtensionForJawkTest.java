package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jawk.Awk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * The MetricsHub functions of an awk source that read JSON (#1578). They are called from a script, as a connector
 * calls them: a function fills the array it is given.
 */
class MetricsHubExtensionForJawkTest {

	private static final String JSON = "{\"items\":[{\"name\":\"a;b\",\"value\":1,\"note\":\"x\\ny\"},{\"name\":\"b\",\"value\":10}]," +
		"\"ok\":true,\"off\":false,\"none\":null,\"ratio\":0.5}";

	/**
	 * Runs a script with the MetricsHub functions. The script reads the JSON document in the <code>json</code>
	 * variable.
	 */
	private static String run(final String script, final MetricsHubExtensionForJawk extension) throws Exception {
		final Awk awk = new Awk(List.of(extension, UtilityExtensionForJawk.INSTANCE), AwkExecutor.newAwkSettings(null));
		return awk.script(awk.compile(script)).input("").variables(Map.of("json", JSON)).execute();
	}

	private static String run(final String script) throws Exception {
		return run(script, MetricsHubExtensionForJawk.builder().hostname("host").build());
	}

	/**
	 * An extension whose HTTP requests give the specified result.
	 */
	private static MetricsHubExtensionForJawk httpGiving(final SourceTable result) {
		final SourceProcessor sourceProcessor = mock(SourceProcessor.class);
		when(sourceProcessor.process(any(HttpSource.class))).thenReturn(result);
		return MetricsHubExtensionForJawk.builder().hostname("host").sourceProcessor(sourceProcessor).build();
	}

	@Test
	void testJson2Table() throws Exception {
		// rows[i][0] is the path of the entry, the properties follow. A cell keeps its ";" and its line feed, a
		// missing property is an empty cell, and a cell that looks like a number compares as a number
		assertEquals("2\n/items[0]|a;b|1|x\ny|4\n/items[1]|b|10||4\n1\n", run("""
			BEGIN {
				n = json2Table(json, "/items", "name;value;note", rows)
				print n
				for (i = 0; i < n; i++) {
					print rows[i][0] "|" rows[i][1] "|" rows[i][2] "|" rows[i][3] "|" length(rows[i])
				}
				print (rows[1][2] > 9)
			}
			"""));
	}

	@Test
	void testJson2TableEventsParsingMode() throws Exception {
		assertEquals(
			"2 a;b 10\n",
			run(
				"BEGIN { n = json2Table(json, \"/items\", \"name;value\", rows, \"Events\"); print n, rows[0][1], rows[1][2] }"
			)
		);
	}

	@Test
	void testJson2TableEmptiesTheArray() throws Exception {
		// What the array held is gone, whatever the document
		assertEquals("0 0\n1 1\n", run("""
			BEGIN {
				rows[5] = "old"
				n = json2Table("not json", "/", "a", rows)
				print n, length(rows)
				rows["old"] = "old"
				n = json2Table(json, "/", "ratio", rows)
				print n, length(rows)
			}
			"""));
	}

	@Test
	void testJson2List() throws Exception {
		// Every leaf value by its path, as the json2List compute writes them
		assertEquals("9\na;b\nTRUE FALSE [NULL] 0.5\n1\n0\n", run("""
			BEGIN {
				list["old"] = "old"
				n = json2List(json, list)
				print n
				print list["/items[0]/name"]
				print list["/ok"], list["/off"], "[" list["/none"] "]", list["/ratio"]
				print (list["/items[1]/value"] > 9)
				print json2List("not json", list)
			}
			"""));
	}

	@Test
	void testExecuteHttpJsonRequest() throws Exception {
		// An object is an array indexed by its keys, an array is indexed from 0, a number is a number, true and
		// false are 1 and 0, null is an empty string
		assertEquals("5\na;b 11 2\n1 0 [] 1\n", run("""
			BEGIN {
				args["method"] = "get"
				args["path"] = "/api"
				response["old"] = "old"
				n = executeHttpJsonRequest(args, response)
				print n
				print response["items"][0]["name"], response["items"][1]["value"] + 1, length(response["items"])
				print response["ok"], response["off"], "[" response["none"] "]", response["ratio"] * 2
			}
			""", httpGiving(SourceTable.ofText(JSON))));
	}

	@Test
	void testExecuteHttpJsonRequestOnAnArray() throws Exception {
		assertEquals(
			"2 x 3\n",
			run(
				"BEGIN { args[\"path\"] = \"/api\"; n = executeHttpJsonRequest(args, response); print n, response[0], response[1][\"a\"] }",
				httpGiving(SourceTable.ofText("[\"x\", {\"a\": 3}]"))
			)
		);
	}

	/**
	 * A number keeps its digits: an unsigned 64-bit counter and a long decimal are not rounded to a double, and they
	 * still compare and compute as numbers.
	 */
	@Test
	void testExecuteHttpJsonRequestKeepsTheDigitsOfANumber() throws Exception {
		assertEquals(
			"18446744073709551615 1234567.123456789 42 1 3\n",
			run(
				"""
					BEGIN {
						args["path"] = "/api"
						executeHttpJsonRequest(args, r)
						print r["counter"], r["decimal"], r["small"], (r["counter"] > 9), r["half"] * 2
					}
					""",
				httpGiving(
					SourceTable.ofText(
						"{\"counter\":18446744073709551615,\"decimal\":1234567.123456789,\"small\":42,\"half\":1.5}"
					)
				)
			)
		);
	}

	@Test
	void testJson2ListKeepsTheLastValueOfAPath() throws Exception {
		assertEquals("1 second\n", run("""
			BEGIN {
				n = json2List("{\\"a/b\\":\\"first\\",\\"a\\":{\\"b\\":\\"second\\"}}", list)
				print n, list["/a/b"]
			}
			"""));
	}

	@Test
	void testExecuteHttpJsonRequestWithoutJson() throws Exception {
		final String script = "BEGIN { args[\"path\"] = \"/api\"; response[1] = 1; print executeHttpJsonRequest(args, response), length(response) }";

		// Not JSON, a JSON scalar, an empty result, a failed request: the array is empty
		assertEquals("0 0\n", run(script, httpGiving(SourceTable.ofText("<html>"))));
		assertEquals("0 0\n", run(script, httpGiving(SourceTable.ofText("42"))));
		// A document followed by something else is not a JSON document
		assertEquals("0 0\n", run(script, httpGiving(SourceTable.ofText("{\"status\":\"ok\"}<html>proxy error</html>"))));
		assertEquals("1 1\n", run(script, httpGiving(SourceTable.ofText("{\"status\":\"ok\"} \n"))));
		assertEquals("0 0\n", run(script, httpGiving(SourceTable.ofText(""))));
		assertEquals("0 0\n", run(script, httpGiving(SourceTable.empty())));
	}

	@Test
	void testJson2csvIsGone() {
		assertThrows(Exception.class, () -> run("BEGIN { args[\"jsonSource\"] = json; print json2csv(args) }"));
	}
}
