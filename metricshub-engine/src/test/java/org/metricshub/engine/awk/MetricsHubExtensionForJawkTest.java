package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jawk.Awk;
import io.jawk.jrt.AssocArray;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
	/**
	 * The result of a HTTP request asked with its status: the status line, a header, an empty line, the body.
	 */
	private static SourceTable answer(final int status, final String body) {
		return SourceTable.ofText("Status: " + status + "\nContent-Type: application/json\n\n" + body);
	}

	private static MetricsHubExtensionForJawk httpGiving(final SourceTable result) {
		final SourceProcessor sourceProcessor = mock(SourceProcessor.class);
		when(sourceProcessor.process(any(HttpSource.class))).thenReturn(result);
		return MetricsHubExtensionForJawk.builder().hostname("host").sourceProcessor(sourceProcessor).build();
	}

	/**
	 * A Jawk engine with the MetricsHub, utility and gawk extensions, as a connector script runs them.
	 *
	 * @param sourceProcessor The source processor of the <code>execute*</code> functions
	 * @return the engine
	 */
	private static Awk engine(final SourceProcessor sourceProcessor) {
		return AwkExecutor.newAwk(
			null,
			MetricsHubExtensionForJawk.builder().sourceProcessor(sourceProcessor).hostname("host").build()
		);
	}

	/**
	 * A source processor that answers every source with the text <code>response</code>.
	 *
	 * @return the mocked source processor
	 */
	private static SourceProcessor sourceProcessor() {
		final SourceProcessor sourceProcessor = mock(SourceProcessor.class);
		final SourceTable response = SourceTable.ofText("response");
		when(sourceProcessor.process(any(HttpSource.class))).thenReturn(response);
		when(sourceProcessor.process(any(IpmiSource.class))).thenReturn(response);
		when(sourceProcessor.process(any(SnmpGetSource.class))).thenReturn(response);
		when(sourceProcessor.process(any(SnmpTableSource.class))).thenReturn(response);
		when(sourceProcessor.process(any(WbemSource.class))).thenReturn(response);
		when(sourceProcessor.process(any(WmiSource.class))).thenReturn(response);
		return sourceProcessor;
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
		assertEquals("200\na;b 11 2\n1 0 [] 1\n", run("""
			BEGIN {
				args["method"] = "get"
				args["path"] = "/api"
				response["old"] = "old"
				n = executeHttpJsonRequest(args, response)
				print n
				print response["items"][0]["name"], response["items"][1]["value"] + 1, length(response["items"])
				print response["ok"], response["off"], "[" response["none"] "]", response["ratio"] * 2
			}
			""", httpGiving(answer(200, JSON))));
	}

	@Test
	void testExecuteHttpJsonRequestOnAnArray() throws Exception {
		assertEquals(
			"200 x 3\n",
			run(
				"BEGIN { args[\"path\"] = \"/api\"; n = executeHttpJsonRequest(args, response); print n, response[0], response[1][\"a\"] }",
				httpGiving(answer(200, "[\"x\", {\"a\": 3}]"))
			)
		);
	}

	/**
	 * A number is an AWK number: a 64-bit integer keeps all its digits, anything else is a double.
	 */
	@Test
	void testExecuteHttpJsonRequestNumbers() throws Exception {
		assertEquals("9223372036854775807 42 3 1\n", run("""
			BEGIN {
				args["path"] = "/api"
				executeHttpJsonRequest(args, r)
				print r["long"], r["small"], r["half"] * 2, (r["huge"] > r["long"])
			}
			""", httpGiving(answer(200, "{\"long\":9223372036854775807,\"small\":42,\"half\":1.5,\"huge\":1e30}"))));
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
		assertEquals("200 0\n", run(script, httpGiving(answer(200, "<html>"))));
		assertEquals("200 0\n", run(script, httpGiving(answer(200, "42"))));
		// A document followed by something else is not a JSON document
		assertEquals("200 0\n", run(script, httpGiving(answer(200, "{\"status\":\"ok\"}<html>proxy error</html>"))));
		assertEquals("200 1\n", run(script, httpGiving(answer(200, "{\"status\":\"ok\"} \n"))));
		assertEquals("200 0\n", run(script, httpGiving(answer(200, ""))));
		assertEquals("0 0\n", run(script, httpGiving(SourceTable.empty())));
	}

	@Test
	void testExecuteHttpJsonRequestReturnsTheStatus() throws Exception {
		final String script = "BEGIN { args[\"path\"] = \"/api\"; args[\"resultContent\"] = \"body\"; " +
			"print executeHttpJsonRequest(args, response), response[\"error\"] }";

		// A refused request is not an empty answer: the status says so, and the body says why
		assertEquals("401 bad token\n", run(script, httpGiving(answer(401, "{\"error\":\"bad token\"}"))));
		assertEquals("204 \n", run(script, httpGiving(answer(204, ""))));
		assertEquals("200 \n", run(script, httpGiving(SourceTable.ofText("Status: 200\n\n{}"))));
		assertEquals("404 \n", run(script, httpGiving(SourceTable.ofText("Status: 404"))));

		// The request asks for the status and the response, whatever resultContent the script gave
		final SourceProcessor sourceProcessor = mock(SourceProcessor.class);
		when(sourceProcessor.process(any(HttpSource.class))).thenReturn(answer(200, "{}"));
		run(script, MetricsHubExtensionForJawk.builder().hostname("host").sourceProcessor(sourceProcessor).build());
		final ArgumentCaptor<HttpSource> source = ArgumentCaptor.forClass(HttpSource.class);
		verify(sourceProcessor).process(source.capture());
		assertEquals(ResultContent.ALL_WITH_STATUS, source.getValue().getResultContent());
	}

	@Test
	void testJson2csvIsGone() {
		assertThrows(Exception.class, () -> run("BEGIN { args[\"jsonSource\"] = json; print json2csv(args) }"));
	}

	/**
	 * A script copies fields (numeric strings) and a cell of a variable table into the arguments of
	 * <code>executeHttpRequest</code>, without <code>sprintf("%s", ...)</code>: the source gets their text, a field
	 * that looks like a number included (#1579, #1586).
	 */
	@Test
	void testExecuteHttpRequestReadsFieldsAndVariableCells() throws Exception {
		final SourceProcessor sourceProcessor = sourceProcessor();
		final String script = """
			{
				request["method"] = "get"
				request["path"] = $1
				request["header"] = $2
				request["body"] = $3
				request["authenticationToken"] = token[0][0]
				request["resultContent"] = labels["content"]
				print executeHttpRequest(request)
			}
			""";

		assertEquals(
			"response\n",
			AwkExecutor.executeAwk(
				script,
				"/redfish/v1/Systems/1 042 1e3\n",
				engine(sourceProcessor),
				Map.of("token", List.of(List.of("abc123")), "labels", Map.of("content", "body"))
			)
		);

		final ArgumentCaptor<HttpSource> source = ArgumentCaptor.forClass(HttpSource.class);
		verify(sourceProcessor).process(source.capture());
		assertEquals("/redfish/v1/Systems/1", source.getValue().getPath());
		assertEquals("042", source.getValue().getHeader(), "A numeric field keeps its text");
		assertEquals("1e3", source.getValue().getBody(), "A numeric field keeps its text");
		assertEquals("abc123", source.getValue().getAuthenticationToken());
		assertEquals("body", source.getValue().getResultContent().name().toLowerCase());
	}

	/**
	 * Every <code>execute*</code> function registers with Jawk and builds its source from the AWK array, numeric
	 * strings included. <code>executeIpmiRequest</code> also runs without argument.
	 */
	@Test
	void testEveryExecuteFunctionReadsTheAwkArray() throws Exception {
		final SourceProcessor sourceProcessor = sourceProcessor();
		final String script = """
			{
				get["oid"] = $1
				table["oid"] = $1
				table["selectColumns"] = $2
				wbem["query"] = $3
				wbem["namespace"] = "root/cimv2"
				wmi["query"] = $3
				wmi["namespace"] = "root\\\\cimv2"
				ipmi["type"] = "ipmi"
				printf "%s %s %s %s %s %s\\n", executeSnmpGet(get), executeSnmpTable(table), executeWbemRequest(wbem), \
					executeWmiRequest(wmi), executeIpmiRequest(ipmi), executeIpmiRequest()
			}
			""";

		assertEquals(
			"response response response response response response\n",
			AwkExecutor.executeAwk(
				script,
				"1.3.6.1.2.1.1.5.0 1,2,3 SELECT_Name_FROM_Win32_Fan\n",
				engine(sourceProcessor),
				null
			)
		);

		final ArgumentCaptor<SnmpGetSource> get = ArgumentCaptor.forClass(SnmpGetSource.class);
		verify(sourceProcessor).process(get.capture());
		assertEquals("1.3.6.1.2.1.1.5.0", get.getValue().getOid());
		final ArgumentCaptor<SnmpTableSource> table = ArgumentCaptor.forClass(SnmpTableSource.class);
		verify(sourceProcessor).process(table.capture());
		assertEquals("1,2,3", table.getValue().getSelectColumns());
		final ArgumentCaptor<WbemSource> wbem = ArgumentCaptor.forClass(WbemSource.class);
		verify(sourceProcessor).process(wbem.capture());
		assertEquals("SELECT_Name_FROM_Win32_Fan", wbem.getValue().getQuery());
		final ArgumentCaptor<WmiSource> wmi = ArgumentCaptor.forClass(WmiSource.class);
		verify(sourceProcessor).process(wmi.capture());
		assertEquals("root\\cimv2", wmi.getValue().getNamespace());
		verify(sourceProcessor, Mockito.times(2)).process(any(IpmiSource.class));
	}

	/**
	 * <code>asorti</code> reads the keys of the source array and fills the script's destination array: it is the
	 * script's own array after the call.
	 */
	@Test
	void testAsortiFillsTheScriptArray() throws Exception {
		final String script = """
			BEGIN {
				src["b"] = 1
				src["a"] = 2
				src[10] = 3
				src[9] = 4
				dest["stale"] = "x"
				n = asorti(src, dest)
				printf "%d", n
				for (i = 1; i <= n; i++) printf " %s", dest[i]
				printf " %d\\n", ("stale" in dest)
			}
			""";

		assertEquals("4 10 9 a b 0\n", AwkExecutor.executeAwk(script, "", engine(sourceProcessor()), null));
	}

	/**
	 * The plain view leaves a missing argument missing: an <code>execute*</code> function reads it as
	 * <code>null</code>, not as a key it created.
	 */
	@Test
	void testMissingArgumentStaysMissing() throws Exception {
		final SourceProcessor sourceProcessor = sourceProcessor();
		final String script = """
			BEGIN {
				request["path"] = "/api"
				executeHttpRequest(request)
				print length(request), ("header" in request)
			}
			""";

		assertEquals("1 0\n", AwkExecutor.executeAwk(script, "", engine(sourceProcessor), null));

		final ArgumentCaptor<HttpSource> source = ArgumentCaptor.forClass(HttpSource.class);
		verify(sourceProcessor).process(source.capture());
		assertNull(source.getValue().getHeader());
	}

	/**
	 * <code>asorti(a, a)</code> sorts the array in place.
	 */
	@Test
	void testAsortiInPlace() throws Exception {
		final String script = """
			BEGIN {
				h["b"] = 1
				h["a"] = 2
				n = asorti(h, h)
				print n, h[1], h[2]
			}
			""";

		assertEquals("2 a b\n", AwkExecutor.executeAwk(script, "", engine(sourceProcessor()), null));
	}
}
