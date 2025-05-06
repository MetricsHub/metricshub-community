package org.metricshub.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlConfigurationProviderTest {

	private final YamlConfigurationProvider provider = new YamlConfigurationProvider();

	@Test
	void testLoadYamlConfigurationFragments(@TempDir Path tempDir) throws IOException {
		// Create temporary YAML configuration files
		final Path yamlFile1 = tempDir.resolve("config1.yaml");
		final Path yamlFile2 = tempDir.resolve("config2.yml");
		final Path nonYamlFile = tempDir.resolve("config.txt");

		// Create an empty directory to make sure the parser does not fail
		Files.createDirectories(tempDir.resolve("additional-dir"));

		final String yamlContent1 = "key1: value1";
		final String yamlContent2 = "key2: value2\nkey3: 3";
		final String txtContent = "This should not be parsed";

		Files.writeString(yamlFile1, yamlContent1);
		Files.writeString(yamlFile2, yamlContent2);
		Files.writeString(nonYamlFile, txtContent);

		// Invoke the provider to load configurations
		final Collection<JsonNode> configurations = provider.load(tempDir);

		// Verify that only YAML files are loaded
		assertEquals(2, configurations.size(), "Should load exactly 2 YAML files");

		// Verify the contents of the loaded YAML configurations
		final boolean config1Found = configurations
			.stream()
			.anyMatch(node -> "value1".equals(getJsonNodeAsText(node, "key1")));
		final boolean config2Found = configurations
			.stream()
			.anyMatch(node ->
				"value2".equals(getJsonNodeAsText(node, "key2")) && Integer.valueOf(3).equals(getJsonNodeAsInt(node, "key3"))
			);

		assertTrue(config1Found, "config1.yaml should be correctly loaded");
		assertTrue(config2Found, "config2.yml should be correctly loaded");
	}

	/**
	 * Get the JSON node as text.
	 *
	 * @param node the JSON node which contains the key
	 * @param key  the key to look for
	 * @return the value of the key as text or null if the key does not exist
	 */
	private String getJsonNodeAsText(final JsonNode node, final String key) {
		final JsonNode jsonNode = node.get(key);
		if (jsonNode == null) {
			return null;
		}
		return jsonNode.asText();
	}

	/**
	 * Get the JSON node as integer.
	 *
	 * @param node the JSON node which contains the key
	 * @param key  the key to look for
	 * @return the value of the key as integer or null if the key does not exist
	 */
	private Integer getJsonNodeAsInt(final JsonNode node, final String key) {
		final JsonNode jsonNode = node.get(key);
		if (jsonNode == null) {
			return null;
		}
		return jsonNode.asInt();
	}

	@Test
	void testLoadWithInvalidYaml(@TempDir Path tempDir) throws IOException {
		final Path invalidYaml = tempDir.resolve("invalid.yaml");
		Files.writeString(invalidYaml, "key: : : value");

		final Collection<JsonNode> configurations = provider.load(tempDir);

		assertEquals(0, configurations.size(), "Invalid YAML should not be loaded");
	}

	@Test
	void testLoadEmptyDirectory(@TempDir Path tempDir) {
		final Collection<JsonNode> configurations = provider.load(tempDir);
		assertTrue(configurations.isEmpty(), "Empty directory should yield no configurations");
	}

	@Test
	void testLoadWithInvalidDirectory(@TempDir Path tempDir) {
		final Path invalidDir = tempDir.resolve("invalid-dir");
		final Collection<JsonNode> configurations = provider.load(invalidDir);
		assertTrue(configurations.isEmpty(), "Invalid directory should yield no configurations");
	}

	@Test
	void testGetFileExtensions() {
		final Collection<String> extensions = provider.getFileExtensions();
		assertTrue(extensions.contains("yaml"), "Should support 'yaml' extension");
		assertTrue(extensions.contains("yml"), "Should support 'yml' extension");
		assertEquals(2, extensions.size(), "Should support exactly 2 file extensions");
	}
}
