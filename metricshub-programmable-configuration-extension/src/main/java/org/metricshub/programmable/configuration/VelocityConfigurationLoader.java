package org.metricshub.programmable.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Programmable Configuration Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeConstants.SpaceGobbling;

/**
 * Loads and evaluates a Velocity template configuration file.
 * <p>
 * The template may declare, through the {@code $schedule} tool, how often it must be re-evaluated
 * (see {@link ScheduleTool}). Rendering the template is what discovers that declaration, so
 * {@link #getCron()} is meaningful only once a render has run. Re-evaluating is simply rendering
 * again: every call re-runs the whole template against a fresh context, so nothing is carried over
 * from a previous render.
 * </p>
 */
@Slf4j
public class VelocityConfigurationLoader {

	/**
	 * Name of the Velocity runtime property, also usable as a JVM system property,
	 * controlling how whitespace and newlines surrounding directives are removed
	 * from the rendered output: {@code -Dparser.space_gobbling=lines}.
	 */
	static final String SPACE_GOBBLING_PROPERTY = RuntimeConstants.SPACE_GOBBLING;

	/**
	 * Space gobbling mode applied when the {@value #SPACE_GOBBLING_PROPERTY} system
	 * property is not set, or is set to an unsupported value. This is also Velocity's
	 * own default: lines made only of directives such as {@code #set}, {@code #if},
	 * {@code #foreach} or {@code #end} do not produce blank lines in the generated
	 * YAML, while the indentation of the surrounding content is preserved.
	 */
	static final SpaceGobbling DEFAULT_SPACE_GOBBLING = SpaceGobbling.LINES;

	/**
	 * Comma-separated list of the space gobbling modes supported by the Velocity
	 * parser, in the lower case form expected in the property value, for error
	 * reporting purposes.
	 */
	private static final String SUPPORTED_SPACE_GOBBLING_MODES = Stream.of(SpaceGobbling.values())
		.map(VelocityConfigurationLoader::toPropertyValue)
		.collect(Collectors.joining(", "));

	private final Path vmPath;

	private Map<String, Object> tools = new HashMap<>();

	/**
	 * The {@code $schedule} tool of this template. It is per-loader (never shared through the tools
	 * map) because the schedule it collects belongs to one template.
	 */
	private final ScheduleTool scheduleTool = new ScheduleTool();

	/**
	 * The cron declared by the last render that <b>completed</b>, which is what {@link #getCron()}
	 * reports. It is set only once a render reached its end, so a render that failed half-way (a data
	 * source that timed out, for example) keeps the schedule the template last established instead of
	 * appearing to declare none.
	 */
	private volatile String lastDeclaredCron;

	/**
	 * The engine, built on first use and reused across renders. Only the engine is reused: the
	 * template itself is re-read on every render (the file resource loader runs with its cache
	 * disabled), so an edit to the {@code .vm} file is picked up without rebuilding this loader.
	 */
	private VelocityEngine velocityEngine;

	/**
	 * Creates a loader for the given template.
	 *
	 * @param vmPath path to the {@code .vm} template file
	 * @param tools  the Velocity tools to expose (for example {@code $http}, {@code $json});
	 *               {@code $schedule} is added automatically and must not be supplied here
	 */
	public VelocityConfigurationLoader(final Path vmPath, final Map<String, Object> tools) {
		this.vmPath = vmPath;
		this.tools = tools;
	}

	/**
	 * Returns the cron expression the template declared through {@code $schedule.cron(...)} during
	 * the last render that completed. Meaningful only after a render has run.
	 * <p>
	 * A render that failed does not change this value: the template keeps the schedule it last
	 * declared, so a temporary failure of one of its data sources cannot make it look unscheduled and
	 * get its cron task cancelled.
	 * </p>
	 *
	 * @return the declared cron expression, or empty when the template declares no schedule
	 */
	public Optional<String> getCron() {
		return Optional.ofNullable(lastDeclaredCron);
	}

	/**
	 * Generates a YAML configuration from the Velocity template file.
	 *
	 * @return The generated YAML configuration as a String.
	 */
	public String generateYaml() {
		try {
			return generateYamlDangerous();
		} catch (Exception e) {
			log.error("Failed to evaluate Velocity template: '{}'. Error: {}", vmPath, e.getMessage());
			log.debug("Velocity template evaluation exception:", e);
			return null;
		}
	}

	/**
	 * Generates a YAML configuration from the Velocity template file,
	 * propagating any exception instead of returning {@code null}.
	 *
	 * @return The generated YAML configuration as a String.
	 * @throws Exception if the Velocity template evaluation fails
	 */
	public String generateYamlDangerous() throws Exception {
		// Initialize the VelocityEngine on first use, then reuse it across renders.
		if (velocityEngine == null) {
			final var engine = new VelocityEngine();
			var props = new Properties();
			props.setProperty("resource.loaders", "file");
			props.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
			props.setProperty("resource.loader.file.path", vmPath.getParent().toString());
			props.setProperty("resource.loader.file.cache", "false");
			props.setProperty(SPACE_GOBBLING_PROPERTY, toPropertyValue(resolveSpaceGobbling()));
			engine.init(props);
			velocityEngine = engine;
		}

		// Load template. The resource loader cache is disabled, so this re-reads the file and an edit
		// to the template is picked up on the next render.
		var templateName = vmPath.getFileName().toString();
		var template = velocityEngine.getTemplate(templateName, StandardCharsets.UTF_8.name());

		// Prepare a fresh context: a render never reuses values produced by a previous one.
		var context = new VelocityContext();

		// Add tools to context
		tools.forEach(context::put);

		// Expose the per-template $schedule tool, cleared so the schedule reflects this render only.
		scheduleTool.reset();
		context.put("schedule", scheduleTool);

		// Render template
		var writer = new StringWriter();
		template.merge(context, writer);

		// The render reached its end, so what the template declared is complete: publish it. Doing this
		// here and not right after reset() is what keeps a failed render from dropping the schedule.
		lastDeclaredCron = scheduleTool.getCron().orElse(null);

		return writer.toString();
	}

	/**
	 * Resolves the Velocity space gobbling mode to apply when rendering templates.
	 * <p>
	 * The mode is read from the {@value #SPACE_GOBBLING_PROPERTY} system property,
	 * for example {@code -Dparser.space_gobbling=structured}. When the property is
	 * absent, blank or holds an unsupported value, {@link #DEFAULT_SPACE_GOBBLING}
	 * is used.
	 * <p>
	 * Validating the value here is required: Velocity's own fallback in
	 * {@code RuntimeInstance.initializeSelfProperties()} catches
	 * {@link java.util.NoSuchElementException} while {@link Enum#valueOf(Class, String)}
	 * throws an {@link IllegalArgumentException}, so an unsupported value would
	 * otherwise fail the whole engine initialization.
	 *
	 * @return the space gobbling mode to apply, never {@code null}
	 */
	static SpaceGobbling resolveSpaceGobbling() {
		final String configuredMode = System.getProperty(SPACE_GOBBLING_PROPERTY);

		if (configuredMode == null || configuredMode.isBlank()) {
			return DEFAULT_SPACE_GOBBLING;
		}

		try {
			return SpaceGobbling.valueOf(configuredMode.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			log.warn(
				"Unsupported value '{}' for system property '{}'. Supported values are: {}. Falling back to '{}'.",
				configuredMode,
				SPACE_GOBBLING_PROPERTY,
				SUPPORTED_SPACE_GOBBLING_MODES,
				toPropertyValue(DEFAULT_SPACE_GOBBLING)
			);
			return DEFAULT_SPACE_GOBBLING;
		}
	}

	/**
	 * Converts a space gobbling mode to the lower case form expected in the
	 * {@value #SPACE_GOBBLING_PROPERTY} property value.
	 *
	 * @param mode the space gobbling mode
	 * @return the corresponding property value, for example {@code structured}
	 */
	private static String toPropertyValue(final SpaceGobbling mode) {
		return mode.name().toLowerCase(Locale.ROOT);
	}
}
