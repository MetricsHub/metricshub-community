package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityConfigurationLoaderTest {

	@Test
	void testGenerateYamlFromSystemHostsTemplate() {
		// Load template path from test resources
		final Path templatePath = Paths.get("src/test/resources/config/system-hosts.vm");
		assertTrue(templatePath.toFile().exists(), "Template file should exist");

		// Load and generate YAML
		final VelocityConfigurationLoader loader = new VelocityConfigurationLoader(templatePath, Map.of());
		final String yaml = loader.generateYaml();

		// Validate
		assertNotNull(yaml, "Generated YAML should not be null");
		assertEquals(
			"""
			resources:
			  host-01-system:
			    attributes:
			      host.name: host-01
			      host.type: linux
			    protocols:
			      ssh:
			        username: user
			        password: pass
			    connectors: ["#system"]
			  host-02-system:
			    attributes:
			      host.name: host-02
			      host.type: linux
			    protocols:
			      ssh:
			        username: user
			        password: pass
			    connectors: ["#system"]
			  host-03-system:
			    attributes:
			      host.name: host-03
			      host.type: linux
			    protocols:
			      ssh:
			        username: user
			        password: pass
			    connectors: ["#system"]
			""",
			yaml.replaceAll("\r\n", "\n"),
			"Generated YAML should match expected output"
		);
	}

	@Test
	void testGenerateYamlReturnsNullOnError(@TempDir final Path tempDir) {
		// Point to a non-existent template file – VelocityEngine will fail
		final Path badPath = tempDir.resolve("does-not-exist.vm");
		final VelocityConfigurationLoader loader = new VelocityConfigurationLoader(badPath, Map.of());
		final String yaml = loader.generateYaml();

		assertNull(yaml, "generateYaml should return null when the template cannot be evaluated");
	}
}
