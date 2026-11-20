package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.FILE_PATTERN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jawk.Awk;
import io.jawk.jrt.AssocArray;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.MacrosUpdater;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.extension.HttpResult;
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
	 * The response to a HTTP request: its status, a header, the body.
	 */
	private static HttpResult answer(final int status, final String body) {
		return new HttpResult(status, "Content-Type: application/json\n", body);
	}

	private static MetricsHubExtensionForJawk httpGiving(final HttpResult result) {
		final SourceProcessor sourceProcessor = mock(SourceProcessor.class);
		when(sourceProcessor.executeHttpRequest(any(HttpSource.class))).thenReturn(result);
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
	 * A source processor that answers every source with the text <code>response</code>, but an <code>snmpGet</code>
	 * with the table it returns: the value <code>v;w</code> first, no row after that.
	 *
	 * @return the mocked source processor
	 */
	private static SourceProcessor sourceProcessor() {
		final SourceProcessor sourceProcessor = mock(SourceProcessor.class);
		final SourceTable response = SourceTable.ofText("response");
		when(sourceProcessor.process(any(HttpSource.class))).thenReturn(response);
		when(sourceProcessor.process(any(IpmiSource.class))).thenReturn(response);
		when(sourceProcessor.process(any(SnmpGetSource.class))).thenReturn(
			SourceTable.ofTable(List.of(List.of("v;w"))),
			SourceTable.empty()
		);
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

	/**
	 * A property of the parent of an entry, which the events parser reads too.
	 */
	@Test
	void testJson2TableParentProperty() throws Exception {
		assertEquals("2 g1 x g1 y\n", run("""
			BEGIN {
				doc = "{\\"groups\\":[{\\"id\\":\\"g1\\",\\"members\\":[{\\"name\\":\\"x\\"},{\\"name\\":\\"y\\"}]}]}"
				n = json2Table(doc, "/groups/members", "../id;name", rows)
				print n, rows[0][1], rows[0][2], rows[1][1], rows[1][2]
			}
			"""));
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

	/**
	 * A number that is not an integer prints with the digits of the document, where a double would print with 6
	 * significant digits, and still computes and compares as a number. The result of a computation is a double.
	 */
	@Test
	void testExecuteHttpJsonRequestDecimals() throws Exception {
		assertEquals(
			"1234567.891 12.50 1234567.891;x 2.46914e+06 1 1 1E+3\n",
			run(
				"""
					BEGIN {
						args["path"] = "/api"
						executeHttpJsonRequest(args, r)
						print r["energy"], r["volts"], r["energy"] ";x", r["energy"] * 2, (r["volts"] == 12.5), (r["volts"] < 100), r["list"][0]
					}
					""",
				httpGiving(answer(200, "{\"energy\":1234567.891,\"volts\":12.50,\"list\":[1e3]}"))
			)
		);
	}

	/**
	 * A null is in its array, without a value: it reads as an empty string, and as an empty array where the script
	 * expects an object.
	 */
	@Test
	void testExecuteHttpJsonRequestNull() throws Exception {
		assertEquals(
			"1 0 [] [] 2 [] b\n",
			run(
				"""
					BEGIN {
						args["path"] = "/api"
						executeHttpJsonRequest(args, r)
						print ("none" in r), length(r["members"][0]["Status"]), "[" r["members"][0]["Status"]["Health"] "]", "[" r["none"] "]", length(r["list"]), "[" r["list"][0] "]", r["list"][1]
					}
					""",
				httpGiving(answer(200, "{\"none\":null,\"members\":[{\"Status\":null}],\"list\":[null,\"b\"]}"))
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

	/**
	 * The HTTP sources a script requested, in order.
	 */
	private static List<HttpSource> requested(final SourceProcessor sourceProcessor, final int times) {
		final ArgumentCaptor<HttpSource> sources = ArgumentCaptor.forClass(HttpSource.class);
		verify(sourceProcessor, Mockito.times(times)).executeHttpRequest(sources.capture());
		return sources.getAllValues();
	}

	/**
	 * A body given as an array is written as JSON: an array indexed from 0 is an array, any other an object with its
	 * properties in alphabetical order, a number a number, a boolean read from a response a boolean, and anything
	 * else a string, a numeric string of the input included. The array of the script keeps its body.
	 */
	@Test
	void testExecuteHttpJsonRequestWithAnArrayBody() throws Exception {
		final SourceProcessor sourceProcessor = mock(SourceProcessor.class);
		when(sourceProcessor.executeHttpRequest(any(HttpSource.class))).thenReturn(answer(200, "{\"flag\":true}"));
		final MetricsHubExtensionForJawk extension = MetricsHubExtensionForJawk.builder()
			.hostname("host")
			.sourceProcessor(sourceProcessor)
			.build();

		assertEquals("200 11 Reads\n", run("""
			BEGIN {
				args["method"] = "post"
				args["path"] = "/api"
				args["header"] = "Accept: application/json"
				executeHttpJsonRequest(args, response)

				split("001 7", fields, " ")
				args["body"]["id"] = fields[1]
				args["body"]["count"] = fields[2] + 0
				args["body"]["ratio"] = 1 / 4
				args["body"]["total"] = 6 / 2
				args["body"]["name"] = "a \\"quoted\\" \\\\ name"
				args["body"]["enabled"] = response["flag"]
				args["body"]["missing"] = response["none"]
				args["body"]["metrics"][0] = "Reads"
				args["body"]["metrics"][1] = "Writes"
				args["body"]["range"]["first"] = 1
				args["body"]["parts"][1] = "from 1"
				args["body"]["empty"]["x"] = 1
				delete args["body"]["empty"]["x"]
				print executeHttpJsonRequest(args, response), length(args["body"]), args["body"]["metrics"][0]
			}
			""", extension));

		final List<HttpSource> sources = requested(sourceProcessor, 2);
		// Without a body, the request is the one of the script
		assertNull(sources.get(0).getBody());
		assertEquals("Accept: application/json", sources.get(0).getHeader());
		assertEquals(
			"{\"count\":7,\"empty\":{},\"enabled\":true,\"id\":\"001\",\"metrics\":[\"Reads\",\"Writes\"],\"missing\":\"\"," +
				"\"name\":\"a \\\"quoted\\\" \\\\ name\",\"parts\":{\"1\":\"from 1\"},\"range\":{\"first\":1},\"ratio\":0.25,\"total\":3}",
			sources.get(1).getBody()
		);
		assertEquals("Accept: application/json\nContent-Type: application/json", sources.get(1).getHeader());
	}

	/**
	 * The content type of a body given as an array is JSON unless the header names one. A body given as a string is
	 * sent as it is, with the header of the script.
	 */
	@Test
	void testExecuteHttpJsonRequestBodyContentType() throws Exception {
		final SourceProcessor sourceProcessor = mock(SourceProcessor.class);
		final MetricsHubExtensionForJawk extension = MetricsHubExtensionForJawk.builder()
			.hostname("host")
			.sourceProcessor(sourceProcessor)
			.build();

		run("""
			BEGIN {
				array["body"][0] = "a"
				executeHttpJsonRequest(array, response)

				array["header"] = "Accept: */*\\ncontent-type: application/vnd.api+json"
				executeHttpJsonRequest(array, response)

				text["body"] = "{\\"a\\": 1}"
				executeHttpJsonRequest(text, response)
			}
			""", extension);

		final List<HttpSource> sources = requested(sourceProcessor, 3);
		assertEquals("[\"a\"]", sources.get(0).getBody());
		assertEquals("Content-Type: application/json", sources.get(0).getHeader());
		assertEquals("[\"a\"]", sources.get(1).getBody());
		assertEquals("Accept: */*\ncontent-type: application/vnd.api+json", sources.get(1).getHeader());
		assertEquals("{\"a\": 1}", sources.get(2).getBody());
		assertNull(sources.get(2).getHeader());
	}

	/**
	 * What the request does afterwards does not undo a body given as an array. A macro in a value is escaped as JSON
	 * when the request replaces it, unless it names its escape. A value that reads as a reference to an embedded file
	 * is the text of the value, and does not replace the body. A header is a text, such as the text of a file that a
	 * variable gives: it gets the content type when it names none, and no embedded file is read.
	 */
	@Test
	void testExecuteHttpJsonRequestBodyMacrosAndEmbeddedFiles() throws Exception {
		final SourceProcessor sourceProcessor = mock(SourceProcessor.class);
		final MetricsHubExtensionForJawk extension = MetricsHubExtensionForJawk.builder()
			.hostname("host")
			.connectorId("connector")
			.sourceProcessor(sourceProcessor)
			.build();

		run("""
			BEGIN {
				array["header"] = "Accept: application/json\\n"
				array["body"]["user"] = "%{USERNAME}"
				array["body"]["password"] = "${PASSWORD}"
				array["body"]["token"] = "${esc(url)::AUTHENTICATIONTOKEN}"
				array["body"]["file"] = "${file::2}"
				executeHttpJsonRequest(array, response)

				array["header"] = "content-type: text/json\\n"
				executeHttpJsonRequest(array, response)
			}
			""", extension);

		final List<HttpSource> sources = requested(sourceProcessor, 2);
		final String body = sources.get(0).getBody();
		assertEquals(
			"{\"file\":\"\\u0024{file::2}\",\"password\":\"${esc(json)::PASSWORD}\"," +
				"\"token\":\"${esc(url)::AUTHENTICATIONTOKEN}\",\"user\":\"%{esc(json)::USERNAME}\"}",
			body
		);
		assertEquals("Accept: application/json\nContent-Type: application/json", sources.get(0).getHeader());
		assertEquals("content-type: text/json\n", sources.get(1).getHeader());
		verify(sourceProcessor, Mockito.never()).getTelemetryManager();

		// The request: the body is not an embedded file, and is still a document once the macros are replaced
		assertFalse(FILE_PATTERN.matcher(body).find());
		assertEquals(
			Map.of("file", "${file::2}", "password", "c\\d\ne", "token", "tk", "user", "a\"b"),
			JsonHelper.buildObjectMapper()
				.readValue(MacrosUpdater.update(body, "a\"b", "c\\d\ne".toCharArray(), "tk", "host", false), Map.class)
		);
	}

	/**
	 * <code>executeHttpRequest()</code> takes a body given as an array too, in a script run as a connector runs it: a
	 * field that looks like a number is a string, and a number once 0 is added to it; <code>mkbool()</code> gives a
	 * boolean.
	 */
	@Test
	void testExecuteHttpRequestWithAnArrayBody() throws Exception {
		final SourceProcessor sourceProcessor = sourceProcessor();
		final String script = """
			{
				args["body"]["id"] = $1
				args["body"]["start"] = $2 + 0
				args["body"]["user"] = $3
				args["body"]["average"] = mkbool(1)
				args["body"]["raw"] = mkbool(0)
				print executeHttpRequest(args)
			}
			""";

		assertEquals(
			"response\n",
			AwkExecutor.executeAwk(script, "000123 1700000000 a\"b\\c\n", engine(sourceProcessor), null)
		);

		final ArgumentCaptor<HttpSource> source = ArgumentCaptor.forClass(HttpSource.class);
		verify(sourceProcessor).process(source.capture());
		assertEquals(
			"{\"average\":true,\"id\":\"000123\",\"raw\":false,\"start\":1700000000,\"user\":\"a\\\"b\\\\c\"}",
			source.getValue().getBody()
		);
		assertEquals("Content-Type: application/json", source.getValue().getHeader());
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
		assertEquals("0 0\n", run(script, httpGiving(null)));
	}

	@Test
	void testExecuteHttpJsonRequestReturnsTheStatus() throws Exception {
		final String script = "BEGIN { args[\"path\"] = \"/api\"; args[\"resultContent\"] = \"body\"; " +
			"print executeHttpJsonRequest(args, response), response[\"error\"] }";

		// A refused request is not an empty answer: the status says so, and the body says why
		assertEquals("401 bad token\n", run(script, httpGiving(answer(401, "{\"error\":\"bad token\"}"))));
		assertEquals("204 \n", run(script, httpGiving(answer(204, ""))));
		assertEquals("200 \n", run(script, httpGiving(answer(200, "{}"))));
		assertEquals("404 \n", run(script, httpGiving(answer(404, null))));
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
	 * strings included. <code>executeIpmiRequest</code> also runs without argument, and <code>executeSnmpGet</code>
	 * returns the value as it is, an empty string when there is none.
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
				printf "[%s]\\n", executeSnmpGet(get)
			}
			""";

		assertEquals(
			"v;w response response response response response\n[]\n",
			AwkExecutor.executeAwk(
				script,
				"1.3.6.1.2.1.1.5.0 1,2,3 SELECT_Name_FROM_Win32_Fan\n",
				engine(sourceProcessor),
				null
			)
		);

		final ArgumentCaptor<SnmpGetSource> get = ArgumentCaptor.forClass(SnmpGetSource.class);
		verify(sourceProcessor, Mockito.times(2)).process(get.capture());
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
	 * A boolean-typed number (<code>mkbool()</code>, a Java Boolean handed to the script) reaches an
	 * <code>execute*</code> function as its number: <code>1</code> or <code>0</code>.
	 */
	@Test
	void testBooleanArgumentIsItsNumber() throws Exception {
		final SourceProcessor sourceProcessor = sourceProcessor();
		final String script = """
			BEGIN {
				request["path"] = "/api"
				request["header"] = mkbool(1)
				request["body"] = mkbool(0)
				print executeHttpRequest(request), (request["header"] ? "on" : "off")
			}
			""";

		assertEquals("response on\n", AwkExecutor.executeAwk(script, "", engine(sourceProcessor), null));

		final ArgumentCaptor<HttpSource> source = ArgumentCaptor.forClass(HttpSource.class);
		verify(sourceProcessor).process(source.capture());
		assertEquals("1", source.getValue().getHeader());
		assertEquals("0", source.getValue().getBody());
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
