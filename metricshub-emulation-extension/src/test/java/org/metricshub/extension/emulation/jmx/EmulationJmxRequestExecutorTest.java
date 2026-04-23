package org.metricshub.extension.emulation.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.emulation.JmxEmulationConfig;
import org.metricshub.extension.jmx.JmxConfiguration;

class EmulationJmxRequestExecutorTest {

	@TempDir
	Path tempDir;

	private EmulationJmxRequestExecutor createExecutor() {
		return new EmulationJmxRequestExecutor(new EmulationRoundRobinManager(), new EmulationImageCacheManager());
	}

	private void writeImageYaml(final Path dir, final String content) throws Exception {
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("image.yaml"), content, StandardCharsets.UTF_8);
	}

	private void writeResponseFile(final Path dir, final String fileName, final String content) throws Exception {
		Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
	}

	@Test
	void testFetchMBeanMatchingEntry() throws Exception {
		writeImageYaml(
			tempDir,
			"""
			image:
			- request:
			    objectName: "java.lang:type=Memory"
			    attributes:
			      - HeapMemoryUsage
			    keyProperties: []
			  response: resp1.txt
			"""
		);
		writeResponseFile(tempDir, "resp1.txt", "val1;val2;");

		final JmxEmulationConfig config = new JmxEmulationConfig(
			JmxConfiguration.builder().hostname("host1").build(),
			tempDir.toString()
		);

		final List<List<String>> result = createExecutor()
			.fetchMBean(config, "java.lang:type=Memory", List.of("HeapMemoryUsage"), List.of(), "host1", null);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(List.of("val1", "val2"), result.get(0));
	}

	@Test
	void testFetchMBeanWithKeyProperties() throws Exception {
		writeImageYaml(
			tempDir,
			"""
			image:
			- request:
			    objectName: "com.example:type=Ex,scope=*"
			    attributes:
			      - Attr1
			    keyProperties:
			      - scope
			  response: resp2.txt
			"""
		);
		writeResponseFile(tempDir, "resp2.txt", "scope1;100;");

		final JmxEmulationConfig config = new JmxEmulationConfig(
			JmxConfiguration.builder().hostname("host2").build(),
			tempDir.toString()
		);

		final List<List<String>> result = createExecutor()
			.fetchMBean(config, "com.example:type=Ex,scope=*", List.of("Attr1"), List.of("scope"), "host2", null);

		assertEquals(1, result.size());
		assertEquals(List.of("scope1", "100"), result.get(0));
	}

	@Test
	void testFetchMBeanNoMatch() throws Exception {
		writeImageYaml(
			tempDir,
			"""
			image:
			- request:
			    objectName: "other:type=Other"
			    attributes:
			      - Attr1
			    keyProperties: []
			  response: resp.txt
			"""
		);
		writeResponseFile(tempDir, "resp.txt", "val;");

		final JmxEmulationConfig config = new JmxEmulationConfig(
			JmxConfiguration.builder().hostname("host").build(),
			tempDir.toString()
		);

		final List<List<String>> result = createExecutor()
			.fetchMBean(config, "nonexistent:type=Missing", List.of("Attr1"), List.of(), "host", null);

		assertTrue(result.isEmpty());
	}

	@Test
	void testFetchMBeanRoundRobin() throws Exception {
		writeImageYaml(
			tempDir,
			"""
			image:
			- request:
			    objectName: "obj:type=T"
			    attributes:
			      - A
			    keyProperties: []
			  response: r1.txt
			- request:
			    objectName: "obj:type=T"
			    attributes:
			      - A
			    keyProperties: []
			  response: r2.txt
			"""
		);
		writeResponseFile(tempDir, "r1.txt", "first;");
		writeResponseFile(tempDir, "r2.txt", "second;");

		final JmxEmulationConfig config = new JmxEmulationConfig(
			JmxConfiguration.builder().hostname("host").build(),
			tempDir.toString()
		);

		final EmulationJmxRequestExecutor executor = createExecutor();
		final List<List<String>> result1 = executor.fetchMBean(config, "obj:type=T", List.of("A"), List.of(), "host", null);
		final List<List<String>> result2 = executor.fetchMBean(config, "obj:type=T", List.of("A"), List.of(), "host", null);

		assertEquals("first", result1.get(0).get(0));
		assertEquals("second", result2.get(0).get(0));
	}

	@Test
	void testFetchMBeanNullObjectName() throws Exception {
		final JmxEmulationConfig config = new JmxEmulationConfig(
			JmxConfiguration.builder().hostname("host").build(),
			tempDir.toString()
		);
		final List<List<String>> result = createExecutor().fetchMBean(config, null, List.of("A"), List.of(), "host", null);
		assertTrue(result.isEmpty());
	}

	@Test
	void testFetchMBeanNotEmulationConfig() throws Exception {
		final JmxConfiguration config = JmxConfiguration.builder().hostname("host").build();
		final List<List<String>> result = createExecutor()
			.fetchMBean(config, "obj:type=T", List.of("A"), List.of(), "host", null);
		assertTrue(result.isEmpty());
	}

	@Test
	void testFetchMBeanNullDirectory() throws Exception {
		final JmxEmulationConfig config = new JmxEmulationConfig(JmxConfiguration.builder().hostname("host").build(), null);
		final List<List<String>> result = createExecutor()
			.fetchMBean(config, "obj:type=T", List.of("A"), List.of(), "host", null);
		assertTrue(result.isEmpty());
	}

	@Test
	void testFetchMBeanMissingIndexFile() throws Exception {
		final JmxEmulationConfig config = new JmxEmulationConfig(
			JmxConfiguration.builder().hostname("host").build(),
			tempDir.toString()
		);
		final List<List<String>> result = createExecutor()
			.fetchMBean(config, "obj:type=T", List.of("A"), List.of(), "host", null);
		assertTrue(result.isEmpty());
	}

	@Test
	void testFetchMBeanMissingResponseFile() throws Exception {
		writeImageYaml(
			tempDir,
			"""
			image:
			- request:
			    objectName: "obj:type=T"
			    attributes:
			      - A
			    keyProperties: []
			  response: missing.txt
			"""
		);

		final JmxEmulationConfig config = new JmxEmulationConfig(
			JmxConfiguration.builder().hostname("host").build(),
			tempDir.toString()
		);

		final List<List<String>> result = createExecutor()
			.fetchMBean(config, "obj:type=T", List.of("A"), List.of(), "host", null);
		assertTrue(result.isEmpty());
	}

	@Test
	void testFetchMBeanBlankResponseFileName() throws Exception {
		writeImageYaml(
			tempDir,
			"""
			image:
			- request:
			    objectName: "obj:type=T"
			    attributes:
			      - A
			    keyProperties: []
			  response: ""
			"""
		);

		final JmxEmulationConfig config = new JmxEmulationConfig(
			JmxConfiguration.builder().hostname("host").build(),
			tempDir.toString()
		);

		final List<List<String>> result = createExecutor()
			.fetchMBean(config, "obj:type=T", List.of("A"), List.of(), "host", null);
		assertTrue(result.isEmpty());
	}

	@Test
	void testCheckConnectionReturnsTrue() throws Exception {
		assertTrue(createExecutor().checkConnection(null, null));
	}

	@Test
	void testFindMatchingEntriesNullEntries() {
		final List<JmxEmulationEntry> result = createExecutor().findMatchingEntries(null, "obj", List.of(), List.of());
		assertTrue(result.isEmpty());
	}

	@Test
	void testFindMatchingEntriesEmptyEntries() {
		final List<JmxEmulationEntry> result = createExecutor().findMatchingEntries(List.of(), "obj", List.of(), List.of());
		assertTrue(result.isEmpty());
	}

	@Test
	void testFindMatchingEntriesNullEntry() {
		final List<JmxEmulationEntry> result = createExecutor()
			.findMatchingEntries(List.of(new JmxEmulationEntry(null, "resp")), "obj", List.of(), List.of());
		assertTrue(result.isEmpty());
	}
}
