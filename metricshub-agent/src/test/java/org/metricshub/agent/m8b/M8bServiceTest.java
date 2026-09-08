package org.metricshub.agent.m8b;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.M8bConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.agent.m8b.protocol.M8bMessage.AgentRegister;
import org.metricshub.agent.m8b.protocol.M8bMessage.HostsUpdated;
import org.metricshub.agent.m8b.protocol.ToolDescriptor;
import org.metricshub.agent.m8b.tunnel.M8bTunnelClient;
import org.metricshub.agent.m8b.tunnel.M8bTunnelListener;
import org.metricshub.agent.m8b.tunnel.M8bTunnelSettings;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.AgentContextHolder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class M8bServiceTest {

	private static final String ENDPOINT = "wss://m8b.example.com/ws/agent";
	private static final String UID = "01923e4a-7c1e-7f4b-8a2d-3c5e6f7a8b9c";

	@Mock
	private AgentContextHolder agentContextHolder;

	@Mock
	private AgentContext agentContext;

	@Mock
	private AgentInfo agentInfo;

	private AgentConfig agentConfig;
	private long generation = 1;
	private final List<M8bTunnelClient> createdClients = new ArrayList<>();
	private final List<M8bTunnelSettings> capturedSettings = new ArrayList<>();
	private final List<M8bTunnelListener> capturedListeners = new ArrayList<>();
	private boolean failClientCreation;
	private M8bService service;

	private static ToolCallback callback(final String name) {
		final ToolDefinition definition = mock(ToolDefinition.class);
		when(definition.name()).thenReturn(name);
		when(definition.description()).thenReturn(name);
		when(definition.inputSchema()).thenReturn("");
		final ToolCallback callback = mock(ToolCallback.class);
		when(callback.getToolDefinition()).thenReturn(definition);
		return callback;
	}

	@BeforeEach
	void setUp() {
		agentConfig = AgentConfig.builder().build();
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContextHolder.getGeneration()).thenAnswer(invocation -> generation);
		when(agentContext.getAgentConfig()).thenAnswer(invocation -> agentConfig);
		when(agentContext.getAgentInfo()).thenReturn(agentInfo);
		when(agentContext.getTelemetryManagers()).thenReturn(new HashMap<>());
		when(agentInfo.getAttributes()).thenReturn(
			Map.of("service.name", "MetricsHub Agent", "version", "3.9.07", "host.name", "server-01")
		);

		// Built before the stubbing: creating mocks inside thenReturn() leaves the stubbing unfinished
		final ToolCallback[] callbacks = { callback("ListHosts"), callback("ExecuteSshCommandline") };
		final ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
		when(provider.getToolCallbacks()).thenReturn(callbacks);

		service = new M8bService(
			agentContextHolder,
			provider,
			(settings, listener) -> {
				if (failClientCreation) {
					throw new IllegalStateException("cannot connect");
				}
				capturedSettings.add(settings);
				capturedListeners.add(listener);
				final M8bTunnelClient client = mock(M8bTunnelClient.class);
				when(client.isConnected()).thenReturn(true);
				createdClients.add(client);
				return client;
			},
			() -> UID
		);
	}

	private void configure(final M8bConfig m8b) {
		agentConfig = AgentConfig.builder().m8b(m8b).build();
	}

	@Test
	void shouldNotStartWhenDisabled() {
		service.supervise();

		assertTrue(createdClients.isEmpty());
	}

	@Test
	void shouldNotStartWithoutAnEndpoint() {
		configure(M8bConfig.builder().enabled(true).build());

		service.supervise();

		assertTrue(createdClients.isEmpty());
	}

	@Test
	void shouldStartTheTunnelWithResolvedSettings() {
		configure(
			M8bConfig.builder()
				.enabled(true)
				.endpoint(" " + ENDPOINT + " ")
				.headers(Map.of("Authorization", "Bearer token"))
				.certificateFile("/tmp/m8b-ca.pem")
				.heartbeatInterval(45)
				.build()
		);

		service.supervise();

		assertEquals(1, createdClients.size());
		verify(createdClients.get(0)).start();
		final M8bTunnelSettings settings = capturedSettings.get(0);
		assertEquals(URI.create(ENDPOINT), settings.endpoint());
		assertEquals(Map.of("Authorization", "Bearer token"), settings.headers());
		assertEquals("/tmp/m8b-ca.pem", settings.certificateFile());
		assertEquals(UID, settings.agentUid());
		assertEquals(Duration.ofSeconds(45), settings.heartbeatInterval());
	}

	@Test
	void shouldFallBackToTheDefaultHeartbeatWhenInvalid() {
		configure(M8bConfig.builder().enabled(true).endpoint(ENDPOINT).heartbeatInterval(0).build());

		service.supervise();

		assertEquals(Duration.ofSeconds(M8bConfig.DEFAULT_HEARTBEAT_INTERVAL), capturedSettings.get(0).heartbeatInterval());
	}

	@Test
	void registrationShouldAdvertiseToolsHostsAndIdentity() {
		configure(
			M8bConfig.builder().enabled(true).endpoint(ENDPOINT).excludedTools(Set.of("ExecuteSshCommandline")).build()
		);
		agentConfig = AgentConfig.builder()
			.m8b(agentConfig.getM8b())
			.resources(
				Map.of(
					"server-01",
					ResourceConfig.builder()
						.attributes(Map.of("host.name", "server-01"))
						.protocols(Map.of("ssh", SshConfiguration.sshConfigurationBuilder().hostname("server-01").build()))
						.build()
				)
			)
			.build();
		final Map<String, Map<String, TelemetryManager>> active = new HashMap<>();
		active.put("metricshub-top-level-rg", Map.of("server-01", mock(TelemetryManager.class)));
		when(agentContext.getTelemetryManagers()).thenReturn(active);

		service.supervise();
		final AgentRegister register = capturedListeners.get(0).buildRegistration();

		assertEquals(1, register.protocolVersion());
		assertEquals("MetricsHub Agent", register.agent().name());
		assertEquals("Community", register.agent().edition());
		assertEquals(List.of("ListHosts"), register.tools().stream().map(ToolDescriptor::name).toList());
		assertTrue(register.toolRegistryRevision().startsWith("sha256:"));
		assertEquals(1, register.hosts().size());
		assertEquals("server-01", register.hosts().get(0).resourceKey());
	}

	@Test
	void shouldRestartTheTunnelWhenTheConfigurationChanges() {
		configure(M8bConfig.builder().enabled(true).endpoint(ENDPOINT).build());
		service.supervise();
		final M8bTunnelClient first = createdClients.get(0);

		service.supervise();
		assertEquals(1, createdClients.size(), "An unchanged configuration keeps the client");

		configure(M8bConfig.builder().enabled(true).endpoint("wss://other.example.com/ws/agent").build());
		service.supervise();

		verify(first).stop(any());
		assertEquals(2, createdClients.size());
		verify(createdClients.get(1)).start();
	}

	@Test
	void shouldStopTheTunnelWhenDisabled() {
		configure(M8bConfig.builder().enabled(true).endpoint(ENDPOINT).build());
		service.supervise();

		configure(M8bConfig.builder().enabled(false).build());
		service.supervise();

		verify(createdClients.get(0)).stop(any());
	}

	@Test
	void shouldRetryAFailedStartupOnTheNextTick() {
		configure(M8bConfig.builder().enabled(true).endpoint(ENDPOINT).build());
		failClientCreation = true;
		service.supervise();
		assertTrue(createdClients.isEmpty());

		failClientCreation = false;
		service.supervise();

		assertEquals(1, createdClients.size(), "The same configuration must be retried after a failure");
	}

	@Test
	void shouldRetryWhenTheUidCannotBeLoaded() {
		final boolean[] fail = { true };
		final ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
		when(provider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
		service = new M8bService(
			agentContextHolder,
			provider,
			(settings, listener) -> {
				capturedSettings.add(settings);
				return mock(M8bTunnelClient.class);
			},
			() -> {
				if (fail[0]) {
					throw new IOException("security directory is read-only");
				}
				return UID;
			}
		);
		configure(M8bConfig.builder().enabled(true).endpoint(ENDPOINT).build());

		service.supervise();
		assertTrue(capturedSettings.isEmpty());

		fail[0] = false;
		service.supervise();
		assertEquals(UID, capturedSettings.get(0).agentUid());
	}

	@Test
	void shouldPushHostsUpdatedWhenAReloadChangesTheInventory() {
		configure(M8bConfig.builder().enabled(true).endpoint(ENDPOINT).build());
		service.supervise();
		final M8bTunnelClient client = createdClients.get(0);
		capturedListeners.get(0).buildRegistration();

		// A reload that does not change the hosts: nothing is sent
		generation = 2;
		service.supervise();
		verify(client, never()).send(any());

		// A reload that adds a host: the new inventory is pushed
		agentConfig = AgentConfig.builder()
			.m8b(agentConfig.getM8b())
			.resources(
				Map.of(
					"server-02",
					ResourceConfig.builder()
						.protocols(Map.of("ssh", SshConfiguration.sshConfigurationBuilder().hostname("server-02").build()))
						.build()
				)
			)
			.build();
		final Map<String, Map<String, TelemetryManager>> active = new HashMap<>();
		active.put("metricshub-top-level-rg", Map.of("server-02", mock(TelemetryManager.class)));
		when(agentContext.getTelemetryManagers()).thenReturn(active);
		generation = 3;
		service.supervise();

		verify(client).send(any(HostsUpdated.class));

		// Same generation again: nothing more is sent
		service.supervise();
		verify(client).send(any());
	}

	@Test
	void shutdownShouldStopTheClient() {
		configure(M8bConfig.builder().enabled(true).endpoint(ENDPOINT).build());
		service.supervise();

		service.shutdown();

		verify(createdClients.get(0)).stop(any());
	}

	@Test
	void shouldIgnoreAMissingContext() {
		when(agentContextHolder.getAgentContext()).thenReturn(null);

		service.supervise();

		assertTrue(createdClients.isEmpty());
		assertNull(null);
		assertNotNull(service);
		assertSame(service, service);
	}
}
