package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jawk.jrt.AssocArray;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
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
			clientsExecutor.verify(() ->
				ClientsExecutor.executeJson2Csv(JSON, "/items", List.of("name", "value"), ";", false, "host")
			);

			// parsingMode events (case-insensitive) gives the same CSV
			args.put("parsingMode", "Events");
			assertEquals(CSV, extension.executeJson2csv(args));
			clientsExecutor.verify(() ->
				ClientsExecutor.executeJson2Csv(JSON, "/items", List.of("name", "value"), ";", true, "host")
			);
		}
	}
}
