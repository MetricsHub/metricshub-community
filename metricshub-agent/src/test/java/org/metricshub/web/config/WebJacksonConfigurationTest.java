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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.uiconfig.ProtocolCheckRequestDto;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class WebJacksonConfigurationTest {

	@Test
	void testExtensionManagerJacksonCustomizerEnablesProtocolCheckDeserialization() throws Exception {
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(new OsCommandExtension()))
			.build();
		final AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		final AgentContextHolder agentContextHolder = new AgentContextHolder(agentContext);
		final Jackson2ObjectMapperBuilderCustomizer customizer =
			new WebJacksonConfiguration().extensionManagerJacksonCustomizer(agentContextHolder);

		final Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
		customizer.customize(builder);
		final ObjectMapper objectMapper = builder.build();

		final String json = """
			{
			  "hostname": "server1",
			  "protocol": "ssh",
			  "protocolConfig": {
			    "username": "admin",
			    "port": 22
			  }
			}
			""";

		final ProtocolCheckRequestDto request = objectMapper.readValue(json, ProtocolCheckRequestDto.class);

		assertEquals("server1", request.getHostname());
		assertEquals("ssh", request.getProtocol());
		final SshConfiguration ssh = (SshConfiguration) request.getProtocolConfig().get("ssh");
		assertNotNull(ssh);
		assertEquals("admin", ssh.getUsername());
		assertEquals(22, ssh.getPort());
	}
}
