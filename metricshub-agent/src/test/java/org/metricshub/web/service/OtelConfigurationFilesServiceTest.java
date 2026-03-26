package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.exception.ConfigFilesException;

class OtelConfigurationFilesServiceTest {

	@TempDir
	Path tempOtelDir;

	private OtelConfigurationFilesService newService() {
		return new OtelConfigurationFilesService(tempOtelDir);
	}

	@Test
	void testShouldListYamlFilesSorted() throws Exception {
		Files.writeString(tempOtelDir.resolve("a.yml"), "k: v");
		Files.writeString(tempOtelDir.resolve("B.yaml"), "say: hello");
		Files.writeString(tempOtelDir.resolve("ignore.txt"), "nope");

		final OtelConfigurationFilesService service = newService();
		final List<ConfigurationFile> files = service.getAllConfigurationFiles();

		assertEquals(2, files.size());
		assertEquals("a.yml", files.get(0).getName());
		assertEquals("B.yaml", files.get(1).getName());
		assertNotNull(files.get(0).getLastModificationTime());
	}

	@Test
	void testGetFileContentOk() throws Exception {
		final OtelConfigurationFilesService service = newService();
		Files.writeString(tempOtelDir.resolve("otel-config.yaml"), "receivers:\n  otlp:", StandardCharsets.UTF_8);

		String content = service.getFileContent("otel-config.yaml");
		assertEquals("receivers:\n  otlp:", content);
	}

	@Test
	void testGetFileContentNotFound() {
		final OtelConfigurationFilesService service = newService();

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.getFileContent("missing.yaml"));
		assertEquals(ConfigFilesException.Code.FILE_NOT_FOUND, ex.getCode());
	}

	@Test
	void testGetFileContentInvalidNameRejectTraversal() {
		final OtelConfigurationFilesService service = newService();

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.getFileContent("../evil.yaml"));
		assertEquals(ConfigFilesException.Code.INVALID_FILE_NAME, ex.getCode());
	}

	@Test
	void testGetFileContentInvalidExtension() {
		final OtelConfigurationFilesService service = newService();

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.getFileContent("config.txt"));
		assertEquals(ConfigFilesException.Code.INVALID_EXTENSION, ex.getCode());
	}

	@Test
	void testSaveOrUpdateCreatesAndOverwrites() throws Exception {
		final OtelConfigurationFilesService service = newService();
		final Path path = tempOtelDir.resolve("app.yaml");

		service.saveOrUpdateFile("app.yaml", "receivers: {}");
		assertTrue(Files.exists(path));
		assertEquals("receivers: {}", Files.readString(path, StandardCharsets.UTF_8));

		service.saveOrUpdateFile("app.yaml", "receivers:\n  otlp:\n    endpoint: 0.0.0.0:4317");
		assertTrue(Files.readString(path, StandardCharsets.UTF_8).contains("0.0.0.0:4317"), "Content should be updated");
	}

	@Test
	void testDeleteFileExisting() throws Exception {
		final OtelConfigurationFilesService service = newService();
		Files.writeString(tempOtelDir.resolve("to-delete.yml"), "x: y");

		service.deleteFile("to-delete.yml");
		assertFalse(Files.exists(tempOtelDir.resolve("to-delete.yml")));
	}

	@Test
	void testDeleteFileMissing() {
		final OtelConfigurationFilesService service = newService();

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.deleteFile("nope.yml"));
		assertEquals(ConfigFilesException.Code.FILE_NOT_FOUND, ex.getCode());
	}

	@Test
	void testRenameFileOk() throws Exception {
		final OtelConfigurationFilesService service = newService();
		Files.writeString(tempOtelDir.resolve("old.yaml"), "name: old");

		service.renameFile("old.yaml", "new.yaml");

		assertFalse(Files.exists(tempOtelDir.resolve("old.yaml")));
		assertTrue(Files.exists(tempOtelDir.resolve("new.yaml")));
		assertEquals("name: old", Files.readString(tempOtelDir.resolve("new.yaml"), StandardCharsets.UTF_8));
	}

	@Test
	void testRenameFileTargetExists() throws Exception {
		final OtelConfigurationFilesService service = newService();
		Files.writeString(tempOtelDir.resolve("a.yaml"), "x: 1");
		Files.writeString(tempOtelDir.resolve("b.yaml"), "x: 2");

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.renameFile("a.yaml", "b.yaml"));
		assertEquals(ConfigFilesException.Code.TARGET_EXISTS, ex.getCode());
	}

	@Test
	void testSaveDraftFile() throws Exception {
		final OtelConfigurationFilesService service = newService();

		service.saveOrUpdateDraftFile("otel-config.yaml", "receivers: {}");
		assertTrue(Files.exists(tempOtelDir.resolve("otel-config.yaml.draft")));
		assertEquals(
			"receivers: {}",
			Files.readString(tempOtelDir.resolve("otel-config.yaml.draft"), StandardCharsets.UTF_8)
		);
	}

	@Test
	void testListIncludesDrafts() throws Exception {
		Files.writeString(tempOtelDir.resolve("config.yaml.draft"), "key: value");

		final OtelConfigurationFilesService service = newService();
		final List<ConfigurationFile> files = service.getAllConfigurationFiles();

		assertEquals(1, files.size());
		assertEquals("config.yaml.draft", files.get(0).getName());
	}

	@Test
	void testValidateValidYaml() throws Exception {
		final OtelConfigurationFilesService service = newService();
		String yaml = "receivers:\n  otlp:\n    endpoint: 0.0.0.0:4317";

		OtelConfigurationFilesService.Validation result = service.validate(yaml, "test.yaml");

		assertNotNull(result);
		assertTrue(result.isValid());
		assertTrue(result.getErrors() == null || result.getErrors().isEmpty());
	}

	@Test
	void testValidateInvalidYaml() throws Exception {
		final OtelConfigurationFilesService service = newService();
		// Invalid YAML: unclosed quote and bad structure
		String yaml = "receivers:\n  otlp:\n    endpoint: \"unclosed";

		OtelConfigurationFilesService.Validation result = service.validate(yaml, "test.yaml");

		assertNotNull(result);
		assertFalse(result.isValid());
		assertNotNull(result.getErrors());
		assertFalse(result.getErrors().isEmpty());
	}

	@Test
	void testBackupListGetSaveDelete() throws Exception {
		final OtelConfigurationFilesService service = newService();
		Path backupDir = tempOtelDir.resolve("backup");
		Files.createDirectories(backupDir);
		Files.writeString(backupDir.resolve("backup-20250101-120000__otel-config.yaml"), "receivers: {}");

		List<ConfigurationFile> backups = service.listAllBackupFiles();
		assertEquals(1, backups.size());
		assertTrue(backups.get(0).getName().contains("otel-config"));

		String content = service.getBackupFileContent(backups.get(0).getName());
		assertEquals("receivers: {}", content);

		service.saveOrUpdateBackupFile("backup-20250102-120000__other.yaml", "key: value");
		backups = service.listAllBackupFiles();
		assertEquals(2, backups.size());

		service.deleteBackupFile(backups.get(0).getName());
		backups = service.listAllBackupFiles();
		assertEquals(1, backups.size());
	}
}
