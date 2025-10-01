package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
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

	// ---------- helpers ----------

	private ConfigurationFilesService newServiceWithDir(Path dir) {
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		final AgentContext agentContext = Mockito.mock(AgentContext.class);
		when(holder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getConfigDirectory()).thenReturn(dir);
		return new ConfigurationFilesService(holder);
	}

	private ConfigurationFilesService newServiceWithNoContext() {
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		when(holder.getAgentContext()).thenReturn(null);
		return new ConfigurationFilesService(holder);
	}

	// ---------- tests ----------

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
	void testGetAllConfigurationFiles_serviceUnavailable() {
		final ConfigurationFilesService service = newServiceWithNoContext();

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, service::getAllConfigurationFiles);
		assertEquals(ConfigFilesException.Code.CONFIG_DIR_UNAVAILABLE, ex.getCode());
	}

	@Test
	void testGetFileContent_ok() throws Exception {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);
		final Path file = tempConfigDir.resolve("metricshub.yaml");
		Files.writeString(file, "hello: world", StandardCharsets.UTF_8);

		String content = service.getFileContent("metricshub.yaml");
		assertEquals("hello: world", content);
	}

	@Test
	void testGetFileContent_notFound() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.getFileContent("missing.yaml"));
		assertEquals(ConfigFilesException.Code.FILE_NOT_FOUND, ex.getCode());
		assertTrue(ex.getMessage().toLowerCase().contains("not found"));
	}

	@Test
	void testGetFileContent_invalidName_rejectTraversal() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.getFileContent("../evil.yaml"));
		// The service rejects traversal early as INVALID_FILE_NAME
		assertEquals(ConfigFilesException.Code.INVALID_FILE_NAME, ex.getCode());
	}

	@Test
	void testGetFileContent_invalidExtension() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.getFileContent("config.txt"));
		assertEquals(ConfigFilesException.Code.INVALID_EXTENSION, ex.getCode());
		assertTrue(ex.getMessage().contains(".yml") || ex.getMessage().contains(".yaml"));
	}

	@Test
	void testSaveOrUpdate_createsAndOverwrites() throws Exception {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);
		final Path path = tempConfigDir.resolve("app.yaml");

		// create
		service.saveOrUpdateFile("app.yaml", "a: 1");
		assertTrue(Files.exists(path));
		assertEquals("a: 1", Files.readString(path, StandardCharsets.UTF_8));

		// overwrite
		service.saveOrUpdateFile("app.yaml", "a: 2\nb: 3");
		assertEquals("a: 2\nb: 3", Files.readString(path, StandardCharsets.UTF_8));
	}

	@Test
	void testValidateFile_validAndInvalid() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		// valid
		ObjectNode ok = service.validateFile("good.yaml", "k: v\nlist:\n  - x\n  - y");
		assertTrue(ok.get("valid").asBoolean());
		assertTrue(ok.get("errors").isArray());
		assertEquals(0, ok.withArray("errors").size());

		// invalid
		ObjectNode bad = service.validateFile("bad.yaml", "k: : v\n list: [ 1, 2");
		assertFalse(bad.get("valid").asBoolean());
		var errors = bad.get("errors");
		assertTrue(errors.isArray());
		assertTrue(errors.size() >= 1);
		assertTrue(errors.get(0).asText().toLowerCase().contains("yaml"));
	}

	@Test
	void testDeleteFile_existing() throws Exception {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);
		final Path path = tempConfigDir.resolve("to-delete.yml");
		Files.writeString(path, "x: y");

		service.deleteFile("to-delete.yml");
		assertFalse(Files.exists(path));
	}

	@Test
	void testDeleteFile_missing() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.deleteFile("nope.yml"));
		assertEquals(ConfigFilesException.Code.FILE_NOT_FOUND, ex.getCode());
	}

	@Test
	void testRenameFile_ok() throws Exception {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);
		final Path src = tempConfigDir.resolve("old.yaml");
		final Path dst = tempConfigDir.resolve("new.yaml");
		Files.writeString(src, "name: old");

		service.renameFile("old.yaml", "new.yaml");

		assertFalse(Files.exists(src));
		assertTrue(Files.exists(dst));
		assertEquals("name: old", Files.readString(dst, StandardCharsets.UTF_8));
	}

	@Test
	void testRenameFile_sourceMissing() {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);

		ConfigFilesException ex = assertThrows(
			ConfigFilesException.class,
			() -> service.renameFile("absent.yaml", "new.yaml")
		);
		assertEquals(ConfigFilesException.Code.FILE_NOT_FOUND, ex.getCode());
	}

	@Test
	void testRenameFile_targetExists() throws Exception {
		final ConfigurationFilesService service = newServiceWithDir(tempConfigDir);
		final Path src = tempConfigDir.resolve("a.yaml");
		final Path dst = tempConfigDir.resolve("b.yaml");
		Files.writeString(src, "x: 1");
		Files.writeString(dst, "x: 2");

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.renameFile("a.yaml", "b.yaml"));
		assertEquals(ConfigFilesException.Code.TARGET_EXISTS, ex.getCode());
	}

	@Test
	void testRequireConfigDir_unavailable() {
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		when(holder.getAgentContext()).thenReturn(null);
		final ConfigurationFilesService service = new ConfigurationFilesService(holder);

		ConfigFilesException ex = assertThrows(ConfigFilesException.class, () -> service.getFileContent("x.yaml"));
		assertEquals(ConfigFilesException.Code.CONFIG_DIR_UNAVAILABLE, ex.getCode());
	}
}
