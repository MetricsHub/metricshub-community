package org.metricshub.web;

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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;

class MetricsHubAgentServerTest {

	@Test
	void shouldMergeDefaultAndUserWebConfiguration() {
		final AgentConfig agentConfig = AgentConfig
			.builder()
			.webConfig(Map.of("server.port", "1234", "spring.ai.mcp.server.enabled", "false"))
			.build();

		final Map<String, String> merged = MetricsHubAgentServer.mergeWebConfiguration(agentConfig);

		assertThat(merged.get("server.port")).isEqualTo("1234");
		assertThat(merged.get("spring.ai.mcp.server.enabled")).isEqualTo("false");
		assertThat(merged.get("spring.ai.mcp.server.name")).isEqualTo("metricshub-mcp-server");
	}

	@Test
	void shouldSkipNullValuesWhenMergingWebConfiguration() {
		final Map<String, String> userConfiguration = new HashMap<>();
		userConfiguration.put("server.port", null);
		final AgentConfig agentConfig = AgentConfig.builder().webConfig(userConfiguration).build();

		final Map<String, String> merged = MetricsHubAgentServer.mergeWebConfiguration(agentConfig);

		assertThat(merged.get("server.port")).isEqualTo("31888");
	}
}
