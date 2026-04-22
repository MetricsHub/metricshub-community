package org.metricshub.extension.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.helpers.JsonHelper;

class JmxRecorderTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void cleanup() {
		JmxRecorder.clearInstances();
	}

	@Test
	void testRecordSingleEntry() throws Exception {
		final JmxRecorder recorder = JmxRecorder.getInstance(tempDir.toString());

		recorder.record("java.lang:type=Memory", List.of("HeapMemoryUsage"), List.of(), "\"val1\";\"val2\"");

		final Path jmxDir = tempDir.resolve("jmx");
		assertTrue(Files.isDirectory(jmxDir));

		final Path indexFile = jmxDir.resolve("image.yaml");
		assertTrue(Files.isRegularFile(indexFile));

		final JsonNode root = readImageYaml(indexFile);
		final JsonNode entries = root.get("image");
		assertNotNull(entries);
		assertEquals(1, entries.size());

		final JsonNode request = entries.get(0).get("request");
		assertEquals("java.lang:type=Memory", request.get("objectName").asText());
		assertEquals("HeapMemoryUsage", request.get("attributes").get(0).asText());
		assertEquals(0, request.get("keyProperties").size());

		final String responseFile = entries.get(0).get("response").asText();
		assertNotNull(responseFile);
		assertTrue(Files.isRegularFile(jmxDir.resolve(responseFile)));
	}

	@Test
	void testRecordMultipleEntries() throws Exception {
		final JmxRecorder recorder = JmxRecorder.getInstance(tempDir.toString());

		recorder.record("obj1", List.of("attr1"), List.of("key1"), "\"a\"");
		recorder.record("obj2", List.of("attr2"), List.of(), "\"b\"");

		final JsonNode root = readImageYaml(tempDir.resolve("jmx").resolve("image.yaml"));
		assertEquals(2, root.get("image").size());
	}

	@Test
	void testSingleton() {
		final JmxRecorder r1 = JmxRecorder.getInstance(tempDir.toString());
		final JmxRecorder r2 = JmxRecorder.getInstance(tempDir.toString());
		assertSame(r1, r2);
	}

	@Test
	void testRecordWithKeyProperties() throws Exception {
		final JmxRecorder recorder = JmxRecorder.getInstance(tempDir.toString());

		recorder.record(
			"com.example:type=Example,scope=*,name=*",
			List.of("Attribute1"),
			List.of("scope", "name"),
			"\"scope1\";\"name1\";\"100\""
		);

		final JsonNode root = readImageYaml(tempDir.resolve("jmx").resolve("image.yaml"));
		final JsonNode request = root.get("image").get(0).get("request");
		final JsonNode keyProperties = request.get("keyProperties");
		assertEquals(2, keyProperties.size());
		assertEquals("scope", keyProperties.get(0).asText());
		assertEquals("name", keyProperties.get(1).asText());
	}

	@Test
	void testRecordEmptyResponse() throws Exception {
		final JmxRecorder recorder = JmxRecorder.getInstance(tempDir.toString());
		recorder.record("obj", List.of("attr"), List.of(), "");

		final Path jmxDir = tempDir.resolve("jmx");
		final Path indexFile = jmxDir.resolve("image.yaml");
		assertTrue(Files.isRegularFile(indexFile));

		final JsonNode root = readImageYaml(indexFile);
		final String responseFile = root.get("image").get(0).get("response").asText();
		final String content = Files.readString(jmxDir.resolve(responseFile));
		assertTrue(content.isEmpty());
	}

	private JsonNode readImageYaml(final Path indexFile) throws Exception {
		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		return yamlMapper.readTree(indexFile.toFile());
	}
}
