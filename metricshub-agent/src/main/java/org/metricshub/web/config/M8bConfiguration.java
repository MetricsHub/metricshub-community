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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.service.M8bControlPlaneClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for M8B Control Plane integration.
 * <p>
 * This configuration enables the M8B WebSocket client when the M8B
 * configuration properties are set in the application configuration.
 * </p>
 * <p>
 * To enable M8B integration, configure the following properties in metricshub.yaml:
 * </p>
 * <pre>
 * web:
 *   m8b.enabled: true
 *   m8b.url: ws://localhost:8080/ws/agent
 *   m8b.api-key: your-api-key
 *   m8b.agent-id: optional-custom-agent-id
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(M8bConfigurationProperties.class)
public class M8bConfiguration {

	/**
	 * Creates the M8B Control Plane client bean.
	 * <p>
	 * The client will automatically connect to the M8B server on startup
	 * if the configuration is valid (enabled, url, and api-key are set).
	 * </p>
	 *
	 * @param config             the M8B configuration properties
	 * @param agentContextHolder the agent context holder
	 * @param objectMapper       the JSON object mapper
	 * @return the M8B control plane client
	 */
	@Bean
	public M8bControlPlaneClient m8bControlPlaneClient(
		M8bConfigurationProperties config,
		AgentContextHolder agentContextHolder,
		ObjectMapper objectMapper
	) {
		return new M8bControlPlaneClient(config, agentContextHolder, objectMapper);
	}
}
