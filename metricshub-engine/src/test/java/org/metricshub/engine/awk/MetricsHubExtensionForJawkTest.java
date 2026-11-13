package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class MetricsHubExtensionForJawkTest {

	private static final String JSON = "{\"items\":[{\"name\":\"a\",\"value\":1},{\"name\":\"b\",\"value\":2}]}";
	private static final String CSV = "/items[0];a;1;\n/items[1];b;2;";

	/**
	 * A Jawk engine with the MetricsHub and utility extensions, as a connector script runs them.
	 *
	 * @param sourceProcessor The source processor of the <code>execute*</code> functions
	 * @return the engine
	 */
	private static Awk engine(final SourceProcessor sourceProcessor) {
		return new Awk(
			List.of(
				MetricsHubExtensionForJawk.builder().sourceProcessor(sourceProcessor).hostname("host").build(),
				UtilityExtensionForJawk.INSTANCE
			),
			AwkExecutor.newAwkSettings(null)
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
	void testExecuteJson2csvParsingMode() {
		final MetricsHubExtensionForJawk extension = MetricsHubExtensionForJawk.builder().hostname("host").build();
		final AssocArray args = AssocArray.createHash();
		args.put("jsonSource", JSON);
		args.put("entryKey", "/items");
		args.put("properties", "name;value");
		args.put("separator", ";");

		try (
			MockedStatic<ClientsExecutor> clientsExecutor = Mockito.mockStatic(
				ClientsExecutor.class,
				Mockito.CALLS_REAL_METHODS
			)
		) {
			// Without parsingMode, the JSON is parsed as a tree
			assertEquals(CSV, extension.executeJson2csv(args));
			clientsExecutor.verify(
				() -> ClientsExecutor.executeJson2Csv(JSON, "/items", List.of("name", "value"), ";", false, "host")
			);

			// parsingMode events (case-insensitive) gives the same CSV
			args.put("parsingMode", "Events");
			assertEquals(CSV, extension.executeJson2csv(args));
			clientsExecutor.verify(
				() -> ClientsExecutor.executeJson2Csv(JSON, "/items", List.of("name", "value"), ";", true, "host")
			);
		}
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
	 * <code>json2csv</code> reads its arguments from fields and from a variable map.
	 */
	@Test
	void testJson2csvReadsFieldsAndVariables() throws Exception {
		final String script = """
			{
				args["jsonSource"] = json
				args["entryKey"] = $1
				args["properties"] = $2
				args["separator"] = ";"
				print json2csv(args)
			}
			""";

		assertEquals(
			CSV + "\n",
			AwkExecutor.executeAwk(script, "/items name;value\n", engine(sourceProcessor()), Map.of("json", JSON))
		);
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

	/**
	 * A <code>json2csv</code> argument the script does not set is the empty string, not the text <code>null</code>.
	 */
	@Test
	void testJson2csvMissingArgumentIsEmpty() throws Exception {
		final String script = """
			{
				args["jsonSource"] = json
				args["entryKey"] = "/items"
				args["properties"] = "name;value"
				print json2csv(args)
			}
			""";

		try (
			MockedStatic<ClientsExecutor> clientsExecutor = Mockito.mockStatic(
				ClientsExecutor.class,
				Mockito.CALLS_REAL_METHODS
			)
		) {
			AwkExecutor.executeAwk(script, "x\n", engine(sourceProcessor()), Map.of("json", JSON));
			clientsExecutor.verify(
				() -> ClientsExecutor.executeJson2Csv(JSON, "/items", List.of("name", "value"), "", false, "host")
			);
		}
	}
}
