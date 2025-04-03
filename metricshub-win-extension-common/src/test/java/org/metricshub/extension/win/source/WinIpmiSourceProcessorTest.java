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
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class WinIpmiSourceProcessorTest {

	private static final String CONNECTOR_ID = "connector_id";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final char[] PASSWORD = "pwd".toCharArray();
	private static final String USERNAME = "user";

	@Mock
	IWinRequestExecutor winRequestExecutorMock;

	@Mock
	Function<TelemetryManager, IWinConfiguration> configurationRetrieverMock;

	WinIpmiSourceProcessor winIpmiSourceProcessor;

	@BeforeEach
	void setup() {
		winIpmiSourceProcessor =
			new WinIpmiSourceProcessor(winRequestExecutorMock, configurationRetrieverMock, CONNECTOR_ID);
	}

	@Test
	void testProcessWindowsIpmiSource() throws Exception {
		final IWinConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(120L)
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_NAME)
			.hostId(HOST_NAME)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Collections.singletonMap(WmiTestConfiguration.class, wmiConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);

		final List<List<String>> wmiResult1 = Arrays.asList(Arrays.asList("IdentifyingNumber", "Name", "Vendor"));
		doReturn(wmiResult1)
			.when(winRequestExecutorMock)
			.executeWmi(
				HOST_NAME,
				wmiConfiguration,
				"SELECT IdentifyingNumber,Name,Vendor FROM Win32_ComputerSystemProduct",
				"root/cimv2"
			);

		final List<List<String>> wmiResult2 = Arrays.asList(
			Arrays.asList("2", "20", "sensorName(sensorId):description for deviceId", "10", "15", "2", "0", "30", "25")
		);
		doReturn(wmiResult2)
			.when(winRequestExecutorMock)
			.executeWmi(
				HOST_NAME,
				wmiConfiguration,
				"SELECT BaseUnits,CurrentReading,Description,LowerThresholdCritical,LowerThresholdNonCritical,SensorType,UnitModifier,UpperThresholdCritical,UpperThresholdNonCritical FROM NumericSensor",
				"root/hardware"
			);

		final List<List<String>> wmiResult3 = Arrays.asList(
			Arrays.asList("state", "sensorName(sensorId):description for deviceType deviceId")
		);
		doReturn(wmiResult3)
			.when(winRequestExecutorMock)
			.executeWmi(HOST_NAME, wmiConfiguration, "SELECT CurrentState,Description FROM Sensor", "root/hardware");

		final List<List<String>> expected = Arrays.asList(
			Arrays.asList("FRU", "Vendor", "Name", "IdentifyingNumber"),
			Arrays.asList("Temperature", "sensorId", "sensorName", "deviceId", "20.0", "25.0", "30.0"),
			Arrays.asList("deviceType", "deviceId", "deviceType deviceId", "", "", "", "sensorName=state")
		);
		SourceTable result = winIpmiSourceProcessor.process(new IpmiSource(), telemetryManager);
		assertEquals(SourceTable.builder().table(expected).build(), result);
	}

	@Test
	void testProcessWindowsIpmiSourceWmiProtocolNull() throws Exception {
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_NAME)
			.hostId(HOST_NAME)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Map.of())
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		doReturn(null).when(configurationRetrieverMock).apply(telemetryManager);

		assertEquals(SourceTable.empty(), winIpmiSourceProcessor.process(new IpmiSource(), telemetryManager));
	}

	@Test
	void testProcessWindowsIpmiSourceWmiException() throws Exception {
		final IWinConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(120L)
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_NAME)
			.hostId(HOST_NAME)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Collections.singletonMap(WmiTestConfiguration.class, wmiConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);

		doThrow(ClientException.class)
			.when(winRequestExecutorMock)
			.executeWmi(
				HOST_NAME,
				wmiConfiguration,
				"SELECT IdentifyingNumber,Name,Vendor FROM Win32_ComputerSystemProduct",
				"root/cimv2"
			);
		assertEquals(SourceTable.empty(), winIpmiSourceProcessor.process(new IpmiSource(), telemetryManager));
	}
}
