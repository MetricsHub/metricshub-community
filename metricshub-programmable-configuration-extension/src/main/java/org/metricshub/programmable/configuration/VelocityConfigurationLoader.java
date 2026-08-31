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
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeConstants.SpaceGobbling;

/**
 * Loads and evaluates a Velocity template configuration file
 */
@Slf4j
@AllArgsConstructor
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
		// Initialize VelocityEngine
		final var velocityEngine = new VelocityEngine();
		var props = new Properties();
		props.setProperty("resource.loaders", "file");
		props.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
		props.setProperty("resource.loader.file.path", vmPath.getParent().toString());
		props.setProperty("resource.loader.file.cache", "false");
		props.setProperty(SPACE_GOBBLING_PROPERTY, toPropertyValue(resolveSpaceGobbling()));
		velocityEngine.init(props);

		// Load template
		var templateName = vmPath.getFileName().toString();
		var template = velocityEngine.getTemplate(templateName, StandardCharsets.UTF_8.name());

		// Prepare context
		var context = new VelocityContext();

		// Add tools to context
		tools.forEach(context::put);

		// Render template
		var writer = new StringWriter();
		template.merge(context, writer);

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
