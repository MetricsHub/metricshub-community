package org.metricshub.web.config;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.metricshub.engine.common.helpers.VersionHelper;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration class for OpenAPI documentation.
 */
@Configuration
public class OpenApiConfig {

	/**
	 * Creates the OpenAPI metadata bean used by springdoc to generate the specification.
	 *
	 * @return the configured {@link OpenAPI} instance
	 */
	@Bean
	public OpenAPI metricshubOpenAPI() {
		return new OpenAPI()
			.info(
				new Info()
					.title("MetricsHub Agent API")
					.description("REST API for the MetricsHub Agent — infrastructure metrics collection and management.")
					.version(VersionHelper.getClassVersion())
					.license(new License().name("AGPL-3.0").url("https://www.gnu.org/licenses/agpl-3.0.html"))
			);
	}

	/**
	 * Configures the Swagger UI to enable the search/filter box, allowing users to easily find specific
	 * endpoints in the API documentation.
	 *
	 * @param config the existing SwaggerUiConfigProperties bean to customize
	 * @return the modified SwaggerUiConfigProperties bean with filtering enabled
	 */
	@Bean
	@Primary
	public SwaggerUiConfigProperties swaggerUiConfig(final SwaggerUiConfigProperties config) {
		config.setFilter("true");
		return config;
	}
}
