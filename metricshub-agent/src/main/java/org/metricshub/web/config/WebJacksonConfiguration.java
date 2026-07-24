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

import com.fasterxml.jackson.databind.InjectableValues;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson customization for REST controllers that deserialize typed protocol configurations.
 */
@Configuration
public class WebJacksonConfiguration {

	/**
	 * Injects the active {@link ExtensionManager} into Jackson deserialization contexts.
	 *
	 * @param agentContextHolder holder for the active agent context
	 * @return Jackson customizer
	 */
	@Bean
	public Jackson2ObjectMapperBuilderCustomizer extensionManagerJacksonCustomizer(
		final AgentContextHolder agentContextHolder
	) {
		return builder ->
			builder.postConfigurer(objectMapper -> {
				final InjectableValues.Std injectableValues = new InjectableValues.Std();
				injectableValues.addValue(ExtensionManager.class, agentContextHolder);
				injectableValues.addValue(ExtensionManager.class.getName(), agentContextHolder);
				objectMapper.setInjectableValues(injectableValues);
			});
	}
}
