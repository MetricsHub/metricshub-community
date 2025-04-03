package org.metricshub.extension.win.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;
import org.metricshub.extension.win.WmiTestConfiguration;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class WmiSourceProcessorTest {

	private static final String CONNECTOR_ID = "connector_id";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final char[] PASSWORD = "pwd".toCharArray();
	private static final String USERNAME = "user";
	private static final String WMI_QUERY = "SELECT Name FROM Win32_Process";

	@Mock
	IWinRequestExecutor winRequestExecutorMock;

	@Mock
	Function<TelemetryManager, IWinConfiguration> configurationRetrieverMock;

	WmiSourceProcessor wmiSourceProcessor;

	@BeforeEach
	void setup() {
		wmiSourceProcessor = new WmiSourceProcessor(winRequestExecutorMock, configurationRetrieverMock, CONNECTOR_ID);
	}

	@Test
	void testProcessWmiSourceNoNamespace() {
		final WmiSource wmiSource = WmiSource
			.builder()
			.query(WMI_QUERY)
			.namespace(MetricsHubConstants.AUTOMATIC_NAMESPACE)
			.build();
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(HOST_NAME + "\\" + USERNAME)
			.password(PASSWORD)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
					.build()
			)
			.build();
		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);
		assertEquals(SourceTable.empty(), wmiSourceProcessor.process(wmiSource, telemetryManager));
	}

	@Test
	void testProcessWmiSourceOk() throws Exception {
		final WmiSource wmiSource = WmiSource
			.builder()
			.query(WMI_QUERY)
			.namespace(MetricsHubConstants.WMI_DEFAULT_NAMESPACE)
			.build();
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(HOST_NAME + "\\" + USERNAME)
			.password(PASSWORD)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
					.build()
			)
			.build();
		final List<List<String>> expected = Arrays.asList(
			Arrays.asList("1.1", "0|4587"),
			Arrays.asList("1.2", "2|4587"),
			Arrays.asList("1.3", "1|4587")
		);
		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);
		doReturn(expected)
			.when(winRequestExecutorMock)
			.executeWmi(HOST_NAME, wmiConfiguration, WMI_QUERY, MetricsHubConstants.WMI_DEFAULT_NAMESPACE);

		assertEquals(
			SourceTable.builder().table(expected).build(),
			wmiSourceProcessor.process(wmiSource, telemetryManager)
		);
	}

	@Test
	void testProcessWmiSourceClientException() throws ClientException {
		final WmiSource wmiSource = WmiSource.builder().query(WMI_QUERY).build();
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(HOST_NAME + "\\" + USERNAME)
			.password(PASSWORD)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
					.build()
			)
			.build();
		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);
		doThrow(new ClientException())
			.when(winRequestExecutorMock)
			.executeWmi(HOST_NAME, wmiConfiguration, WMI_QUERY, MetricsHubConstants.WMI_DEFAULT_NAMESPACE);
		assertEquals(SourceTable.empty(), wmiSourceProcessor.process(wmiSource, telemetryManager));
	}

	@Test
	void testProcessWmiSourceNull() {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(HOST_NAME + "\\" + USERNAME)
			.password(PASSWORD)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
					.build()
			)
			.build();

		assertEquals(SourceTable.empty(), wmiSourceProcessor.process((WmiSource) null, telemetryManager));
	}

	@Test
	void testProcessWmiSourceButWmiNotConfigured() {
		final WmiSource wmiSource = WmiSource.builder().query(WMI_QUERY).build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Collections.emptyMap())
					.build()
			)
			.build();

		doReturn(null).when(configurationRetrieverMock).apply(telemetryManager);
		assertEquals(SourceTable.empty(), wmiSourceProcessor.process(wmiSource, telemetryManager));
	}
}
