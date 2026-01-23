package org.metricshub.engine.strategy.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.CONFIGURED_OS_NT_MESSAGE;
import static org.metricshub.engine.constants.Constants.CONFIGURED_OS_SOLARIS_MESSAGE;
import static org.metricshub.engine.constants.Constants.ERROR;
import static org.metricshub.engine.constants.Constants.EXECUTE_WMI_RESULT;
import static org.metricshub.engine.constants.Constants.FAILED_OS_DETECTION;
import static org.metricshub.engine.constants.Constants.HIGH_VERSION_NUMBER;
import static org.metricshub.engine.constants.Constants.HOST_ID;
import static org.metricshub.engine.constants.Constants.HTTP;
import static org.metricshub.engine.constants.Constants.IPMI_CONNECTION_SUCCESS_WITH_IPMI_OVER_LAN_MESSAGE;
import static org.metricshub.engine.constants.Constants.LIST_ALL_LINUX_PROCESSES_RESULT;
import static org.metricshub.engine.constants.Constants.LOCALHOST;
import static org.metricshub.engine.constants.Constants.LOW_VERSION_NUMBER;
import static org.metricshub.engine.constants.Constants.MANAGEMENT_CARD_HOST;
import static org.metricshub.engine.constants.Constants.MY_CONNECTOR_1_NAME;
import static org.metricshub.engine.constants.Constants.NO_RUNNING_PROCESS_MATCH_REGEX_MESSAGE;
import static org.metricshub.engine.constants.Constants.NO_TEST_WILL_BE_PERFORMED_AIX_MESSAGE;
import static org.metricshub.engine.constants.Constants.NO_TEST_WILL_BE_PERFORMED_MESSAGE;
import static org.metricshub.engine.constants.Constants.NO_TEST_WILL_BE_PERFORMED_REMOTELY_MESSAGE;
import static org.metricshub.engine.constants.Constants.NO_TEST_WILL_BE_PERFORMED_UNKNOWN_OS_MESSAGE;
import static org.metricshub.engine.constants.Constants.PROCESS_CRITERION_COMMAND_LINE;
import static org.metricshub.engine.constants.Constants.RESULT;
import static org.metricshub.engine.constants.Constants.RUNNING_PROCESS_MATCH_REGEX_MESSAGE;
import static org.metricshub.engine.constants.Constants.SUCCESSFUL_OS_DETECTION;
import static org.metricshub.engine.constants.Constants.SYSTEM_POWER_UP_MESSAGE;
import static org.metricshub.engine.constants.Constants.TEST;
import static org.metricshub.engine.constants.Constants.TEST_BODY;
import static org.metricshub.engine.constants.Constants.WBEM_QUERY;
import static org.metricshub.engine.constants.Constants.WEBM_CRITERION_SUCCESS_EXPECTED_RESULT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.DeviceTypeCriterion;
import org.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ProductRequirementsCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.utils.CriterionProcessVisitor;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * This is a test for {@link CriterionProcessor}
 */
@ExtendWith(MockitoExtension.class)
class CriterionProcessorTest {

	@Mock
	private ClientsExecutor clientsExecutorMock;

	@Mock
	private TelemetryManager telemetryManagerMock;

	@InjectMocks
	private CriterionProcessor criterionProcessor;

	private TelemetryManager telemetryManager;

	/**
	 * Initialize the {@link TelemetryManager} with a Linux host configuration
	 */
	private void initLinuxTestConfiguration() {
		final TestConfiguration testConfiguration = new TestConfiguration();

		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(LOCALHOST)
						.hostId(LOCALHOST)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(TestConfiguration.class, testConfiguration))
						.build()
				)
				.build();
	}

	/**
	 * Initialize the {@link TelemetryManager} with a Windows host configuration
	 */
	private void initWindowsTestConfiguration() {
		final TestConfiguration testConfiguration = new TestConfiguration();

		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(LOCALHOST)
						.hostId(LOCALHOST)
						.hostType(DeviceKind.WINDOWS)
						.configurations(Map.of(TestConfiguration.class, testConfiguration))
						.build()
				)
				.build();
	}

	/**
	 * Initialize the {@link TelemetryManager} with a OOB host configuration
	 */
	private void initIpmi() {
		final TestConfiguration ipmiConfiguration = new TestConfiguration();

		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(LOCALHOST)
						.hostId(LOCALHOST)
						.hostType(DeviceKind.OOB)
						.configurations(Map.of(TestConfiguration.class, ipmiConfiguration))
						.build()
				)
				.build();
	}

	@Test
	void testProcessSnmpGetCriterion() {
		initLinuxTestConfiguration();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn(Set.of(SnmpGetCriterion.class, SnmpGetNextCriterion.class))
			.when(protocolExtensionMock)
			.getSupportedCriteria();

		final SnmpGetCriterion snmpGetCriterion = SnmpGetCriterion.builder().oid("1.2.3.4.5.6").build();

		final CriterionTestResult expected = CriterionTestResult.builder().success(true).message("success").build();

		doReturn(expected)
			.when(protocolExtensionMock)
			.processCriterion(snmpGetCriterion, MY_CONNECTOR_1_NAME, telemetryManager);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);
		assertEquals(expected, criterionProcessor.process(snmpGetCriterion));
	}

	@Test
	void testProcessWmiCriterion() {
		initWindowsTestConfiguration();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn(Set.of(WmiCriterion.class)).when(protocolExtensionMock).getSupportedCriteria();

		final WmiCriterion wmiCriterion = WmiCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_SUCCESS_EXPECTED_RESULT)
			.build();

		final CriterionTestResult expected = CriterionTestResult.builder().success(true).message("success").build();

		doReturn(expected)
			.when(protocolExtensionMock)
			.processCriterion(wmiCriterion, MY_CONNECTOR_1_NAME, telemetryManager);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);
		assertEquals(expected, criterionProcessor.process(wmiCriterion));
	}

	@Test
	void testProcessWbemCriterion() {
		initWindowsTestConfiguration();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn(Set.of(WbemCriterion.class)).when(protocolExtensionMock).getSupportedCriteria();

		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_SUCCESS_EXPECTED_RESULT)
			.build();

		final CriterionTestResult expected = CriterionTestResult.builder().success(true).message("success").build();

		doReturn(expected)
			.when(protocolExtensionMock)
			.processCriterion(wbemCriterion, MY_CONNECTOR_1_NAME, telemetryManager);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);
		assertEquals(expected, criterionProcessor.process(wbemCriterion));
	}

	@Test
	void testProcessServiceCriterion() {
		initWindowsTestConfiguration();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn(Set.of(ServiceCriterion.class)).when(protocolExtensionMock).getSupportedCriteria();

		final ServiceCriterion serviceCriterion = ServiceCriterion.builder().name("TWGIPC").build();

		final CriterionTestResult expected = CriterionTestResult.builder().success(true).message("success").build();

		doReturn(expected)
			.when(protocolExtensionMock)
			.processCriterion(serviceCriterion, MY_CONNECTOR_1_NAME, telemetryManager);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);
		assertEquals(expected, criterionProcessor.process(serviceCriterion));
	}

	@Test
	void testProcessIpmiCriterion() {
		initWindowsTestConfiguration();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn(Set.of(IpmiCriterion.class)).when(protocolExtensionMock).getSupportedCriteria();

		final IpmiCriterion ipmiCriterion = new IpmiCriterion();

		final CriterionTestResult expected = CriterionTestResult.builder().success(true).message("success").build();

		doReturn(expected)
			.when(protocolExtensionMock)
			.processCriterion(ipmiCriterion, MY_CONNECTOR_1_NAME, telemetryManager);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);
		assertEquals(expected, criterionProcessor.process(ipmiCriterion));
	}

	@Test
	void testProcessCommandLineCriterion() {
		initWindowsTestConfiguration();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn(Set.of(CommandLineCriterion.class)).when(protocolExtensionMock).getSupportedCriteria();

		final CommandLineCriterion commandLineCriterion = CommandLineCriterion.builder().commandLine("show system").build();

		final CriterionTestResult expected = CriterionTestResult.builder().success(true).message("success").build();

		doReturn(expected)
			.when(protocolExtensionMock)
			.processCriterion(commandLineCriterion, MY_CONNECTOR_1_NAME, telemetryManager);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);
		assertEquals(expected, criterionProcessor.process(commandLineCriterion));
	}

	@Test
	void testProcessProcessWindows() throws Exception {
		initWindowsTestConfiguration();
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine(PROCESS_CRITERION_COMMAND_LINE);

		doReturn(HOST_ID).when(telemetryManagerMock).getHostname();
		doReturn(HostProperties.builder().isLocalhost(true).build()).when(telemetryManagerMock).getHostProperties();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = spy(ExtensionManager.class);

		doReturn(Optional.of(protocolExtensionMock)).when(extensionManager).findExtensionByType("wmi");

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::getOS).thenReturn(Optional.of(LocalOsHandler.WINDOWS));
			mockedLocalOSHandler.when(LocalOsHandler::getSystemOsVersion).thenReturn(Optional.of("5.1"));

			final CriterionTestResult expected = CriterionTestResult.builder().success(true).message("success").build();

			doReturn(expected).when(protocolExtensionMock).processCriterion(processCriterion, null, null);

			final CriterionProcessor criterionProcessor = new CriterionProcessor(
				clientsExecutorMock,
				telemetryManagerMock,
				MY_CONNECTOR_1_NAME,
				extensionManager
			);
			assertEquals(expected, criterionProcessor.process(processCriterion));
		}
	}

	@Test
	void testProcessSnmpGetNextCriterion() {
		initLinuxTestConfiguration();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn(Set.of(SnmpGetCriterion.class, SnmpGetNextCriterion.class))
			.when(protocolExtensionMock)
			.getSupportedCriteria();

		final SnmpGetNextCriterion snmpGetNextCriterion = SnmpGetNextCriterion.builder().oid("1.2.3.4.5").build();

		final CriterionTestResult expected = CriterionTestResult.builder().success(true).message("success").build();

		doReturn(expected)
			.when(protocolExtensionMock)
			.processCriterion(snmpGetNextCriterion, MY_CONNECTOR_1_NAME, telemetryManager);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);
		assertEquals(expected, criterionProcessor.process(snmpGetNextCriterion));
	}

	@Test
	void testProcessProcessProcessNull() {
		final ProcessCriterion processCriterion = null;

		assertEquals(CriterionTestResult.empty(), criterionProcessor.process(processCriterion));
	}

	@Test
	void testProcessProcessCommandLineEmpty() {
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine("");

		final CriterionTestResult criterionTestResult = criterionProcessor.process(processCriterion);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(NO_TEST_WILL_BE_PERFORMED_MESSAGE, criterionTestResult.getMessage());
		assertNull(criterionTestResult.getResult());
	}

	@Test
	void testProcessProcessNotLocalHost() {
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine(PROCESS_CRITERION_COMMAND_LINE);
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(HostProperties.builder().build())
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.build();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		final CriterionTestResult criterionTestResult = criterionProcessor.process(processCriterion);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(NO_TEST_WILL_BE_PERFORMED_REMOTELY_MESSAGE, criterionTestResult.getMessage());
		assertNull(criterionTestResult.getResult());
	}

	@Test
	void testProcessProcessUnknownOS() {
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine(PROCESS_CRITERION_COMMAND_LINE);

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::getOS).thenReturn(Optional.empty());

			final CriterionTestResult criterionTestResult = criterionProcessor.process(processCriterion);

			assertNotNull(criterionTestResult);
			assertTrue(criterionTestResult.isSuccess());
			assertEquals(NO_TEST_WILL_BE_PERFORMED_UNKNOWN_OS_MESSAGE, criterionTestResult.getMessage());
			assertNull(criterionTestResult.getResult());
		}
	}

	@Test
	void testProcessProcessLinuxNoProcess() {
		final ProcessCriterion process = new ProcessCriterion();
		process.setCommandLine(PROCESS_CRITERION_COMMAND_LINE);

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();
		doReturn(telemetryManager.getHostname()).when(telemetryManagerMock).getHostname();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		try (
			final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class);
			final MockedStatic<CriterionProcessVisitor> mockedCriterionProcessVisitorImpl = mockStatic(
				CriterionProcessVisitor.class
			)
		) {
			mockedLocalOSHandler.when(LocalOsHandler::getOS).thenReturn(Optional.of(LocalOsHandler.LINUX));
			mockedCriterionProcessVisitorImpl
				.when(CriterionProcessVisitor::listAllLinuxProcesses)
				.thenReturn(LIST_ALL_LINUX_PROCESSES_RESULT);

			final CriterionTestResult criterionTestResult = criterionProcessor.process(process);

			assertNotNull(criterionTestResult);
			assertFalse(criterionTestResult.isSuccess());
			assertEquals(NO_RUNNING_PROCESS_MATCH_REGEX_MESSAGE, criterionTestResult.getMessage());
			assertNull(criterionTestResult.getResult());
		}
	}

	@Test
	void testProcessProcessLinuxOK() {
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine(PROCESS_CRITERION_COMMAND_LINE);

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();
		doReturn(telemetryManager.getHostname()).when(telemetryManagerMock).getHostname();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		try (
			final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class);
			final MockedStatic<CriterionProcessVisitor> mockedCriterionProcessVisitorImpl = mockStatic(
				CriterionProcessVisitor.class
			)
		) {
			mockedLocalOSHandler.when(LocalOsHandler::getOS).thenReturn(Optional.of(LocalOsHandler.LINUX));
			mockedCriterionProcessVisitorImpl
				.when(CriterionProcessVisitor::listAllLinuxProcesses)
				.thenReturn(EXECUTE_WMI_RESULT);

			final CriterionTestResult criterionTestResult = criterionProcessor.process(processCriterion);

			assertNotNull(criterionTestResult);
			assertTrue(criterionTestResult.isSuccess());
			assertEquals(RUNNING_PROCESS_MATCH_REGEX_MESSAGE, criterionTestResult.getMessage());
			assertNull(criterionTestResult.getResult());
		}
	}

	@Test
	void testProcessProcessNotImplementedAixOK() {
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine(PROCESS_CRITERION_COMMAND_LINE);

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();
		doReturn(telemetryManager.getHostname()).when(telemetryManagerMock).getHostname();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::getOS).thenReturn(Optional.of(LocalOsHandler.AIX));

			final CriterionTestResult criterionTestResult = criterionProcessor.process(processCriterion);

			assertNotNull(criterionTestResult);
			assertTrue(criterionTestResult.isSuccess());
			assertEquals(NO_TEST_WILL_BE_PERFORMED_AIX_MESSAGE, criterionTestResult.getMessage());
			assertNull(criterionTestResult.getResult());
		}
	}

	@Test
	void testProcessDeviceTypeCriterion() {
		// Init configurations
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.NETWORK).build()
			)
			.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();

		final DeviceTypeCriterion deviceTypeCriterion = DeviceTypeCriterion.builder().build();
		// Init CriterionTestResult success and failure instances
		final CriterionTestResult successfulTestResult = CriterionTestResult
			.builder()
			.message(SUCCESSFUL_OS_DETECTION)
			.result(CONFIGURED_OS_NT_MESSAGE)
			.success(true)
			.criterion(deviceTypeCriterion)
			.build();

		final CriterionTestResult failedTestResult = CriterionTestResult
			.builder()
			.message(FAILED_OS_DETECTION)
			.result(CONFIGURED_OS_NT_MESSAGE)
			.success(false)
			.criterion(deviceTypeCriterion)
			.build();

		// Test configured NETWORK OS

		assertEquals(successfulTestResult, criterionProcessor.process(deviceTypeCriterion));

		// Include NETWORK OS

		deviceTypeCriterion.setKeep(Set.of(DeviceKind.NETWORK));
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		assertEquals(successfulTestResult, criterionProcessor.process(deviceTypeCriterion));

		deviceTypeCriterion.setKeep(Collections.emptySet());
		deviceTypeCriterion.setExclude(Set.of(DeviceKind.NETWORK));
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		assertEquals(failedTestResult, criterionProcessor.process(deviceTypeCriterion));

		// Test Linux OS

		// Exclude only Linux OS with empty keep set
		deviceTypeCriterion.setExclude(Set.of(DeviceKind.LINUX));
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		assertEquals(successfulTestResult, criterionProcessor.process(deviceTypeCriterion));

		// Include only Linux OS with empty keep set
		deviceTypeCriterion.setKeep(Set.of(DeviceKind.LINUX));
		deviceTypeCriterion.setExclude(Collections.emptySet());
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		criterionProcessor.process(deviceTypeCriterion);
		assertEquals(failedTestResult, criterionProcessor.process(deviceTypeCriterion));

		// Exclude only Linux with empty keep set
		deviceTypeCriterion.setKeep(Collections.emptySet());
		deviceTypeCriterion.setExclude(Set.of(DeviceKind.LINUX));
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		assertEquals(successfulTestResult, criterionProcessor.process(deviceTypeCriterion));

		// TEST SOLARIS OS

		// Prepare CriterionTestResult with specific SOLARIS CriterionTestResult instances
		successfulTestResult.setResult(CONFIGURED_OS_SOLARIS_MESSAGE);
		failedTestResult.setResult(CONFIGURED_OS_SOLARIS_MESSAGE);
		telemetryManager.setHostConfiguration(
			HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.SOLARIS).build()
		);

		// Exclude only SOLARIS OS
		deviceTypeCriterion.setKeep(Collections.emptySet());
		deviceTypeCriterion.setExclude(Set.of(DeviceKind.SOLARIS));
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		assertEquals(failedTestResult, criterionProcessor.process(deviceTypeCriterion));

		// Include only SOLARIS OS
		deviceTypeCriterion.setKeep(Set.of(DeviceKind.SOLARIS));
		deviceTypeCriterion.setExclude(Collections.emptySet());
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		assertEquals(successfulTestResult, criterionProcessor.process(deviceTypeCriterion));
	}

	@Test
	void testIsDeviceTypeIncluded() {
		// Create the device criterion instance
		final DeviceTypeCriterion deviceTypeCriterion = DeviceTypeCriterion.builder().build();

		// Prepare the device kind list
		final List<DeviceKind> deviceKindList = Arrays.asList(DeviceKind.STORAGE, DeviceKind.NETWORK, DeviceKind.LINUX);
		assertTrue(criterionProcessor.isDeviceKindIncluded(deviceKindList, deviceTypeCriterion));

		// Keep only Solaris OS
		deviceTypeCriterion.setKeep(Set.of(DeviceKind.SOLARIS));
		assertFalse(criterionProcessor.isDeviceKindIncluded(deviceKindList, deviceTypeCriterion));

		// Keep only Linux
		deviceTypeCriterion.setKeep(Set.of(DeviceKind.LINUX));
		assertTrue(criterionProcessor.isDeviceKindIncluded(deviceKindList, deviceTypeCriterion));

		// Exclude only Solaris and with empty keep set
		deviceTypeCriterion.setKeep(Collections.emptySet());
		deviceTypeCriterion.setExclude(Set.of(DeviceKind.SOLARIS));
		assertTrue(criterionProcessor.isDeviceKindIncluded(deviceKindList, deviceTypeCriterion));

		// Exclude only Linux
		deviceTypeCriterion.setExclude(Set.of(DeviceKind.LINUX));
		assertFalse(criterionProcessor.isDeviceKindIncluded(deviceKindList, deviceTypeCriterion));
	}

	@Test
	void testProcessHttpCriterion() throws IOException {
		final HttpCriterion httpCriterion = HttpCriterion
			.builder()
			.type(HTTP)
			.method(HttpMethod.GET)
			.url(TEST)
			.body(TEST_BODY)
			.resultContent(ResultContent.ALL)
			.expectedResult(RESULT)
			.errorMessage(ERROR)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_ID)
			.hostId(HOST_ID)
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
			.build();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final CriterionTestResult expected = CriterionTestResult.builder().success(true).result(RESULT).build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn(Set.of(HttpCriterion.class)).when(protocolExtensionMock).getSupportedCriteria();

		doReturn(expected)
			.when(protocolExtensionMock)
			.processCriterion(any(HttpCriterion.class), anyString(), any(TelemetryManager.class));

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(httpCriterion);

		assertEquals(RESULT, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessIPMIOutOfBand() throws Exception {
		initIpmi();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn(Set.of(IpmiCriterion.class)).when(protocolExtensionMock).getSupportedCriteria();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(MANAGEMENT_CARD_HOST)
					.hostType(DeviceKind.OOB)
					.hostname(MANAGEMENT_CARD_HOST)
					.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
					.build()
			)
			.build();

		final IpmiCriterion ipmiCriterion = IpmiCriterion.builder().build();

		CriterionTestResult result = CriterionTestResult
			.builder()
			.result(SYSTEM_POWER_UP_MESSAGE)
			.message(IPMI_CONNECTION_SUCCESS_WITH_IPMI_OVER_LAN_MESSAGE)
			.success(true)
			.build();

		doReturn(result).when(protocolExtensionMock).processCriterion(ipmiCriterion, MY_CONNECTOR_1_NAME, telemetryManager);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);

		assertEquals(
			CriterionTestResult
				.builder()
				.result(SYSTEM_POWER_UP_MESSAGE)
				.message(IPMI_CONNECTION_SUCCESS_WITH_IPMI_OVER_LAN_MESSAGE)
				.success(true)
				.criterion(ipmiCriterion)
				.build(),
			criterionProcessor.process(new IpmiCriterion())
		);
	}

	@Test
	void testProcessIPMIOutOfBandNullResult() throws Exception {
		initIpmi();

		final IProtocolExtension protocolExtensionMock = spy(IProtocolExtension.class);

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		doReturn(true)
			.when(protocolExtensionMock)
			.isValidConfiguration(telemetryManager.getHostConfiguration().getConfigurations().get(TestConfiguration.class));

		doReturn(Set.of(IpmiCriterion.class)).when(protocolExtensionMock).getSupportedCriteria();

		final IpmiCriterion ipmiCriterion = IpmiCriterion.builder().build();

		doReturn(null).when(protocolExtensionMock).processCriterion(ipmiCriterion, MY_CONNECTOR_1_NAME, telemetryManager);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);
		assertEquals(CriterionTestResult.empty(), criterionProcessor.process(new IpmiCriterion()));
	}

	@Test
	void testVisitCommandLineLineEmpty() {
		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine("");
		commandLineCriterion.setExpectedResult("Agent Rev:");
		commandLineCriterion.setExecuteLocally(true);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		// The extension manager is empty because it is not involved in this test
		final ExtensionManager extensionManager = ExtensionManager.empty();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			extensionManager
		);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(commandLineCriterion);

		assertNotNull(criterionTestResult);
	}

	@Test
	void testProductRequirementsCriterionProcessCriterionNull() {
		final ProductRequirementsCriterion productRequirementsCriterion = null;

		assertTrue(new CriterionProcessor().process(productRequirementsCriterion).isSuccess());
	}

	@Test
	void testProductRequirementsCriterionProcessCriterionNullVersion() {
		final ProductRequirementsCriterion productRequirementsCriterion = ProductRequirementsCriterion.builder().build();

		assertTrue(new CriterionProcessor().process(productRequirementsCriterion).isSuccess());
	}

	@Test
	void testProductRequirementsCriterionProcessCriterionEmptyVersion() {
		final ProductRequirementsCriterion productRequirementsCriterion = ProductRequirementsCriterion
			.builder()
			.engineVersion("")
			.build();

		assertTrue(new CriterionProcessor().process(productRequirementsCriterion).isSuccess());
	}

	@Test
	void testProductRequirementsCriterionProcessCriterionOK() {
		final ProductRequirementsCriterion productRequirementsCriterion = ProductRequirementsCriterion
			.builder()
			.engineVersion(LOW_VERSION_NUMBER)
			.build();
		assertTrue(new CriterionProcessor().process(productRequirementsCriterion).isSuccess());
	}

	@Test
	void testProductRequirementsCriterionProcessCriterionNOK() {
		final ProductRequirementsCriterion productRequirementsCriterion = ProductRequirementsCriterion
			.builder()
			.engineVersion(HIGH_VERSION_NUMBER) // We will need to update the test once we reach metricshub-engine version 1000
			.build();

		assertFalse(new CriterionProcessor().process(productRequirementsCriterion).isSuccess());
	}
}
