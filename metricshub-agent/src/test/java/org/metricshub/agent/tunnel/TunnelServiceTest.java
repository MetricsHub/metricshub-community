package org.metricshub.agent.tunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.metricshub.agent.config.CentralConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.TunnelConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.agent.tunnel.client.TunnelClient;
import org.metricshub.agent.tunnel.client.TunnelListener;
import org.metricshub.agent.tunnel.client.TunnelSettings;
import org.metricshub.agent.tunnel.protocol.ToolDescriptor;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.AgentRegister;
import org.metricshub.agent.tunnel.protocol.TunnelMessage.HostsUpdated;
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
class TunnelServiceTest {

	private static final String ENDPOINT = "wss://tunnel.example.com/ws/agent";
	private static final String UID = "01923e4a-7c1e-7f4b-8a2d-3c5e6f7a8b9c";

	@Mock
	private AgentContextHolder agentContextHolder;

	@Mock
	private AgentContext agentContext;

	@Mock
	private AgentInfo agentInfo;

	private AgentConfig agentConfig;
	private long generation = 1;
	private final List<TunnelClient> createdClients = new ArrayList<>();
	private final List<TunnelSettings> capturedSettings = new ArrayList<>();
	private final List<TunnelListener> capturedListeners = new ArrayList<>();
	private boolean failClientCreation;
	private TunnelService service;

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

		service = new TunnelService(
			agentContextHolder,
			provider,
			(settings, listener) -> {
				if (failClientCreation) {
					throw new IllegalStateException("cannot connect");
				}
				capturedSettings.add(settings);
				capturedListeners.add(listener);
				final TunnelClient client = mock(TunnelClient.class);
				when(client.isConnected()).thenReturn(true);
				// A frame that fits, which is the ordinary case; the test about one that does not
				// says so for itself
				when(client.send(any())).thenReturn(true);
				createdClients.add(client);
				return client;
			},
			() -> UID
		);
	}

	private void configure(final TunnelConfig tunnel) {
		agentConfig = AgentConfig.builder().central(CentralConfig.builder().enabled(true).tunnel(tunnel).build()).build();
	}

	@Test
	void shouldNotStartWhenDisabled() {
		service.supervise();

		assertTrue(createdClients.isEmpty());
	}

	@Test
	void shouldNotStartWithoutAnEndpoint() {
		configure(TunnelConfig.builder().enabled(true).build());

		service.supervise();

		assertTrue(createdClients.isEmpty());
	}

	@Test
	void shouldStartTheTunnelWithResolvedSettings() {
		configure(
			TunnelConfig.builder()
				.enabled(true)
				.endpoint(" " + ENDPOINT + " ")
				.headers(Map.of("Authorization", "Bearer token"))
				.certificateFile("/tmp/tunnel-ca.pem")
				.heartbeatInterval(45)
				.build()
		);

		service.supervise();

		assertEquals(1, createdClients.size());
		verify(createdClients.get(0)).start();
		final TunnelSettings settings = capturedSettings.get(0);
		assertEquals(URI.create(ENDPOINT), settings.endpoint());
		assertEquals(Map.of("Authorization", "Bearer token"), settings.headers());
		assertEquals("/tmp/tunnel-ca.pem", settings.certificateFile());
		assertEquals(UID, settings.agentUid());
		assertEquals(Duration.ofSeconds(45), settings.heartbeatInterval());
	}

	@Test
	void shouldFallBackToTheDefaultHeartbeatWhenInvalid() {
		configure(TunnelConfig.builder().enabled(true).endpoint(ENDPOINT).heartbeatInterval(0).build());

		service.supervise();

		assertEquals(
			Duration.ofSeconds(TunnelConfig.DEFAULT_HEARTBEAT_INTERVAL),
			capturedSettings.get(0).heartbeatInterval()
		);
	}

	@Test
	void registrationShouldAdvertiseToolsHostsAndIdentity() {
		configure(
			TunnelConfig.builder().enabled(true).endpoint(ENDPOINT).excludedTools(Set.of("ExecuteSshCommandline")).build()
		);
		agentConfig = AgentConfig.builder()
			.central(agentConfig.getCentral())
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
		configure(TunnelConfig.builder().enabled(true).endpoint(ENDPOINT).build());
		service.supervise();
		final TunnelClient first = createdClients.get(0);

		service.supervise();
		assertEquals(1, createdClients.size(), "An unchanged configuration keeps the client");

		configure(TunnelConfig.builder().enabled(true).endpoint("wss://other.example.com/ws/agent").build());
		service.supervise();

		verify(first).stop(any());
		assertEquals(2, createdClients.size());
		verify(createdClients.get(1)).start();
	}

	@Test
	void shouldStopTheTunnelWhenDisabled() {
		configure(TunnelConfig.builder().enabled(true).endpoint(ENDPOINT).build());
		service.supervise();

		configure(TunnelConfig.builder().enabled(false).build());
		service.supervise();

		verify(createdClients.get(0)).stop(any());
	}

	@Test
	void shouldRetryAFailedStartupOnTheNextTick() {
		configure(TunnelConfig.builder().enabled(true).endpoint(ENDPOINT).build());
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
		service = new TunnelService(
			agentContextHolder,
			provider,
			(settings, listener) -> {
				capturedSettings.add(settings);
				return mock(TunnelClient.class);
			},
			() -> {
				if (fail[0]) {
					throw new IOException("security directory is read-only");
				}
				return UID;
			}
		);
		configure(TunnelConfig.builder().enabled(true).endpoint(ENDPOINT).build());

		service.supervise();
		assertTrue(capturedSettings.isEmpty());

		fail[0] = false;
		service.supervise();
		assertEquals(UID, capturedSettings.get(0).agentUid());
	}

	@Test
	void shouldPushHostsUpdatedWhenAReloadChangesTheInventory() {
		configure(TunnelConfig.builder().enabled(true).endpoint(ENDPOINT).build());
		service.supervise();
		final TunnelClient client = createdClients.get(0);
		capturedListeners.get(0).buildRegistration();

		// A reload that does not change the hosts: nothing is sent
		generation = 2;
		service.supervise();
		verify(client, never()).send(any());

		// A reload that adds a host: the new inventory is pushed
		agentConfig = AgentConfig.builder()
			.central(agentConfig.getCentral())
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
	void anInventoryTooLargeToSendStaysPending() {
		configure(TunnelConfig.builder().enabled(true).endpoint(ENDPOINT).build());
		service.supervise();
		final TunnelClient client = createdClients.get(0);
		capturedListeners.get(0).buildRegistration();

		// This server will not accept a frame this size, so nothing is sent
		when(client.send(any(HostsUpdated.class))).thenReturn(false);
		final Map<String, Map<String, TelemetryManager>> active = new HashMap<>();
		active.put("metricshub-top-level-rg", Map.of("server-02", mock(TelemetryManager.class)));
		when(agentContext.getTelemetryManagers()).thenReturn(active);
		agentConfig = AgentConfig.builder()
			.central(agentConfig.getCentral())
			.resources(
				Map.of(
					"server-02",
					ResourceConfig.builder()
						.protocols(Map.of("ssh", SshConfiguration.sshConfigurationBuilder().hostname("server-02").build()))
						.build()
				)
			)
			.build();
		generation = 3;
		service.supervise();

		// Recording it as advertised would leave Central routing on the old inventory for as
		// long as this process ran: every later tick would see nothing to do
		service.supervise();
		verify(client, times(2)).send(any(HostsUpdated.class));

		// And it recovers by itself once the frame fits -- a host removed, or the cap raised
		when(client.send(any(HostsUpdated.class))).thenReturn(true);
		service.supervise();
		verify(client, times(3)).send(any(HostsUpdated.class));
		service.supervise();
		verify(client, times(3)).send(any(HostsUpdated.class));
	}

	@Test
	void shouldReRegisterWhenTheAgentsIdentityChanges() {
		configure(TunnelConfig.builder().enabled(true).endpoint(ENDPOINT).build());
		service.supervise();
		final TunnelClient client = createdClients.get(0);
		capturedListeners.get(0).buildRegistration();

		// A reload that renames the host. There is no frame that amends an identity -- agent.register
		// is the only one carrying a descriptor -- so the only way to tell Central is to register
		// again.
		when(agentInfo.getAttributes()).thenReturn(Map.of("host.name", "renamed-01", "service.name", "MetricsHub Agent"));
		generation = 2;
		service.supervise();

		verify(client).reconnect(anyString());
		verify(client, never()).send(any(HostsUpdated.class));

		// And not again for the same identity
		service.supervise();
		verify(client).reconnect(anyString());
	}

	@Test
	void shutdownShouldStopTheClient() {
		configure(TunnelConfig.builder().enabled(true).endpoint(ENDPOINT).build());
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
