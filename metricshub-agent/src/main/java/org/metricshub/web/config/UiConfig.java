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
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.helper.ConfigHelper;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
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
		final Path webDirectory = Paths.get(getWebDirectory()).toAbsolutePath().normalize();
		final String webRoot = webDirectory.toUri().toString();
		final String webRootLocation = webRoot.endsWith("/") ? webRoot : webRoot + "/";

		log.info("Serving web resources from {}", webRootLocation);

		registry
			.setOrder(Ordered.LOWEST_PRECEDENCE)
			.addResourceHandler("/**")
			.addResourceLocations(webRootLocation)
			.resourceChain(true)
			.addResolver(
				new PathResourceResolver() {
					@Override
					protected Resource getResource(String resourcePath, Resource location) throws IOException {
						// Let springdoc handle its own paths
						if (resourcePath.startsWith("v3/api-docs") || resourcePath.startsWith("swagger-ui")) {
							return null;
						}

						// Serve the file if it exists
						Resource requested = location.createRelative(resourcePath);
						if (requested.exists() && requested.isReadable()) {
							return requested;
						}

						// Missing static assets must 404 — never return index.html (wrong MIME type for .js/.css)
						if (isStaticAssetPath(resourcePath)) {
							return null;
						}

						// Fallback to the SPA entry point for client-side routes
						return location.createRelative("index.html");
					}
				}
			);
	}

	/**
	 * Returns whether the request path targets a bundled static asset rather than a client route.
	 *
	 * @param resourcePath path relative to the web root
	 * @return {@code true} when the path should not fall back to {@code index.html}
	 */
	private static boolean isStaticAssetPath(final String resourcePath) {
		if (resourcePath == null || resourcePath.isBlank()) {
			return false;
		}
		if (resourcePath.startsWith("assets/")) {
			return true;
		}
		final int dotIndex = resourcePath.lastIndexOf('.');
		if (dotIndex < 0 || dotIndex == resourcePath.length() - 1) {
			return false;
		}
		return switch (resourcePath.substring(dotIndex + 1).toLowerCase()) {
			case
				"js",
				"mjs",
				"css",
				"map",
				"woff",
				"woff2",
				"ttf",
				"otf",
				"eot",
				"png",
				"jpg",
				"jpeg",
				"gif",
				"svg",
				"webp",
				"ico" -> true;
			default -> false;
		};
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
