package org.metricshub.engine.connector.deserializer.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;

class Json2CsvComputeDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/compute/json2Csv/";
	}

	@Test
	void testDeserializeCompute() throws IOException {
		final Connector connector = getConnector("json2Csv");

		final List<Compute> computes = new ArrayList<>();
		computes.add(Json2Csv.builder().type("json2Csv").properties("property;test;here").entryKey("/").build());
		computes.add(Json2Csv.builder().type("json2Csv").properties("name").entryKey("/items").build());
		// json2Table is the name, json2Csv its deprecated alias: same class, same fields, only the written type differs
		final Json2Csv json2Table = (Json2Csv) computes.get(1).copy();
		json2Table.setType("json2Table");
		computes.add(json2Table);
		// The string form and the list form give the same compute: an empty item is kept, one trailing ; is dropped
		final Json2Csv list = Json2Csv.builder().type("json2Table").properties(List.of("property", "", "here")).build();
		computes.add(Json2Csv.builder().type("json2Table").properties("property;;here;").build());
		computes.add(list);
		assertEquals(List.of("property", "", "here"), list.getProperties());
		assertEquals(computes.get(3), list);
		// An item of the list form is one property, whatever it holds
		final Json2Csv verbatim = Json2Csv.builder().type("json2Table").properties(List.of("a;b", "1")).build();
		computes.add(verbatim);
		assertEquals(List.of("a;b", "1"), verbatim.getProperties());

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

		// parsingMode is ignored, only recorded for the content-type check
		assertEquals(
			List.of(false, true, true, false, false, false),
			connector.getBeforeAll()
				.get("testCompute")
				.getComputes()
				.stream()
				.map(compute -> ((Json2Csv) compute).isParsingModeDeclared())
				.toList()
		);
	}

	/**
	 * An empty list is accepted, as the empty string is: both are a compute without a property.
	 */
	@Test
	void testEmptyProperties() throws IOException {
		final ObjectMapper mapper = JsonHelper.buildYamlMapper();
		for (final String type : List.of("json2Table", "xml2Csv")) {
			assertEquals(
				mapper.readValue("type: " + type + "\nproperties: \"\"", Compute.class),
				mapper.readValue("type: " + type + "\nproperties: []", Compute.class)
			);
		}
		assertEquals(List.of(), Json2Csv.builder().properties(List.of()).build().getProperties());
	}

	@Test
	void testUpdateEachProperty() {
		final Json2Csv origin = Json2Csv.builder().properties(List.of("id", "", "name")).build();
		final Json2Csv copy = origin.copy();
		copy.update(value -> value + "!");
		assertEquals(List.of("id!", "!", "name!"), copy.getProperties());
		// The copy has its own list
		assertEquals(List.of("id", "", "name"), origin.getProperties());

		// A reference is resolved inside its item: its value is never split, also in the string form
		final Json2Csv reference = Json2Csv.builder().properties("id;${reference}").build();
		reference.update(value -> value.replace("${reference}", "a;b"));
		assertEquals(List.of("id", "a;b"), reference.getProperties());

		// No properties: nothing to update
		final Json2Csv none = Json2Csv.builder().build();
		none.update(value -> value + "!");
		assertNull(none.getProperties());
	}
}
