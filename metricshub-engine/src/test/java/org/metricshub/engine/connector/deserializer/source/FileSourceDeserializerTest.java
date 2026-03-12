package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSourceProcessingMode;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

class FileSourceDeserializerTest extends DeserializerTest {

	private static final long ONE_MB = 1024L * 1024L;

	/**
	 * Normalizes a Windows-style path for comparison with deserialized YAML. Test resources
	 * use double backslashes (e.g. {@code C:\\temp\\*.log}); the parser yields that literal.
	 */
	private static String normalizePath(String path) {
		if (path.matches("^[A-Za-z]:.*")) {
			return path.replace("/", "\\\\");
		}
		return path;
	}

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/file/";
	}

	@Test
	void testDeserializeFileSource() throws IOException {
		final Connector connector = getConnector("file");

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testFileSource",
				FileSource
					.builder()
					.key("${source::beforeAll.testFileSource}")
					.type("file")
					.paths(
						new LinkedHashSet<>(List.of(normalizePath("C:/Program Files/MetricsHub/logs/*.log"), "/var/log/app/*.log"))
					)
					.maxSizePerPoll(100L * ONE_MB)
					.mode(FileSourceProcessingMode.LOG)
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeFileSourceWithNegativeMaxSizePerPoll() throws IOException {
		final Connector connector = getConnector("file2");

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testFileSource",
				FileSource
					.builder()
					.key("${source::beforeAll.testFileSource}")
					.type("file")
					.paths(new LinkedHashSet<>(List.of(normalizePath("C:/temp/*.log"))))
					.maxSizePerPoll(-1L)
					.mode(FileSourceProcessingMode.FLAT)
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeFileSourceWithoutMaxSizePerPollAndMode() throws IOException {
		final Connector connector = getConnector("file3");

		// file3 has no maxSizePerPoll nor mode: default 5 MB, default mode LOG
		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testFileSource",
				FileSource
					.builder()
					.key("${source::beforeAll.testFileSource}")
					.type("file")
					.paths(Set.of("/var/log/*.log"))
					.maxSizePerPoll(5L * ONE_MB)
					.mode(FileSourceProcessingMode.LOG)
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeFileSourceWithSizeStringMaxSizePerPoll() throws IOException {
		final Connector connector = getConnector("file4");

		// "1mb" deserializes to 1 * 1024 * 1024 bytes
		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testFileSource",
				FileSource
					.builder()
					.key("${source::beforeAll.testFileSource}")
					.type("file")
					.paths(Set.of("/var/log/*.log"))
					.maxSizePerPoll(ONE_MB)
					.mode(FileSourceProcessingMode.LOG)
					.build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}
}
