package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jawk.jrt.AssocArray;
import io.jawk.jrt.StrNum;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class MetricsHubExtensionForJawkTest {

	private static final String JSON = "{\"items\":[{\"name\":\"a\",\"value\":1},{\"name\":\"b\",\"value\":2}]}";
	private static final String CSV = "/items[0];a;1;\n/items[1];b;2;";

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
	 * Builds a Jawk numeric string, as Jawk hands over a field or a value of an awk variable list, map or table.
	 *
	 * @param value The text
	 * @return the numeric string
	 */
	private static StrNum strNum(final String value) throws ReflectiveOperationException {
		final Constructor<StrNum> constructor = StrNum.class.getDeclaredConstructor(String.class);
		constructor.setAccessible(true);
		return constructor.newInstance(value);
	}

	/**
	 * A script that copies a cell of a variable table (or a field) into the arguments of
	 * <code>executeHttpRequest</code> hands over Jawk numeric strings: the source reads their text (#1579).
	 */
	@Test
	void testExecuteHttpRequestReadsNumericStringsAsText() throws ReflectiveOperationException {
		final SourceProcessor sourceProcessor = mock(SourceProcessor.class);
		when(sourceProcessor.process(any(HttpSource.class))).thenReturn(SourceTable.ofText("response"));
		final MetricsHubExtensionForJawk extension = MetricsHubExtensionForJawk.builder()
			.sourceProcessor(sourceProcessor)
			.hostname("host")
			.build();

		final AssocArray compute = AssocArray.createHash();
		compute.put("type", "awk");
		compute.put("script", strNum("{ print $1 }"));
		final AssocArray args = AssocArray.createHash();
		args.put("method", "get");
		args.put("path", strNum("/redfish/v1/Systems/1"));
		args.put("authenticationToken", strNum("abc123"));
		args.put("header", strNum("42"));
		args.put("resultContent", "body");
		args.put("computes", List.of(compute));

		assertEquals("response", extension.executeHttpRequest(args));

		final ArgumentCaptor<HttpSource> source = ArgumentCaptor.forClass(HttpSource.class);
		verify(sourceProcessor).process(source.capture());
		assertEquals("/redfish/v1/Systems/1", source.getValue().getPath());
		assertEquals("abc123", source.getValue().getAuthenticationToken());
		assertEquals("42", source.getValue().getHeader());
		assertEquals("{ print $1 }", ((Awk) source.getValue().getComputes().get(0)).getScript());
	}

	/**
	 * Plain values stay as they are, maps and lists are copied with plain values.
	 */
	@Test
	void testPlainValue() throws ReflectiveOperationException {
		assertEquals("10", MetricsHubExtensionForJawk.plainValue(strNum("10")));
		assertEquals(2.5, MetricsHubExtensionForJawk.plainValue(2.5));
		assertEquals(true, MetricsHubExtensionForJawk.plainValue(true));
		assertEquals(null, MetricsHubExtensionForJawk.plainValue(null));
		assertEquals(
			Map.of("a", List.of("1", 2L), "b", Map.of("c", "d")),
			MetricsHubExtensionForJawk.plainValue(Map.of("a", List.of(strNum("1"), 2L), "b", Map.of("c", strNum("d"))))
		);
	}
}
