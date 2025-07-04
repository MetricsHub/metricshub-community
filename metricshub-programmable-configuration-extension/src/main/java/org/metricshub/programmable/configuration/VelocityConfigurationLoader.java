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
import java.util.Properties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

/**
 * Loads and evaluates a Velocity template configuration file
 */
@Slf4j
@AllArgsConstructor
public class VelocityConfigurationLoader {

	private final Path vmPath;

	/**
	 * Generates a YAML configuration from the Velocity template file.
	 *
	 * @return The generated YAML configuration as a String.
	 */
	public String generateYaml() {
		try {
			// Initialize VelocityEngine
			final var velocityEngine = new VelocityEngine();
			var props = new Properties();
			props.setProperty("resource.loader", "file");
			props.setProperty("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
			props.setProperty("file.resource.loader.path", vmPath.getParent().toString());
			props.setProperty("file.resource.loader.cache", "false");
			velocityEngine.init(props);

			// Load template
			var templateName = vmPath.getFileName().toString();
			var template = velocityEngine.getTemplate(templateName, StandardCharsets.UTF_8.name());

			// Prepare context
			var context = new VelocityContext();

			// Render template
			var writer = new StringWriter();
			template.merge(context, writer);

			return writer.toString();
		} catch (Exception e) {
			log.error("Failed to evaluate Velocity template: '{}'. Error: {}", vmPath, e.getMessage());
			log.debug("Velocity template evaluation exception:", e);
			return null;
		}
	}
}
