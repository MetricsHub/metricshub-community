package org.metricshub.agent.m8b;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.M8bConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.agent.m8b.protocol.M8bJson;
import org.metricshub.agent.m8b.protocol.M8bMessage;
import org.metricshub.agent.m8b.protocol.M8bMessage.ToolInvoke;
import org.metricshub.agent.m8b.tunnel.FakeM8bServer;
import org.metricshub.agent.m8b.tunnel.M8bTunnelClient;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.config.ToolCallbackConfiguration;
import org.metricshub.web.mcp.ListResourcesService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * The whole agent side over a real WebSocket: the Spring AI tool registry built by
 * {@link ToolCallbackConfiguration} is advertised to a fake Governor, and a {@code tool.invoke} of the
 * real {@code ListHosts} tool comes back as {@code tool.result}.
 */
@SpringJUnitConfig(classes = { ToolCallbackConfiguration.class, M8bServiceEndToEndTest.TestConfig.class })
class M8bServiceEndToEndTest {

	private static final long TIMEOUT_MS = 15_000;

	@Configuration
	static class TestConfig {

		@Bean
		AgentContextHolder agentContextHolder() {
			return mock(AgentContextHolder.class);
		}

		@Bean
		ListResourcesService listResourcesService(final AgentContextHolder agentContextHolder) {
			return new ListResourcesService(agentContextHolder);
		}
	}

	@Autowired
	private ToolCallbackProvider toolCallbackProvider;

	@Autowired
	private AgentContextHolder agentContextHolder;

	private FakeM8bServer server;
	private M8bService service;

	@BeforeEach
	void setUp() throws Exception {
		server = new FakeM8bServer();
		server.startAndAwait();

		final AgentConfig agentConfig = AgentConfig.builder()
			.m8b(
				M8bConfig.builder()
					.enabled(true)
					.endpoint(server.uri().toString())
					.headers(Map.of("Authorization", "Bearer secret"))
					.build()
			)
			.resources(
				Map.of(
					"server-01",
					ResourceConfig.builder()
						.attributes(Map.of("host.name", "server-01", "host.type", "linux"))
						.protocols(
							Map.of("ssh", SshConfiguration.sshConfigurationBuilder().hostname("server-01.example.com").build())
						)
						.build()
				)
			)
			.build();
		final Map<String, Map<String, TelemetryManager>> active = new HashMap<>();
		active.put("metricshub-top-level-rg", Map.of("server-01", mock(TelemetryManager.class)));
		final AgentInfo agentInfo = mock(AgentInfo.class);
		when(agentInfo.getAttributes()).thenReturn(Map.of("service.name", "MetricsHub Agent", "version", "3.9.07"));
		final AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getAgentConfig()).thenReturn(agentConfig);
		when(agentContext.getAgentInfo()).thenReturn(agentInfo);
		when(agentContext.getTelemetryManagers()).thenReturn(active);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContextHolder.getGeneration()).thenReturn(1L);

		service = new M8bService(agentContextHolder, toolCallbackProvider, M8bTunnelClient::new, () ->
			"01923e4a-7c1e-7f4b-8a2d-3c5e6f7a8b9c"
		);
	}

	@AfterEach
	void tearDown() throws Exception {
		service.shutdown();
		server.stop(1000);
	}

	@Test
	void shouldAdvertiseTheRealToolsAndExecuteListHosts() throws Exception {
		service.supervise();

		final Map<String, String> handshake = server.awaitHandshake(TIMEOUT_MS);
		assertNotNull(handshake);
		assertEquals("Bearer secret", handshake.get("Authorization"));

		final JsonNode register = server.awaitFrame(M8bMessage.AgentRegister.TYPE, TIMEOUT_MS);
		assertNotNull(register, "agent.register expected");
		assertEquals("MetricsHub Agent", register.at("/agent/name").asText());
		assertEquals("Community", register.at("/agent/edition").asText());
		final List<String> tools = register.findValues("name").stream().map(JsonNode::asText).toList();
		assertTrue(tools.contains("ListHosts"), "The real Spring AI tool registry must be advertised: " + tools);
		assertEquals("server-01", register.at("/hosts/0/resourceKey").asText());
		assertEquals("server-01.example.com", register.at("/hosts/0/hostnames/ssh").asText());

		// The registration must be ACKNOWLEDGED before an invocation is sent, not merely received:
		// awaitFrame above returns as the frame arrives, while the answer to it is written on the
		// server's own thread. Sending from here first would put a tool.invoke on the wire ahead of
		// the agent.registered, and the client refuses an invocation on an unregistered session.
		assertTrue(server.awaitRegistrationAck(TIMEOUT_MS), "The server must acknowledge the registration");

		server.sendToAll(new ToolInvoke("req-1", "ListHosts", M8bJson.MAPPER.createObjectNode(), 30_000));

		final JsonNode result = server.awaitFrame(M8bMessage.ToolResult.TYPE, TIMEOUT_MS);
		assertNotNull(result, "tool.result expected");
		assertEquals("req-1", result.get("requestId").asText());
		assertEquals("metricshub-top-level-rg", result.at("/result/server-01/resourceGroupKey").asText());

		server.sendToAll(new ToolInvoke("req-2", "NotAdvertised", M8bJson.MAPPER.createObjectNode(), 30_000));

		final JsonNode error = server.awaitFrame(M8bMessage.ToolError.TYPE, TIMEOUT_MS);
		assertNotNull(error, "tool.error expected");
		assertEquals("TOOL_NOT_AVAILABLE", error.get("code").asText());
	}
}
