package org.metricshub.extension.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.helpers.JsonHelper;

class JdbcRecorderTest {

	@AfterEach
	void tearDown() {
		JdbcRecorder.clearInstances();
	}

	@Test
	void testRecordAndFlushCreatesImageYaml(@TempDir final Path tempDir) throws Exception {
		final JdbcRecorder recorder = JdbcRecorder.getInstance(tempDir.toString());

		recorder.record("SELECT 1", List.of(List.of("1")));
		recorder.flush();

		final Path jdbcDir = tempDir.resolve(JdbcRecorder.JDBC_SUBDIR);
		final Path indexFile = jdbcDir.resolve(JdbcRecorder.IMAGE_YAML);
		assertTrue(Files.isRegularFile(indexFile));

		final JsonNode root = JsonHelper.buildYamlMapper().readTree(indexFile.toFile());
		final JsonNode image = root.get("image");
		assertNotNull(image);
		assertEquals(1, image.size());
		assertEquals("SELECT 1", image.get(0).get("query").asText());

		final String responseFileName = image.get(0).get("response").asText();
		final String responseContent = Files.readString(jdbcDir.resolve(responseFileName), StandardCharsets.UTF_8);
		assertTrue(responseContent.contains("1"));
	}

	@Test
	void testRecordMultipleEntriesFlushedOnce(@TempDir final Path tempDir) throws Exception {
		final JdbcRecorder recorder = JdbcRecorder.getInstance(tempDir.toString());

		recorder.record("SELECT 1", List.of(List.of("1")));
		recorder.record("SELECT 2", List.of(List.of("2")));
		recorder.flush();

		final Path indexFile = tempDir.resolve(JdbcRecorder.JDBC_SUBDIR).resolve(JdbcRecorder.IMAGE_YAML);
		final JsonNode root = JsonHelper.buildYamlMapper().readTree(indexFile.toFile());
		final JsonNode image = root.get("image");
		assertEquals(2, image.size());
		assertEquals("SELECT 1", image.get(0).get("query").asText());
		assertEquals("SELECT 2", image.get(1).get("query").asText());
	}

	@Test
	void testFlushWithoutEntriesDoesNothing(@TempDir final Path tempDir) {
		final JdbcRecorder recorder = JdbcRecorder.getInstance(tempDir.toString());
		recorder.flush();
		assertFalse(Files.exists(tempDir.resolve(JdbcRecorder.JDBC_SUBDIR).resolve(JdbcRecorder.IMAGE_YAML)));
	}

	@Test
	void testFlushAndRemoveInstance(@TempDir final Path tempDir) {
		final String outputDir = tempDir.toString();
		final JdbcRecorder recorder = JdbcRecorder.getInstance(outputDir);
		recorder.record("SELECT 1", List.of(List.of("1")));

		JdbcRecorder.flushAndRemoveInstance(outputDir);

		assertTrue(Files.isRegularFile(tempDir.resolve(JdbcRecorder.JDBC_SUBDIR).resolve(JdbcRecorder.IMAGE_YAML)));
		final JdbcRecorder newRecorder = JdbcRecorder.getInstance(outputDir);
		assertFalse(recorder == newRecorder);
	}
}
