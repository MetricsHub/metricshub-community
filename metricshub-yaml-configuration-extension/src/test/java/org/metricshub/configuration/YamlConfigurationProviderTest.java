package org.metricshub.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;

class YamlConfigurationProviderTest {

	private final YamlConfigurationProvider provider = new YamlConfigurationProvider();

	@Test
	void testLoadYamlConfigurationFragments(@TempDir Path tempDir) throws IOException {
		// Create temporary YAML configuration files
		final Path yamlFile1 = tempDir.resolve("config1.yaml");
		final Path yamlFile2 = tempDir.resolve("config2.yml");
		final Path nonYamlFile = tempDir.resolve("config.txt");

		final String yamlContent1 = "key1: value1";
		String yamlContent2 = "key2: value2\nkey3: 3";
		String txtContent = "This should not be parsed";

		Files.writeString(yamlFile1, yamlContent1);
		Files.writeString(yamlFile2, yamlContent2);
		Files.writeString(nonYamlFile, txtContent);

		// Invoke the provider to load configurations
		Collection<JsonNode> configurations = provider.load(tempDir);

		// Verify that only YAML files are loaded
		assertEquals(2, configurations.size(), "Should load exactly 2 YAML files");

		// Verify the contents of the loaded YAML configurations
		boolean config1Found = configurations.stream().anyMatch(node -> "value1".equals(node.get("key1").asText()));
		boolean config2Found = configurations.stream()
				.anyMatch(node -> "value2".equals(node.get("key2").asText()) && node.get("key3").asInt() == 3);

		assertTrue(config1Found, "config1.yaml should be correctly loaded");
		assertTrue(config2Found, "config2.yml should be correctly loaded");
	}

	@Test
	void testLoadWithInvalidYaml(@TempDir Path tempDir) throws IOException {
		Path invalidYaml = tempDir.resolve("invalid.yaml");
		Files.writeString(invalidYaml, "key: : : value");

		Collection<JsonNode> configurations = provider.load(tempDir);

		assertEquals(0, configurations.size(), "Invalid YAML should not be loaded");
	}

	@Test
	void testLoadEmptyDirectory(@TempDir Path tempDir) {
		Collection<JsonNode> configurations = provider.load(tempDir);
		assertTrue(configurations.isEmpty(), "Empty directory should yield no configurations");
	}
}