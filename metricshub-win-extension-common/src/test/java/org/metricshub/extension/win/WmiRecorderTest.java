package org.metricshub.extension.win;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.helpers.JsonHelper;

/**
 * Tests for {@link WmiRecorder}.
 */
class WmiRecorderTest {

	@AfterEach
	void tearDown() {
		WmiRecorder.clearInstances();
	}

	@Test
	void testRecordCreatesImageYaml(@TempDir final Path tempDir) throws IOException {
		final WmiRecorder recorder = WmiRecorder.getInstance(tempDir.toString());

		recorder.record("SELECT Name FROM Win32_ComputerSystem", "root\\cimv2", List.of(List.of("MyComputer")));

		final Path wmiDir = tempDir.resolve(WmiRecorder.WMI_SUBDIR);
		assertTrue(Files.isDirectory(wmiDir));

		final Path indexFile = wmiDir.resolve(WmiRecorder.IMAGE_YAML);
		assertTrue(Files.isRegularFile(indexFile));

		final JsonNode root = JsonHelper.buildYamlMapper().readTree(indexFile.toFile());
		final JsonNode image = root.get("image");
		assertNotNull(image);
		assertEquals(1, image.size());

		final JsonNode entry = image.get(0);
		assertEquals("SELECT Name FROM Win32_ComputerSystem", entry.get("request").get("wql").asText());
		assertEquals("root\\cimv2", entry.get("request").get("namespace").asText());

		final String responseFileName = entry.get("response").asText();
		assertTrue(responseFileName.endsWith(".csv"));

		final String responseContent = Files.readString(wmiDir.resolve(responseFileName), StandardCharsets.UTF_8);
		assertTrue(responseContent.contains("MyComputer"));
	}

	@Test
	void testRecordAppendsToExistingImage(@TempDir final Path tempDir) throws IOException {
		final WmiRecorder recorder = WmiRecorder.getInstance(tempDir.toString());

		recorder.record("SELECT Name FROM Win32_Process", "root\\cimv2", List.of(List.of("proc1")));
		recorder.record("SELECT Name FROM Win32_Service", "root\\cimv2", List.of(List.of("svc1")));

		final Path indexFile = tempDir.resolve(WmiRecorder.WMI_SUBDIR).resolve(WmiRecorder.IMAGE_YAML);
		final JsonNode root = JsonHelper.buildYamlMapper().readTree(indexFile.toFile());
		final JsonNode image = root.get("image");

		assertEquals(2, image.size());
		assertEquals("SELECT Name FROM Win32_Process", image.get(0).get("request").get("wql").asText());
		assertEquals("SELECT Name FROM Win32_Service", image.get(1).get("request").get("wql").asText());
	}

	@Test
	void testGetInstanceReturnsSameInstance() {
		final WmiRecorder first = WmiRecorder.getInstance("/tmp/dir1");
		final WmiRecorder second = WmiRecorder.getInstance("/tmp/dir1");

		assertEquals(first, second);
	}

	@Test
	void testRecordDuplicateQueries(@TempDir final Path tempDir) throws IOException {
		final WmiRecorder recorder = WmiRecorder.getInstance(tempDir.toString());

		recorder.record("SELECT Name FROM Win32_Process", "root\\cimv2", List.of(List.of("proc1")));
		recorder.record("SELECT Name FROM Win32_Process", "root\\cimv2", List.of(List.of("proc2")));

		final Path indexFile = tempDir.resolve(WmiRecorder.WMI_SUBDIR).resolve(WmiRecorder.IMAGE_YAML);
		final JsonNode root = JsonHelper.buildYamlMapper().readTree(indexFile.toFile());
		final JsonNode image = root.get("image");

		assertEquals(2, image.size());

		final String file1 = image.get(0).get("response").asText();
		final String file2 = image.get(1).get("response").asText();
		assertTrue(!file1.equals(file2), "Each recording should produce a unique file");
	}
}
