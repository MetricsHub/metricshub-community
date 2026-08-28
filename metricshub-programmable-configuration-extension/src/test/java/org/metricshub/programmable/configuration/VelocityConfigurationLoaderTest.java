package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.programmable.configuration.VelocityConfigurationLoader.DEFAULT_SPACE_GOBBLING;
import static org.metricshub.programmable.configuration.VelocityConfigurationLoader.SPACE_GOBBLING_PROPERTY;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import org.apache.velocity.runtime.RuntimeConstants.SpaceGobbling;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class VelocityConfigurationLoaderTest {

	/**
	 * Template exercising directive-only lines: a top-level {@code #set}, a
	 * {@code #foreach} loop, an indented {@code #set} and an indented
	 * {@code #if}/{@code #end} block.
	 */
	private static final String DIRECTIVE_ONLY_LINES_TEMPLATE = """
		resources:
		#set($hosts = ["host-01", "host-02"])
		#foreach($host in $hosts)
		  ${host}-system:
		    attributes:
		    #set($type = "linux")
		      host.name: ${host}
		    #if($type)
		      host.type: ${type}
		    #end
		#end
		""";

	@AfterEach
	void clearSpaceGobblingProperty() {
		System.clearProperty(SPACE_GOBBLING_PROPERTY);
	}

	/**
	 * Writes the given template content in {@code tempDir} and renders it.
	 *
	 * @param tempDir the directory hosting the template
	 * @param content the Velocity template content
	 * @return the generated YAML, with normalized line separators
	 * @throws IOException if the template cannot be written
	 */
	private static String render(final Path tempDir, final String content) throws IOException {
		final Path templatePath = tempDir.resolve("gobbling.vm");
		Files.writeString(templatePath, content, StandardCharsets.UTF_8);

		final String yaml = new VelocityConfigurationLoader(templatePath, Map.of()).generateYaml();
		assertNotNull(yaml, "Generated YAML should not be null");

		return yaml.replaceAll("\r\n", "\n");
	}

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

	@Test
	void testResolveSpaceGobblingDefaultsToLines() {
		assertEquals(
			SpaceGobbling.LINES,
			VelocityConfigurationLoader.resolveSpaceGobbling(),
			"Space gobbling should default to 'lines' when the system property is not set"
		);
		assertEquals(SpaceGobbling.LINES, DEFAULT_SPACE_GOBBLING, "The default should match Velocity's own default");
	}

	/**
	 * Every mode declared by Velocity must be accepted, so that the supported values
	 * cannot drift from the engine version in use.
	 */
	@ParameterizedTest
	@EnumSource(SpaceGobbling.class)
	void testResolveSpaceGobblingAcceptsEverySupportedMode(final SpaceGobbling mode) {
		System.setProperty(SPACE_GOBBLING_PROPERTY, mode.name().toLowerCase(Locale.ROOT));

		assertEquals(
			mode,
			VelocityConfigurationLoader.resolveSpaceGobbling(),
			"Every mode declared by Velocity should be supported"
		);
	}

	@ParameterizedTest
	@ValueSource(strings = { "STRUCTURED", "  Lines  ", "Bc" })
	void testResolveSpaceGobblingIsCaseInsensitiveAndTrimmed(final String configuredMode) {
		System.setProperty(SPACE_GOBBLING_PROPERTY, configuredMode);

		assertEquals(
			SpaceGobbling.valueOf(configuredMode.trim().toUpperCase(Locale.ROOT)),
			VelocityConfigurationLoader.resolveSpaceGobbling(),
			"Supported space gobbling modes should be accepted regardless of case and surrounding blanks"
		);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "gobble", "true" })
	void testResolveSpaceGobblingFallsBackOnUnsupportedModes(final String configuredMode) {
		System.setProperty(SPACE_GOBBLING_PROPERTY, configuredMode);

		assertEquals(
			DEFAULT_SPACE_GOBBLING,
			VelocityConfigurationLoader.resolveSpaceGobbling(),
			"Unsupported space gobbling modes should fall back to the default one"
		);
	}

	@Test
	void testGenerateYamlGobblesDirectiveOnlyLinesByDefault(@TempDir final Path tempDir) throws IOException {
		final String yaml = render(tempDir, DIRECTIVE_ONLY_LINES_TEMPLATE);

		assertEquals(
			"""
			resources:
			  host-01-system:
			    attributes:
			      host.name: host-01
			      host.type: linux
			  host-02-system:
			    attributes:
			      host.name: host-02
			      host.type: linux
			""",
			yaml,
			"Directive-only lines should not produce blank lines, and indentation should be preserved"
		);
	}

	@Test
	void testGenerateYamlKeepsDirectiveNewlinesWithNoneSpaceGobbling(@TempDir final Path tempDir) throws IOException {
		System.setProperty(SPACE_GOBBLING_PROPERTY, "none");

		final String yaml = render(tempDir, DIRECTIVE_ONLY_LINES_TEMPLATE);

		assertTrue(
			yaml.lines().anyMatch(String::isBlank),
			() -> "The 'none' mode should keep the newlines of directive-only lines, but got:\n" + yaml
		);
	}

	@Test
	void testGenerateYamlGobblesIndentationWithStructuredSpaceGobbling(@TempDir final Path tempDir) throws IOException {
		System.setProperty(SPACE_GOBBLING_PROPERTY, "structured");

		final String yaml = render(tempDir, DIRECTIVE_ONLY_LINES_TEMPLATE);

		assertEquals(
			"""
			resources:
			host-01-system:
			  attributes:
			    host.name: host-01
			  host.type: linux
			host-02-system:
			  attributes:
			    host.name: host-02
			  host.type: linux
			""",
			yaml,
			"The 'structured' mode should also gobble the indentation preceding directive-only lines"
		);
	}

	/**
	 * An unsupported value must not reach the Velocity engine: its own guard catches
	 * {@code NoSuchElementException} while {@code Enum.valueOf} throws an
	 * {@code IllegalArgumentException}, so {@code VelocityEngine.init} would fail and
	 * no template would be rendered at all.
	 */
	@Test
	void testGenerateYamlFallsBackToDefaultOnUnsupportedSpaceGobbling(@TempDir final Path tempDir) throws IOException {
		System.setProperty(SPACE_GOBBLING_PROPERTY, "gobble-everything");

		// render() already fails the test if the engine could not be initialized
		final String yaml = render(tempDir, DIRECTIVE_ONLY_LINES_TEMPLATE);

		assertTrue(
			yaml.lines().noneMatch(String::isBlank),
			() -> "An unsupported mode should fall back to 'lines', but got:\n" + yaml
		);
	}
}
