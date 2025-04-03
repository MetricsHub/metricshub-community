package org.metricshub.extension.win.detection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WmiTestConfiguration;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.helpers.LocalOsHandler;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class WinServiceCriterionProcessorTest {

	@Mock
	WmiDetectionService wmiDetectionServiceMock;

	@Mock
	Function<TelemetryManager, IWinConfiguration> configurationRetrieverMock;

	@InjectMocks
	WinServiceCriterionProcessor winServiceCriterionProcessor;

	private static final char[] PASSWORD = "pwd".toCharArray();
	private static final String USERNAME = "user";
	private static final String SERVICE_NAME = "TWGIPC";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();

	@Test
	void testProcessServiceCheckOsNull() {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(15L)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
					.build()
			)
			.build();

		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);

		final ServiceCriterion serviceCriterion = new ServiceCriterion();
		serviceCriterion.setName(SERVICE_NAME);

		final CriterionTestResult criterionTestResult = winServiceCriterionProcessor.process(
			serviceCriterion,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertTrue(criterionTestResult.getMessage().contains("Host OS is not Windows"));
		assertNull(criterionTestResult.getResult());
	}

	@Test
	void testProcessServiceCheckServiceNull() {
		final ServiceCriterion serviceCriterion = null;
		assertTrue(
			winServiceCriterionProcessor.process(serviceCriterion, null).getMessage().contains("Malformed Service criterion.")
		);
	}

	@Test
	void testProcessServiceCheckProtocolNull() {
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(HOST_NAME).hostId(HOST_NAME).configurations(Map.of()).build()
			)
			.build();

		doReturn(null).when(configurationRetrieverMock).apply(telemetryManager);

		final ServiceCriterion serviceCriterion = new ServiceCriterion();
		serviceCriterion.setName(SERVICE_NAME);

		assertTrue(
			winServiceCriterionProcessor
				.process(serviceCriterion, telemetryManager)
				.getMessage()
				.contains("Neither WMI nor WinRM credentials are configured for this host.")
		);
	}

	@Test
	void testProcessServiceCheckOsNotWindows() {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(15L)
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

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::isWindows).thenReturn(false);

			final ServiceCriterion serviceCriterion = new ServiceCriterion();
			serviceCriterion.setName(SERVICE_NAME);

			final CriterionTestResult criterionTestResult = winServiceCriterionProcessor.process(
				serviceCriterion,
				telemetryManager
			);

			assertNotNull(criterionTestResult);
			assertFalse(criterionTestResult.isSuccess());
			assertNull(criterionTestResult.getResult());
		}
	}

	@Test
	void testProcessServiceNameBlank() {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(15L)
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

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::isWindows).thenReturn(true);

			final ServiceCriterion serviceCriterion = new ServiceCriterion();
			serviceCriterion.setName("");

			final CriterionTestResult criterionTestResult = winServiceCriterionProcessor.process(
				serviceCriterion,
				telemetryManager
			);

			assertNotNull(criterionTestResult);
			assertTrue(criterionTestResult.isSuccess());
			assertNotNull(criterionTestResult.getResult());
		}
	}

	@Test
	void testProcessServiceRunning() {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(15L)
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

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::isWindows).thenReturn(true);

			final ServiceCriterion serviceCriterion = new ServiceCriterion();
			serviceCriterion.setName(SERVICE_NAME);

			doReturn(CriterionTestResult.success(serviceCriterion, SERVICE_NAME + ";running"))
				.when(wmiDetectionServiceMock)
				.performDetectionTest(any(), any(), any());

			final CriterionTestResult criterionTestResult = winServiceCriterionProcessor.process(
				serviceCriterion,
				telemetryManager
			);

			assertNotNull(criterionTestResult);
			assertTrue(criterionTestResult.isSuccess());
			assertNotNull(criterionTestResult.getResult());
		}
	}

	@Test
	void testProcessServiceDown() {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(15L)
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

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::isWindows).thenReturn(true);

			final ServiceCriterion serviceCriterion = new ServiceCriterion();
			serviceCriterion.setName(SERVICE_NAME);

			doReturn(CriterionTestResult.success(serviceCriterion, SERVICE_NAME + ";down"))
				.when(wmiDetectionServiceMock)
				.performDetectionTest(any(), any(), any());

			final CriterionTestResult criterionTestResult = winServiceCriterionProcessor.process(
				serviceCriterion,
				telemetryManager
			);

			assertNotNull(criterionTestResult);
			assertFalse(criterionTestResult.isSuccess());
			assertNotNull(criterionTestResult.getResult());
		}
	}

	@Test
	void testProcessServiceNullResult() {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(15L)
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

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::isWindows).thenReturn(true);

			final ServiceCriterion serviceCriterion = new ServiceCriterion();
			serviceCriterion.setName(SERVICE_NAME);

			doReturn(CriterionTestResult.success(serviceCriterion, null))
				.when(wmiDetectionServiceMock)
				.performDetectionTest(any(), any(), any());

			final CriterionTestResult criterionTestResult = winServiceCriterionProcessor.process(
				serviceCriterion,
				telemetryManager
			);

			assertNotNull(criterionTestResult);
			assertFalse(criterionTestResult.isSuccess());
			assertNotNull(criterionTestResult.getResult());
		}
	}

	@Test
	void testProcessServiceQueryFails() {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(15L)
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

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::isWindows).thenReturn(true);

			final ServiceCriterion serviceCriterion = new ServiceCriterion();
			serviceCriterion.setName(SERVICE_NAME);

			doReturn(CriterionTestResult.error(serviceCriterion, "error"))
				.when(wmiDetectionServiceMock)
				.performDetectionTest(any(), any(), any());

			final CriterionTestResult criterionTestResult = winServiceCriterionProcessor.process(
				serviceCriterion,
				telemetryManager
			);

			assertNotNull(criterionTestResult);
			assertFalse(criterionTestResult.isSuccess());
			assertNull(criterionTestResult.getResult());
		}
	}
}
