package org.metricshub.agent.opamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.OpAmpConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.opamp.client.OpampClient;
import org.metricshub.opamp.client.OpampClientSettings;
import org.metricshub.web.AgentContextHolder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpAmpServiceTest {

	private static final String ENDPOINT = "https://opamp.example.com/v1/opamp";

	@Mock
	private AgentContextHolder agentContextHolder;

	@Mock
	private AgentContext agentContext;

	@Mock
	private AgentInfo agentInfo;

	@Mock
	private OpampClient client;

	private AgentConfig agentConfig;
	private AtomicReference<OpampClientSettings> capturedSettings;
	private OpAmpService opAmpService;
	private int factoryInvocations;

	@BeforeEach
	void setUp() {
		agentConfig = AgentConfig.builder().build();
		capturedSettings = new AtomicReference<>();
		factoryInvocations = 0;

		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getAgentConfig()).thenAnswer(invocation -> agentConfig);
		when(agentContext.getAgentInfo()).thenReturn(agentInfo);
		when(agentInfo.getAttributes()).thenReturn(
			Map.of("service.name", "MetricsHub Agent", "version", "3.9.05", "host.name", "test-host")
		);
		when(client.isStarted()).thenReturn(true);

		opAmpService = new OpAmpService(agentContextHolder, settings -> {
			capturedSettings.set(settings);
			factoryInvocations++;
			return client;
		});
	}

	private static OpAmpConfig enabledConfig(final String endpoint) {
		return OpAmpConfig.builder().enabled(true).endpoint(endpoint).build();
	}

	@Test
	void disabledConfigurationShouldNotStartTheClient() {
		opAmpService.supervise();

		assertEquals(0, factoryInvocations);
	}

	@Test
	void enabledConfigurationShouldStartTheClientOnce() {
		agentConfig.setOpamp(enabledConfig(ENDPOINT));

		opAmpService.supervise();
		opAmpService.supervise();

		assertEquals(1, factoryInvocations);
		verify(client, times(1)).start();
		// Description and health are reported before start and refreshed on every tick
		verify(client, times(3)).setAgentDescription(any());
		verify(client, times(3)).setHealth(any());
		assertEquals(URI.create(ENDPOINT), capturedSettings.get().getEndpoint());
	}

	@Test
	void enabledConfigurationWithoutEndpointShouldNotStartTheClient() {
		agentConfig.setOpamp(OpAmpConfig.builder().enabled(true).build());

		opAmpService.supervise();

		assertEquals(0, factoryInvocations);
	}

	@Test
	void configurationChangeShouldRebuildTheClient() {
		agentConfig.setOpamp(enabledConfig(ENDPOINT));
		opAmpService.supervise();

		agentConfig.setOpamp(enabledConfig("https://other.example.com/v1/opamp"));
		opAmpService.supervise();

		assertEquals(2, factoryInvocations);
		verify(client, times(1)).stop("OpAMP configuration changed");
		assertEquals(URI.create("https://other.example.com/v1/opamp"), capturedSettings.get().getEndpoint());
	}

	@Test
	void disablingShouldStopTheClient() {
		agentConfig.setOpamp(enabledConfig(ENDPOINT));
		opAmpService.supervise();

		agentConfig.setOpamp(OpAmpConfig.builder().enabled(false).build());
		opAmpService.supervise();

		assertEquals(1, factoryInvocations);
		verify(client, times(1)).stop("OpAMP configuration changed");
	}

	@Test
	void shutdownShouldStopTheClient() {
		agentConfig.setOpamp(enabledConfig(ENDPOINT));
		opAmpService.supervise();

		opAmpService.shutdown();

		verify(client, times(1)).stop("Agent shutting down");
	}

	@Test
	void clientStartFailureShouldBeContained() {
		agentConfig.setOpamp(enabledConfig(ENDPOINT));
		final OpAmpService failingService = new OpAmpService(agentContextHolder, settings -> {
			throw new IllegalStateException("Simulated client creation failure");
		});

		failingService.supervise();

		verify(client, never()).start();
	}

	@Test
	void failedStartShouldReleaseTheClientResources() {
		agentConfig.setOpamp(enabledConfig(ENDPOINT));
		org.mockito.Mockito.doThrow(new IllegalStateException("Simulated start failure")).when(client).start();

		opAmpService.supervise();

		verify(client, times(1)).stop("OpAMP client startup failed");
	}

	@Test
	void startupFailureShouldBeRetriedOnTheNextTick() {
		agentConfig.setOpamp(enabledConfig(ENDPOINT));
		final AtomicReference<Boolean> failFirstAttempt = new AtomicReference<>(true);
		final OpAmpService retryingService = new OpAmpService(agentContextHolder, settings -> {
			factoryInvocations++;
			if (Boolean.TRUE.equals(failFirstAttempt.getAndSet(false))) {
				throw new IllegalStateException("Simulated transient startup failure");
			}
			return client;
		});

		retryingService.supervise();
		// The transient failure is retried with the unchanged configuration
		retryingService.supervise();

		assertEquals(2, factoryInvocations);
		verify(client, times(1)).start();
	}

	@Test
	void invalidDurationsShouldFallBackToDefaults() {
		final OpAmpConfig config = OpAmpConfig.builder()
			.enabled(true)
			.endpoint(ENDPOINT)
			.pollInterval(0)
			.requestTimeout(-5)
			.build();

		final OpampClientSettings settings = opAmpService.buildSettings(config);

		assertEquals(Duration.ofSeconds(OpAmpConfig.DEFAULT_POLL_INTERVAL), settings.getPollInterval());
		assertEquals(Duration.ofSeconds(OpAmpConfig.DEFAULT_REQUEST_TIMEOUT), settings.getRequestTimeout());
	}

	@Test
	void upgradeAdapterSinkShouldBeReboundOnEveryClientBuild() {
		final org.metricshub.agent.upgrade.UpgradeManager upgradeManager = mock(
			org.metricshub.agent.upgrade.UpgradeManager.class
		);
		final org.metricshub.agent.upgrade.opamp.OpampUpgradeAdapter adapter =
			new org.metricshub.agent.upgrade.opamp.OpampUpgradeAdapter(upgradeManager);
		final org.metricshub.opamp.client.packages.PackageStatusSink clientSink = mock(
			org.metricshub.opamp.client.packages.PackageStatusSink.class
		);
		when(client.packageStatusSink()).thenReturn(clientSink);

		final OpAmpService service = new OpAmpService(agentContextHolder, adapter, settings -> {
			factoryInvocations++;
			return client;
		});
		agentConfig.setOpamp(enabledConfig(ENDPOINT));

		service.supervise();

		verify(client).setPackagesHandler(adapter);
		verify(client).packageStatusSink();
	}

	@Test
	void packagesHandlerShouldNotBeRegisteredWhenUpgradesAreDisabled() {
		final org.metricshub.opamp.client.packages.OpampPackagesHandler handler = mock(
			org.metricshub.opamp.client.packages.OpampPackagesHandler.class
		);
		final OpAmpService service = new OpAmpService(agentContextHolder, handler, settings -> {
			factoryInvocations++;
			return client;
		});
		agentConfig.setOpamp(enabledConfig(ENDPOINT));
		agentConfig.setUpgrade(org.metricshub.agent.config.UpgradeConfig.builder().enabled(false).build());

		service.supervise();

		verify(client, never()).setPackagesHandler(any());
		verify(client, times(1)).start();
	}

	@Test
	void buildSettingsShouldMapTheConfiguration() {
		final OpAmpConfig config = OpAmpConfig.builder()
			.enabled(true)
			.endpoint(" " + ENDPOINT + " ")
			.headers(Map.of("Authorization", "Bearer token"))
			.certificateFile("/tmp/ca.pem")
			.pollInterval(60)
			.requestTimeout(15)
			.reportHealth(false)
			.build();

		final OpampClientSettings settings = opAmpService.buildSettings(config);

		assertEquals(URI.create(ENDPOINT), settings.getEndpoint());
		assertEquals(Map.of("Authorization", "Bearer token"), settings.getHeaders());
		assertEquals("/tmp/ca.pem", settings.getCertificateFile());
		assertEquals(Duration.ofSeconds(60), settings.getPollInterval());
		assertEquals(Duration.ofSeconds(15), settings.getRequestTimeout());
		assertEquals(false, settings.isReportHealth());
		assertEquals(
			List.of(OpAmpService.OPAMP_INSTANCE_UID_FILENAME),
			List.of(settings.getInstanceUidFile().getFileName().toString())
		);
	}
}
