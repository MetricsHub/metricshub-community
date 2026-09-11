package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.velocity.runtime.RuntimeConstants.SpaceGobbling;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

	/**
	 * Value the test JVM was launched with, if any.
	 */
	private static String originalSpaceGobbling;

	@BeforeAll
	static void captureSpaceGobblingProperty() {
		originalSpaceGobbling = System.getProperty(SPACE_GOBBLING_PROPERTY);
	}

	/**
	 * Restores the value the test JVM was launched with, so that this class does not
	 * leak its own settings to the test classes running after it.
	 */
	@AfterAll
	static void restoreSpaceGobblingProperty() {
		if (originalSpaceGobbling == null) {
			System.clearProperty(SPACE_GOBBLING_PROPERTY);
		} else {
			System.setProperty(SPACE_GOBBLING_PROPERTY, originalSpaceGobbling);
		}
	}

	/**
	 * Starts every test from the same state: the property is absent unless the test
	 * sets it itself, whatever the test JVM was launched with. Without this, running
	 * the build with {@code -Dparser.space_gobbling=none} would make the outcome of
	 * this class depend on the execution order.
	 */
	@BeforeEach
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

	@Test
	void testScheduleDeclarationIsDiscoveredAndPrintsNothing(@TempDir final Path tempDir) throws IOException {
		final Path templatePath = tempDir.resolve("scheduled.vm");
		Files.writeString(templatePath, "$schedule.cron('0/5 * * * * ?')\nresources: {}\n", StandardCharsets.UTF_8);

		final VelocityConfigurationLoader loader = new VelocityConfigurationLoader(templatePath, Map.of());
		final String yaml = loader.generateYaml();

		assertEquals(Optional.of("0/5 * * * * ?"), loader.getCron());
		assertTrue(yaml.contains("resources:"), () -> "The body must still render, but got:\n" + yaml);
		assertTrue(!yaml.contains("schedule"), () -> "The directive must print nothing, but got:\n" + yaml);
	}

	@Test
	void testNoScheduleDeclarationYieldsNoCron(@TempDir final Path tempDir) throws IOException {
		final Path templatePath = tempDir.resolve("plain.vm");
		Files.writeString(templatePath, "resources: {}\n", StandardCharsets.UTF_8);

		final VelocityConfigurationLoader loader = new VelocityConfigurationLoader(templatePath, Map.of());
		loader.generateYaml();

		assertTrue(loader.getCron().isEmpty(), "A template without $schedule.cron declares no schedule");
	}

	/**
	 * The schedule must reflect the template as it is now: re-rendering after the directive was
	 * removed must leave no schedule behind.
	 */
	@Test
	void testRemovingTheDeclarationClearsTheCronOnNextRender(@TempDir final Path tempDir) throws IOException {
		final Path templatePath = tempDir.resolve("scheduled.vm");
		Files.writeString(templatePath, "$schedule.cron('0/5 * * * * ?')\nresources: {}\n", StandardCharsets.UTF_8);

		final VelocityConfigurationLoader loader = new VelocityConfigurationLoader(templatePath, Map.of());
		loader.generateYaml();
		assertEquals(Optional.of("0/5 * * * * ?"), loader.getCron());

		Files.writeString(templatePath, "resources: {}\n", StandardCharsets.UTF_8);
		loader.generateYaml();

		assertTrue(loader.getCron().isEmpty(), "The removed declaration must not survive the next render");
	}

	/**
	 * Velocity tool whose only method fails, standing for a data source ({@code $http}, {@code $sql})
	 * that is temporarily unreachable. Public so Velocity can introspect it.
	 */
	public static class ExplodingTool {

		/**
		 * Always fails.
		 *
		 * @return never returns
		 */
		public String explode() {
			throw new IllegalStateException("The data source is unavailable.");
		}
	}

	/**
	 * A template whose render fails part-way must keep the schedule it last declared. Reporting no
	 * schedule would have the discovery sweep treat it as undeclared and cancel its cron task, so a
	 * data source failing once would stop the template from ever being re-evaluated again.
	 */
	@Test
	void testAFailedRenderKeepsTheLastDeclaredCron(@TempDir final Path tempDir) throws IOException {
		final Path templatePath = tempDir.resolve("scheduled.vm");
		Files.writeString(templatePath, "$schedule.cron('0/5 * * * * ?')\nresources: {}\n", StandardCharsets.UTF_8);

		final VelocityConfigurationLoader loader = new VelocityConfigurationLoader(
			templatePath,
			Map.of("boom", new ExplodingTool())
		);
		loader.generateYaml();
		assertEquals(Optional.of("0/5 * * * * ?"), loader.getCron());

		// Same declaration, but a data source read after it now fails.
		Files.writeString(
			templatePath,
			"$schedule.cron('0/5 * * * * ?')\nresources: $boom.explode()\n",
			StandardCharsets.UTF_8
		);
		assertNull(loader.generateYaml(), "A render that throws produces no YAML");

		assertEquals(
			Optional.of("0/5 * * * * ?"),
			loader.getCron(),
			"The schedule declared by the last successful render must survive a failed one"
		);
	}

	/**
	 * Two renders of the same template can overlap: a cron firing or an on-demand request on one side,
	 * the configuration watcher's reload on the other. Each render collects its declarations in a tool
	 * of its own, so neither can read or clear the other's, and the schedule stays what the template
	 * says whichever order they finish in.
	 */
	@Test
	void testConcurrentRendersKeepTheDeclaredCron(@TempDir final Path tempDir) throws Exception {
		final Path templatePath = tempDir.resolve("scheduled.vm");
		Files.writeString(templatePath, "$schedule.cron('0/5 * * * * ?')\nresources: {}\n", StandardCharsets.UTF_8);

		final VelocityConfigurationLoader loader = new VelocityConfigurationLoader(templatePath, Map.of());
		final List<String> wrongCrons = Collections.synchronizedList(new ArrayList<>());

		final List<Thread> renders = new ArrayList<>();
		for (int thread = 0; thread < 4; thread++) {
			renders.add(
				new Thread(() -> {
					for (int round = 0; round < 20; round++) {
						loader.generateYaml();
						final Optional<String> cron = loader.getCron();
						if (!Optional.of("0/5 * * * * ?").equals(cron)) {
							wrongCrons.add(String.valueOf(cron.orElse(null)));
						}
					}
				})
			);
		}
		renders.forEach(Thread::start);
		for (final Thread render : renders) {
			render.join(30_000);
			assertFalse(render.isAlive(), "Every render must have completed");
		}

		assertTrue(wrongCrons.isEmpty(), () -> "Overlapping renders reported a wrong schedule: " + wrongCrons);
	}

	/**
	 * A template that never rendered successfully declares nothing: there is no earlier schedule to
	 * fall back on.
	 */
	@Test
	void testAFailedFirstRenderLeavesNoCron(@TempDir final Path tempDir) throws IOException {
		final Path templatePath = tempDir.resolve("scheduled.vm");
		Files.writeString(
			templatePath,
			"$schedule.cron('0/5 * * * * ?')\nresources: $boom.explode()\n",
			StandardCharsets.UTF_8
		);

		final VelocityConfigurationLoader loader = new VelocityConfigurationLoader(
			templatePath,
			Map.of("boom", new ExplodingTool())
		);
		assertNull(loader.generateYaml(), "A render that throws produces no YAML");

		assertTrue(loader.getCron().isEmpty(), "A template that never rendered declares no schedule");
	}
}
