package org.metricshub.engine.connector.deserializer.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Xml2Csv;

class Json2CsvComputeDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/compute/json2Csv/";
	}

	@Test
	void testDeserializeCompute() throws IOException {
		final Connector connector = getConnector("json2Csv");

		final List<Compute> computes = new ArrayList<>();
		computes.add(
			Json2Csv.builder().type("json2Csv").properties(List.of("property", "test", "here")).entryKey("/").build()
		);
		computes.add(Json2Csv.builder().type("json2Csv").properties(List.of("name")).entryKey("/items").build());
		// json2Table is the name, json2Csv its deprecated alias: same class, same fields, only the written type differs
		final Json2Csv json2Table = (Json2Csv) computes.get(1).copy();
		json2Table.setType("json2Table");
		computes.add(json2Table);
		// An empty item is kept
		computes.add(Json2Csv.builder().type("json2Table").properties(List.of("property", "", "here")).build());
		// An item is one property, whatever it holds
		computes.add(Json2Csv.builder().type("json2Table").properties(List.of("a;b", "1")).build());

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testCompute",
				HttpSource.builder()
					.key("${source::beforeAll.testCompute}")
					.type("http")
					.url("/testUrl/")
					.computes(computes)
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());

		// parsingMode and separator are only recorded for the content-type check
		final List<Json2Csv> actual = connector.getBeforeAll()
			.get("testCompute")
			.getComputes()
			.stream()
			.map(Json2Csv.class::cast)
			.toList();
		assertEquals(
			List.of(false, true, true, false, false),
			actual.stream().map(Json2Csv::isParsingModeDeclared).toList()
		);
		assertEquals(
			List.of(false, false, false, false, true),
			actual.stream().map(Json2Csv::isSeparatorDeclared).toList()
		);
	}

	/**
	 * The properties of a json2Table and of an xml2Csv are a list: an empty list is a compute without a property, and a
	 * connector whose properties are a string, even an empty one, or hold a null or a nested item does not load.
	 */
	@Test
	void testPropertiesAreAList() throws IOException {
		for (final String type : List.of("json2Table", "xml2Csv")) {
			final Compute compute = deserialize(type, "[]").getBeforeAll().get("testCompute").getComputes().get(0);
			assertEquals(
				List.of(),
				compute instanceof Json2Csv json2Table ? json2Table.getProperties() : ((Xml2Csv) compute).getProperties()
			);
			for (final String properties : List.of("a;b", "\"\"", "[ a, ~ ]", "[ a, [ b ] ]")) {
				assertThrows(JsonMappingException.class, () -> deserialize(type, properties), type + " " + properties);
			}
		}
	}

	/**
	 * Deserializes a connector whose only source holds one compute.
	 *
	 * @param type       The type of the compute
	 * @param properties The properties of the compute, as written in YAML
	 * @return The connector
	 * @throws IOException When the connector does not load
	 */
	private static Connector deserialize(final String type, final String properties) throws IOException {
		final ObjectMapper mapper = JsonHelper.buildYamlMapper();
		return new ConnectorDeserializer(mapper).deserialize(mapper.readTree("""
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  testCompute:
			    type: http
			    url: /testUrl/
			    computes:
			    - type: %s
			      properties: %s
			""".formatted(type, properties)));
	}

	@Test
	void testUpdateEachProperty() {
		final Json2Csv origin = Json2Csv.builder().properties(List.of("id", "", "name")).build();
		final Json2Csv copy = origin.copy();
		copy.update(value -> value + "!");
		assertEquals(List.of("id!", "!", "name!"), copy.getProperties());
		// The copy has its own list
		assertEquals(List.of("id", "", "name"), origin.getProperties());

		// A reference is resolved inside its item: its value is never split
		final Json2Csv reference = Json2Csv.builder().properties(List.of("id", "${reference}")).build();
		reference.update(value -> value.replace("${reference}", "a;b"));
		assertEquals(List.of("id", "a;b"), reference.getProperties());

		// No properties: nothing to update
		final Json2Csv none = Json2Csv.builder().build();
		none.update(value -> value + "!");
		assertNull(none.getProperties());
	}
}
