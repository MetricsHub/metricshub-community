package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.ConfigurationFile;
import org.mockito.Mockito;

class ConfigurationFilesServiceTest {

	@TempDir
	Path tempConfigDir;

	@Test
	void testShouldListYamlFilesAtDepthOneSorted() throws Exception {
		// Create files in the temp config directory
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

		// Mock AgentContextHolder -> AgentContext -> getConfigDirectory()
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		final AgentContext agentContext = Mockito.mock(AgentContext.class);
		when(holder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getConfigDirectory()).thenReturn(tempConfigDir);

		final ConfigurationFilesService service = new ConfigurationFilesService(holder);

		// Get the configuration YAML files
		final List<ConfigurationFile> files = service.getAllConfigurationFiles();

		// Assert
		assertEquals(2, files.size(), "Only .yml/.yaml at depth 1 should be listed");

		// Sorted case-insensitively by name -> "a.yml" then "B.yaml"
		final ConfigurationFile first = files.get(0);
		final ConfigurationFile second = files.get(1);

		assertEquals("a.yml", first.getName(), "First file should be a.yml");
		assertEquals("B.yaml", second.getName(), "Second file should be B.yaml");

		// Sizes should match content length we wrote above
		assertEquals(Files.size(aYml), first.getSize(), "Size of a.yml should match");
		assertEquals(Files.size(bYaml), second.getSize(), "Size of B.yaml should match");

		// lastModificationTime should be populated (ISO-8601 string from FileTime#toString)
		assertNotNull(first.getLastModificationTime(), "lastModificationTime must not be null");
		assertNotNull(second.getLastModificationTime(), "lastModificationTime must not be null");
	}

	@Test
	void testShouldReturnEmptyListWhenNoAgentContext() {
		// AgentContextHolder returns null -> service should return empty list
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		when(holder.getAgentContext()).thenReturn(null);

		final ConfigurationFilesService service = new ConfigurationFilesService(holder);

		final List<ConfigurationFile> files = service.getAllConfigurationFiles();
		assertEquals(0, files.size(), "Should return empty list when AgentContext is null");
	}
}
