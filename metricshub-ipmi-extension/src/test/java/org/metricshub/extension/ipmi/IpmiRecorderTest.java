package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.helpers.JsonHelper;

/**
 * Tests for {@link IpmiRecorder}.
 */
class IpmiRecorderTest {

	@AfterEach
	void tearDown() {
		IpmiRecorder.clearInstances();
	}

	@Test
	void testRecordDetection(@TempDir final Path tempDir) throws Exception {
		final IpmiRecorder recorder = IpmiRecorder.getInstance(tempDir.toString());

		recorder.record(IpmiRecorder.IPMI_DETECTION_REQUEST, "System power state is up.");
		recorder.flush();

		final Path ipmiDir = tempDir.resolve("ipmi");
		assertTrue(Files.isDirectory(ipmiDir));
		assertTrue(Files.isRegularFile(ipmiDir.resolve("image.yaml")));

		final String indexContent = Files.readString(ipmiDir.resolve("image.yaml"), StandardCharsets.UTF_8);
		assertTrue(indexContent.contains("IpmiDetection"));

		// Verify response file content
		final var yamlMapper = JsonHelper.buildYamlMapper();
		@SuppressWarnings("unchecked")
		final Map<String, List<Map<String, String>>> image = yamlMapper.readValue(
			ipmiDir.resolve("image.yaml").toFile(),
			Map.class
		);
		final String responseFileName = image.get("image").get(0).get("response");
		final String responseContent = Files.readString(ipmiDir.resolve(responseFileName), StandardCharsets.UTF_8);
		assertEquals("System power state is up.", responseContent);
	}

	@Test
	void testRecordGetSensors(@TempDir final Path tempDir) throws Exception {
		final IpmiRecorder recorder = IpmiRecorder.getInstance(tempDir.toString());

		recorder.record(IpmiRecorder.GET_SENSORS_REQUEST, "FRU data here");
		recorder.flush();

		final Path ipmiDir = tempDir.resolve("ipmi");
		final String indexContent = Files.readString(ipmiDir.resolve("image.yaml"), StandardCharsets.UTF_8);
		assertTrue(indexContent.contains("GetSensors"));
	}

	@Test
	void testRecordMultipleEntries(@TempDir final Path tempDir) throws Exception {
		final IpmiRecorder recorder = IpmiRecorder.getInstance(tempDir.toString());

		recorder.record(IpmiRecorder.IPMI_DETECTION_REQUEST, "detection result 1");
		recorder.record(IpmiRecorder.GET_SENSORS_REQUEST, "sensors result 1");
		recorder.record(IpmiRecorder.IPMI_DETECTION_REQUEST, "detection result 2");
		recorder.flush();

		final Path ipmiDir = tempDir.resolve("ipmi");
		final var yamlMapper = JsonHelper.buildYamlMapper();
		@SuppressWarnings("unchecked")
		final Map<String, List<Map<String, String>>> image = yamlMapper.readValue(
			ipmiDir.resolve("image.yaml").toFile(),
			Map.class
		);

		assertEquals(3, image.get("image").size());
		assertEquals("IpmiDetection", image.get("image").get(0).get("request"));
		assertEquals("GetSensors", image.get("image").get(1).get("request"));
		assertEquals("IpmiDetection", image.get("image").get(2).get("request"));
	}

	@Test
	void testGetInstanceReturnsSameInstance(@TempDir final Path tempDir) {
		final IpmiRecorder recorder1 = IpmiRecorder.getInstance(tempDir.toString());
		final IpmiRecorder recorder2 = IpmiRecorder.getInstance(tempDir.toString());
		assertEquals(recorder1, recorder2);
	}

	@Test
	void testRecordNullResponse(@TempDir final Path tempDir) throws Exception {
		final IpmiRecorder recorder = IpmiRecorder.getInstance(tempDir.toString());

		recorder.record(IpmiRecorder.IPMI_DETECTION_REQUEST, null);
		recorder.flush();

		final Path ipmiDir = tempDir.resolve("ipmi");
		final var yamlMapper = JsonHelper.buildYamlMapper();
		@SuppressWarnings("unchecked")
		final Map<String, List<Map<String, String>>> image = yamlMapper.readValue(
			ipmiDir.resolve("image.yaml").toFile(),
			Map.class
		);
		final String responseFileName = image.get("image").get(0).get("response");
		final String responseContent = Files.readString(ipmiDir.resolve(responseFileName), StandardCharsets.UTF_8);
		assertEquals("", responseContent);
	}
}
