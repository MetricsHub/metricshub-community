package org.metricshub.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.uiconfig.ProtocolCheckRequestDto;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.json.JsonMapper;

class WebJacksonConfigurationTest {

	@Test
	void testExtensionManagerJacksonCustomizerEnablesProtocolCheckDeserialization() throws Exception {
		final ExtensionManager extensionManager = ExtensionManager.builder()
			.withProtocolExtensions(List.of(new OsCommandExtension()))
			.build();

		final AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		final AgentContextHolder agentContextHolder = new AgentContextHolder(agentContext);

		final JsonMapperBuilderCustomizer customizer = new WebJacksonConfiguration().extensionManagerJacksonCustomizer(
			agentContextHolder
		);

		final JsonMapper.Builder builder = JsonMapper.builder();
		customizer.customize(builder);
		final JsonMapper objectMapper = builder.build();

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
