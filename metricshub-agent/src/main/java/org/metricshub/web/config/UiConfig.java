package org.metricshub.web.config;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.helper.ConfigHelper;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Configuration class for setting up UI-related configurations in the MetricsHub web application.
 */
@Configuration
@Slf4j
public class UiConfig implements WebMvcConfigurer {

	/**
	 * Configures view controllers to redirect the root URL ("/") to "index.html".
	 */
	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/").setViewName("forward:/index.html");
	}

	/**
	 * Configures resource handlers to serve static resources from a specific file location.
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		final String webRoot = "file:" + getWebDirectory() + "/";

		log.info("Serving web resources from {}", webRoot);

		registry
			.addResourceHandler("/**")
			.addResourceLocations(webRoot)
			.resourceChain(true)
			.addResolver(
				new PathResourceResolver() {
					@Override
					protected Resource getResource(String resourcePath, Resource location) throws IOException {
						// Serve the file if it exists
						Resource requested = location.createRelative(resourcePath);
						if (requested.exists() && requested.isReadable()) {
							return requested;
						}

						// Don’t swallow API or real files with extensions
						if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
							// let other handlers (e.g., controllers) deal with it
							return null;
						}

						// Fallback to the SPA entry point for client-side routes
						return location.createRelative("index.html");
					}
				}
			);
	}

	/**
	 * Retrieves the web directory path from system properties or defaults to a predefined location.
	 *
	 * @return The path to the web directory as a string.
	 */
	private static String getWebDirectory() {
		// Get the path directory from the java properties or from the default location
		final var webDir = System.getProperty("web.dir");
		if (webDir != null && !webDir.isBlank()) {
			return webDir;
		}
		return ConfigHelper.getSubPath("web").toString();
	}
}
