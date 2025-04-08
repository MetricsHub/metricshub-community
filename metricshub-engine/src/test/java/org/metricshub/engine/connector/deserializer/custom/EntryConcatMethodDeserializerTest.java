package org.metricshub.engine.connector.deserializer.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.engine.connector.model.common.EntryConcatMethod.JSON_ARRAY;
import static org.metricshub.engine.connector.model.common.EntryConcatMethod.JSON_ARRAY_EXTENDED;
import static org.metricshub.engine.connector.model.common.EntryConcatMethod.LIST;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.common.CustomConcatMethod;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;

class EntryConcatMethodDeserializerTest {

	private static final String SOURCE_REF = "${source::beforeAll.source1}";
	private static final String EXECUTE_FOR_EACH_ENTRY_OF_YAML =
		"""
		source: ${source::beforeAll.source1}
		concatMethod: ReplaceMe
		""";

	private static ObjectMapper mapper;

	private static final String REPLACE_ME = "ReplaceMe";

	@BeforeAll
	static void setUp() {
		mapper = JsonHelper.buildYamlMapper();
	}

	@Test
	void testCustomConcatMethodValue() throws IOException {
		final String yaml =
			"""

			  concatStart: "<tag>"
			  concatEnd: "</tag>"
			""";

		final ExecuteForEachEntryOf executeForEachEntry = JsonHelper.deserialize(
			mapper,
			new ByteArrayInputStream(EXECUTE_FOR_EACH_ENTRY_OF_YAML.replace(REPLACE_ME, yaml).getBytes()),
			ExecuteForEachEntryOf.class
		);
		assertEquals(
			ExecuteForEachEntryOf
				.builder()
				.source(SOURCE_REF)
				.concatMethod(new CustomConcatMethod("<tag>", "</tag>"))
				.build(),
			executeForEachEntry
		);
	}

	@Test
	void testConcatMethodValueNull() throws IOException {
		final ExecuteForEachEntryOf executeForEachEntry = JsonHelper.deserialize(
			mapper,
			new ByteArrayInputStream(EXECUTE_FOR_EACH_ENTRY_OF_YAML.replace(REPLACE_ME, "").getBytes()),
			ExecuteForEachEntryOf.class
		);
		assertEquals(ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(LIST).build(), executeForEachEntry);
	}

	@Test
	void testEntryConcatMethodJsonArray() throws IOException {
		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(JSON_ARRAY).build(),
			deserializeExecuteForEachEntryOf("JSONArray")
		);

		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(JSON_ARRAY).build(),
			deserializeExecuteForEachEntryOf("jsonArray")
		);

		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(JSON_ARRAY).build(),
			deserializeExecuteForEachEntryOf("json_array")
		);

		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(JSON_ARRAY).build(),
			deserializeExecuteForEachEntryOf("JSON_ARRAY")
		);
	}

	@Test
	void testEntryConcatMethodList() throws IOException {
		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(LIST).build(),
			deserializeExecuteForEachEntryOf("list")
		);

		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(LIST).build(),
			deserializeExecuteForEachEntryOf("List")
		);

		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(LIST).build(),
			deserializeExecuteForEachEntryOf("LIST")
		);
	}

	@Test
	void testEntryConcatMethodJsonArrayExtended() throws IOException {
		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(JSON_ARRAY_EXTENDED).build(),
			deserializeExecuteForEachEntryOf("jsonArrayExtended")
		);

		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(JSON_ARRAY_EXTENDED).build(),
			deserializeExecuteForEachEntryOf("JSONArrayExtended")
		);

		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(JSON_ARRAY_EXTENDED).build(),
			deserializeExecuteForEachEntryOf("json_array_extended")
		);

		assertEquals(
			ExecuteForEachEntryOf.builder().source(SOURCE_REF).concatMethod(JSON_ARRAY_EXTENDED).build(),
			deserializeExecuteForEachEntryOf("JSON_ARRAY_EXTENDED")
		);
	}

	@Test
	void testEntryConcatMethodUnknown() throws IOException {
		assertThrows(InvalidFormatException.class, () -> deserializeExecuteForEachEntryOf("Unknown"));
	}

	/**
	 * Deserialization of EXECUTE_FOR_EACH_ENTRY_OF_YAML into an {@link ExecuteForEachEntryOf} object
	 *
	 * @param replacement
	 * @return {@link ExecuteForEachEntryOf}
	 * @throws IOException
	 */
	private ExecuteForEachEntryOf deserializeExecuteForEachEntryOf(String replacement) throws IOException {
		final ExecuteForEachEntryOf executeForEachEntry = JsonHelper.deserialize(
			mapper,
			new ByteArrayInputStream(EXECUTE_FOR_EACH_ENTRY_OF_YAML.replace(REPLACE_ME, replacement).getBytes()),
			ExecuteForEachEntryOf.class
		);
		return executeForEachEntry;
	}
}
