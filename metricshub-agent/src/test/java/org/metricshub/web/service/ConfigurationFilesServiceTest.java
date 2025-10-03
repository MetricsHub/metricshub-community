package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.exception.ConfigFilesException;
import org.mockito.Mockito;

class ConfigurationFilesServiceTest {

	@TempDir
	Path tempConfigDir;

	/**
	 * Create a ConfigurationFilesService with a mocked AgentContextHolder that returns the given dir.
	 * @param dir the config dir to return from the mocked AgentContext
	 * @return the service instance
	 */
	private ConfigurationFilesService newServiceWithDir(Path dir) {
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		final AgentContext agentContext = Mockito.mock(AgentContext.class);
		when(holder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getConfigDirectory()).thenReturn(dir);
		return new ConfigurationFilesService(holder);
	}

	/**
	 * Create a ConfigurationFilesService with a mocked AgentContextHolder that returns null context.
	 * @return the service instance
	 */
	private ConfigurationFilesService newServiceWithNoContext() {
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		when(holder.getAgentContext()).thenReturn(null);
		return new ConfigurationFilesService(holder);
	}

	@Test
	void testShouldListYamlFilesAtDepthOneSorted() throws Exception {
		final Path aYml = tempConfigDir.resolve("a.yml");
		final Path bYaml = tempConfigDir.resolve("B.yaml");
		final Path notYaml = tempConfigDir.resolve("ignore.txt");
		final Path subDir = tempConfigDir.resolve("sub");
		final Path deepYaml = subDir.resolve("deep.yaml");

		Files.writeString(aYml, "k: v");
		Files.writeString(bYaml, "say: hello");
		Files.writeString(notYaml, "nope");
		Files.createDirectories(subDir);
		Files.writeString(deepYaml, "too: deep");

		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		final List<ConfigurationFile> files = service.getAllConfigurationFiles();

		assertEquals(2, files.size(), "Only .yml/.yaml at depth 1 should be listed");

		final ConfigurationFile first = files.get(0);
		final ConfigurationFile second = files.get(1);

		assertEquals("a.yml", first.getName(), "First file should be a.yml");
		assertEquals("B.yaml", second.getName(), "Second file should be B.yaml");

		assertEquals(Files.size(aYml), first.getSize(), "Size of a.yml should match");
		assertEquals(Files.size(bYaml), second.getSize(), "Size of B.yaml should match");

		assertNotNull(first.getLastModificationTime(), "lastModificationTime must not be null");
		assertNotNull(second.getLastModificationTime(), "lastModificationTime must not be null");
	}

	@Test
	void testGetAllConfigurationFilesServiceUnavailable() {
		final ConfigurationFilesService service = newServiceWithNoContext();

		ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			service::getAllConfigurationFiles,
			"Service should throw when no AgentContext"
		);
		assertEquals(ConfigFilesException.Code.CONFIG_DIR_UNAVAILABLE, ex.getCode(), "Error code should match");
	}

	@Test
	void testGetFileContentOk() throws Exception {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);
		final Path file = tempConfigDir.resolve("metricshub.yaml");
		Files.writeString(file, "hello: world", StandardCharsets.UTF_8);

		String content = service.getFileContent("metricshub.yaml");
		assertEquals("hello: world", content, "File content should match");
	}

	@Test
	void testGetFileContentNotFound() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			() -> service.getFileContent("missing.yaml"),
			"Service should throw when file is missing"
		);
		assertEquals(ConfigFilesException.Code.FILE_NOT_FOUND, ex.getCode(), "Error code should match");
		assertTrue(ex.getMessage().toLowerCase().contains("not found"), "Message should indicate not found");
	}

	@Test
	void testGetFileContentInvalidNameRejectTraversal() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			() -> service.getFileContent("../evil.yaml"),
			"Service should throw on path traversal attempt"
		);
		// The service rejects traversal early as INVALID_FILE_NAME
		assertEquals(ConfigFilesException.Code.INVALID_FILE_NAME, ex.getCode(), "Error code should match");
	}

	@Test
	void testGetFileContentInvalidExtension() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			() -> service.getFileContent("config.txt"),
			"Service should throw on invalid file extension"
		);
		assertEquals(ConfigFilesException.Code.INVALID_EXTENSION, ex.getCode(), "Error code should match");
		assertTrue(
			ex.getMessage().contains(".yml") || ex.getMessage().contains(".yaml"),
			"Message should indicate valid extensions"
		);
	}

	@Test
	void testSaveOrUpdateCreatesAndOverwrites() throws Exception {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);
		final Path path = tempConfigDir.resolve("app.yaml");

		// create
		service.saveOrUpdateFile("app.yaml", "a: 1");
		assertTrue(Files.exists(path), "File should be created");
		assertEquals("a: 1", Files.readString(path, StandardCharsets.UTF_8), "File content should match");

		// overwrite
		service.saveOrUpdateFile("app.yaml", "a: 2\nb: 3");
		assertEquals("a: 2\nb: 3", Files.readString(path, StandardCharsets.UTF_8), "File content should be updated");
	}

	@Test
	void testDeleteFileExisting() throws Exception {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);
		final Path path = tempConfigDir.resolve("to-delete.yml");
		Files.writeString(path, "x: y");

		service.deleteFile("to-delete.yml");
		assertFalse(Files.exists(path), "File should be deleted");
	}

	@Test
	void testDeleteFileMissing() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			() -> service.deleteFile("nope.yml"),
			"Service should throw when deleting missing file"
		);
		assertEquals(ConfigFilesException.Code.FILE_NOT_FOUND, ex.getCode(), "Error code should match");
	}

	@Test
	void testRenameFileOk() throws Exception {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);
		final Path src = tempConfigDir.resolve("old.yaml");
		final Path dst = tempConfigDir.resolve("new.yaml");
		Files.writeString(src, "name: old");

		service.renameFile("old.yaml", "new.yaml");

		assertFalse(Files.exists(src), "Source file should be gone");
		assertTrue(Files.exists(dst), "Target file should exist");
		assertEquals("name: old", Files.readString(dst, StandardCharsets.UTF_8), "Target file content should match source");
	}

	@Test
	void testRenameFileSourceMissing() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			() -> service.renameFile("absent.yaml", "new.yaml"),
			"Service should throw when source file is missing"
		);
		assertEquals(ConfigFilesException.Code.FILE_NOT_FOUND, ex.getCode(), "Error code should match");
	}

	@Test
	void testRenameFileTargetExists() throws Exception {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);
		final Path src = tempConfigDir.resolve("a.yaml");
		final Path dst = tempConfigDir.resolve("b.yaml");
		Files.writeString(src, "x: 1");
		Files.writeString(dst, "x: 2");

		ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			() -> service.renameFile("a.yaml", "b.yaml"),
			"Service should throw when target file exists"
		);
		assertEquals(ConfigFilesException.Code.TARGET_EXISTS, ex.getCode(), "Error code should match");
	}

	@Test
	void testRequireConfigDirUnavailable() {
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		when(holder.getAgentContext()).thenReturn(null);
		final ConfigurationFilesService service = new ConfigurationFilesService(holder);

		ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			() -> service.getFileContent("x.yaml"),
			"Service should throw when no AgentContext"
		);
		assertEquals(ConfigFilesException.Code.CONFIG_DIR_UNAVAILABLE, ex.getCode(), "Error code should match");
	}
}
