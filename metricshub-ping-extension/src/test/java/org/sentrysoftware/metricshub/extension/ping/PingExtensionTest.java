package org.sentrysoftware.metricshub.extension.ping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType.HOST;

import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class PingExtensionTest {

	@Mock
	private PingRequestExecutor pingRequestExecutorMock;

	@InjectMocks
	private PingExtension pingExtension;

	private TelemetryManager telemetryManager;

	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final String CONNECTOR_ID = "connector";

	/**
	 * Creates a TelemetryManager instance with a Ping configuration.
	 */
	void initPing() {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();

		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final PingConfiguration pingConfiguration = PingConfiguration.builder().hostname(HOST_NAME).build();

		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(CONNECTOR_ID, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		telemetryManager =
			TelemetryManager
				.builder()
				.monitors(monitors)
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(HOST_NAME)
						.hostId(HOST_NAME)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(PingConfiguration.class, pingConfiguration))
						.build()
				)
				.connectorStore(connectorStore)
				.build();
	}

	@Test
	void testCheckPingDown() throws UnknownHostException {
		initPing();

		// Mock false protocol health check response
		doReturn(false).when(pingRequestExecutorMock).ping(anyString(), anyInt());

		Optional<Boolean> result = pingExtension.checkProtocol(telemetryManager);

		// Assert the result
		assertFalse(result.get());
	}

	@Test
	void testCheckPingUp() throws UnknownHostException {
		initPing();

		// Mock ICMP Ping protocol health check response
		doReturn(true).when(pingRequestExecutorMock).ping(anyString(), anyInt());

		Optional<Boolean> result = pingExtension.checkProtocol(telemetryManager);

		// Assert the result
		assertTrue(result.get());
	}

	@Test
	void testIsValidConfiguration() {
		assertTrue(pingExtension.isValidConfiguration(PingConfiguration.builder().build()));
		assertFalse(
			pingExtension.isValidConfiguration(
				new IConfiguration() {
					@Override
					public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {}

					@Override
					public String getHostname() {
						return null;
					}

					@Override
					public void setHostname(String hostname) {}
				}
			)
		);
	}

	@Test
	void testGetSupportedSources() {
		assertTrue(pingExtension.getSupportedSources().isEmpty());
	}

	@Test
	void testGetSupportedCriteria() {
		assertTrue(pingExtension.getSupportedCriteria().isEmpty());
	}

	@Test
	void testGetConfigurationToSourceMapping() {
		assertTrue(pingExtension.getConfigurationToSourceMapping().isEmpty());
	}

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(pingExtension.isSupportedConfigurationType("ping"));
		assertFalse(pingExtension.isSupportedConfigurationType("snmp"));
	}

	@Test
	void testBuildConfiguration() throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		configuration.set("maxAttempts", new IntNode(443));
		configuration.set("timeout", new TextNode("120"));

		assertEquals(
			PingConfiguration.builder().timeout(120L).build(),
			pingExtension.buildConfiguration("ping", configuration, value -> value)
		);

		assertEquals(
			PingConfiguration.builder().timeout(120L).build(),
			pingExtension.buildConfiguration("ping", configuration, null)
		);
	}

	@Test
	void testProcessCriterion() {
		assertEquals(
			CriterionTestResult.empty(),
			pingExtension.processCriterion(new HttpCriterion(), CONNECTOR_ID, telemetryManager)
		);
	}

	@Test
	void testProcessSource() {
		assertEquals(SourceTable.empty(), pingExtension.processSource(new HttpSource(), CONNECTOR_ID, telemetryManager));
	}
}
