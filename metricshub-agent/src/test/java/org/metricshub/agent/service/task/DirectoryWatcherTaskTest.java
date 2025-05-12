package org.metricshub.agent.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.service.TestHelper;

class DirectoryWatcherTaskTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void setup() {
		TestHelper.configureGlobalLogger();
	}

	@Test
	void testDirectoryWatcherOnCreateEvent() {
		final StringBuilder result = new StringBuilder();

		final DirectoryWatcherTask watcher = DirectoryWatcherTask
			.builder()
			.directory(tempDir)
			.await(20)
			.filter(event -> {
				String fileName = event.context().toString().toLowerCase();
				return event.kind() == StandardWatchEventKinds.ENTRY_CREATE && fileName.endsWith(".yaml");
			})
			.checksumSupplier(() ->
				ConfigHelper.calculateDirectoryMD5ChecksumSafe(tempDir, path -> path.toString().endsWith(".yaml"))
			)
			.onChange(() -> result.append("File created"))
			.build();

		watcher.start();

		final Path newYamlFile = tempDir.resolve("testFile.yaml");
		Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> Files.createFile(newYamlFile).toFile().exists());

		Awaitility
			.await()
			.atMost(Durations.FIVE_SECONDS)
			.untilAsserted(() -> assertEquals("File created", result.toString(), "File creation event not detected"));
	}

	@Test
	void testDirectoryWatcherIgnoreNonYamlFiles() {
		final StringBuilder result = new StringBuilder();

		final DirectoryWatcherTask watcher = DirectoryWatcherTask
			.builder()
			.directory(tempDir)
			.filter(event -> {
				String fileName = event.context().toString().toLowerCase();
				return (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && fileName.endsWith(".yaml"));
			})
			.onChange(() -> result.append("File created"))
			.checksumSupplier(() ->
				ConfigHelper.calculateDirectoryMD5ChecksumSafe(tempDir, path -> path.toString().endsWith(".yaml"))
			)
			.build();

		watcher.start();

		final Path newTxtFile = tempDir.resolve("ignoredFile.txt");
		Awaitility.await().atMost(Durations.FIVE_SECONDS).until(() -> Files.createFile(newTxtFile).toFile().exists());

		Awaitility
			.await()
			.during(Durations.TWO_SECONDS)
			.untilAsserted(() -> assertEquals("", result.toString(), "File creation event detected for non-YAML file"));
	}
}
