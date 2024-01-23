package org.sentrysoftware.metricshub.engine.strategy.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.WMI_PROCESS_QUERY;
import static org.sentrysoftware.metricshub.engine.constants.Constants.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.collections.Sets;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.client.ClientsExecutor;
import org.sentrysoftware.metricshub.engine.client.http.HttpRequest;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.helpers.LocalOsHandler;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.HttpConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IpmiConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.OsCommandConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.SnmpConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.SshConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.WbemConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.WmiConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.common.HttpMethod;
import org.sentrysoftware.metricshub.engine.connector.model.common.ResultContent;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.DeviceTypeCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.OsCommandCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ProductRequirementsCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.sentrysoftware.metricshub.engine.strategy.utils.CriterionProcessVisitor;
import org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandHelper;
import org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandResult;
import org.sentrysoftware.metricshub.engine.strategy.utils.WqlDetectionHelper;
import org.sentrysoftware.metricshub.engine.telemetry.HostProperties;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * This is a test for {@link CriterionProcessor}
 */
@ExtendWith(MockitoExtension.class)
class CriterionProcessorTest {

	@Mock
	private ClientsExecutor clientsExecutorMock;

	@Mock
	private WqlDetectionHelper wqlDetectionHelperMock;

	@InjectMocks
	private CriterionProcessor criterionProcessor;

	@Mock
	private TelemetryManager telemetryManagerMock;

	private TelemetryManager telemetryManager;
	private WmiConfiguration wmiConfiguration;
	private WbemConfiguration wbemConfiguration;

	private void initWbem() {
		wbemConfiguration = WbemConfiguration.builder().build();
		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(LOCALHOST)
						.hostId(LOCALHOST)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(WbemConfiguration.class, wbemConfiguration))
						.build()
				)
				.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
	}

	@Test
	void testProcessWbemCriterionSuccess() throws Exception {
		initWbem();
		doReturn(EXCUTE_WBEM_RESULT).when(clientsExecutorMock).executeWql(any(), eq(wbemConfiguration), any(), any());
		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_SUCCESS_EXPECTED_RESULT)
			.build();

		final CriterionTestResult result = criterionProcessor.process(wbemCriterion);
		assertTrue(result.isSuccess());
	}

	@Test
	void testProcessWbemCriterionActualResultIsNotExpectedResult() throws Exception {
		initWbem();
		doReturn(EXCUTE_WBEM_RESULT).when(clientsExecutorMock).executeWql(any(), eq(wbemConfiguration), any(), any());
		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_FAILURE_EXPECTED_RESULT)
			.build();
		final CriterionTestResult result = criterionProcessor.process(wbemCriterion);
		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains(WBEM_CRITERION_UNEXPECTED_RESULT_MESSAGE));
	}

	@Test
	void testProcessWbemCriterionMalformedCriterion() throws Exception {
		final CriterionTestResult result = criterionProcessor.process((WbemCriterion) null);
		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains(WBEM_MALFORMED_CRITERION_MESSAGE));
	}

	@Test
	void testProcessWbemEmptyQueryResult() throws Exception {
		initWbem();
		doReturn(List.of()).when(clientsExecutorMock).executeWql(any(), eq(wbemConfiguration), any(), any());
		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_SUCCESS_EXPECTED_RESULT)
			.build();

		final CriterionTestResult result = criterionProcessor.process(wbemCriterion);
		assertFalse(result.isSuccess());
		assertEquals(WBEM_CRITERION_NO_RESULT_MESSAGE, result.getResult());
	}

	@Test
	void testProcessWbemCriterionWithNullWbemConfiguration() throws Exception {
		wbemConfiguration = null;
		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(LOCALHOST)
						.hostId(LOCALHOST)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of())
						.build()
				)
				.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_SUCCESS_EXPECTED_RESULT)
			.build();

		final CriterionTestResult result = criterionProcessor.process(wbemCriterion);
		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains(WBEM_CREDENTIALS_NOT_CONFIGURED));
	}

	@Test
	void testProcessWbemCriterionWithClientException() throws Exception {
		initWbem();
		doThrow(ClientException.class).when(clientsExecutorMock).executeWql(any(), eq(wbemConfiguration), any(), any());
		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_SUCCESS_EXPECTED_RESULT)
			.build();
		final CriterionTestResult result = criterionProcessor.process(wbemCriterion);
		assertFalse(result.isSuccess());
		assertTrue(result.getException() instanceof ClientException);
	}

	private void initSNMP() {
		final SnmpConfiguration snmpConfiguration = SnmpConfiguration
			.builder()
			.community(SNMP_CONFIGURATION_COMMUNITY)
			.version(SnmpConfiguration.SnmpVersion.V1)
			.port(161)
			.timeout(120L)
			.build();

		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(HOST_WIN)
						.hostId(HOST_WIN)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(SnmpConfiguration.class, snmpConfiguration))
						.build()
				)
				.build();
	}

	@Test
	void testProcessSNMPGetNextException() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doThrow(new TimeoutException(SNMP_GET_NEXT_TIMEOUT_EXCEPTION_MESSAGE))
			.when(clientsExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(SnmpGetNextCriterion.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(SNMP_GET_NEXT_TIMEOUT_MESSAGE).build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetNextNullResult() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(null).when(clientsExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(SnmpGetNextCriterion.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(FAILED_SNMP_GET_NEXT_NULL_MESSAGE)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetNextEmptyResult() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(EMPTY).when(clientsExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(SnmpGetNextCriterion.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(FAILED_SNMP_GET_NEXT_EMPTY_MESSAGE)
			.result(EMPTY)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetNextNotSameSubTreeOID() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(SNMP_GET_NEXT_FIRST_RESULT).when(clientsExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(SnmpGetNextCriterion.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(FAILED_SNMP_GET_NEXT_WRONG_OID_MESSAGE)
			.result(SNMP_GET_NEXT_FIRST_RESULT)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetNextSuccessWithNoExpectedResult() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(SNMP_GET_NEXT_SECOND_RESULT).when(clientsExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(SnmpGetNextCriterion.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(SUCCESSFUL_SNMP_GET_NEXT_WITHOUT_EXPECTED_RESULT_MESSAGE)
			.result(SNMP_GET_NEXT_SECOND_RESULT)
			.success(true)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetNextExpectedResultNotMatches() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(SNMP_GET_NEXT_SECOND_RESULT).when(clientsExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(
			SnmpGetNextCriterion.builder().oid(OID).expectedResult(SNMP_GET_NEXT_CRITERION_VERSION).build()
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(FAILED_SNMP_GET_NEXT_OID_NOT_MATCHING_MESSAGE)
			.result(SNMP_GET_NEXT_SECOND_RESULT)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetNextExpectedResultMatches() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(SNMP_GET_NEXT_THIRD_RESULT).when(clientsExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(
			SnmpGetNextCriterion.builder().oid(OID).expectedResult(SNMP_GET_NEXT_CRITERION_VERSION).build()
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(SUCCESSFUL_SNMP_GET_NEXT_MATCHING_EXPECTED_RESULT_MESSAGE)
			.result(SNMP_GET_NEXT_THIRD_RESULT)
			.success(true)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetNextExpectedResultCannotExtract() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(SNMP_GET_NEXT_FOURTH_RESULT).when(clientsExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(
			SnmpGetNextCriterion.builder().oid(OID).expectedResult(SNMP_GET_NEXT_CRITERION_VERSION).build()
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(FAILED_SNMP_GET_NEXT_WRONG_EXTRACTED_VALUE_MESSAGE)
			.result(SNMP_GET_NEXT_FOURTH_RESULT)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetNextReturnsEmptyResult() {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		assertEquals(CriterionTestResult.empty(), criterionProcessor.process((SnmpGetNextCriterion) null));
		assertEquals(CriterionTestResult.empty(), criterionProcessor.process((SnmpGetNextCriterion) null));
		assertNull(criterionProcessor.process(SnmpGetNextCriterion.builder().oid(OID).build()).getResult());
	}

	@Test
	void testProcessSNMPGetReturnsEmptyResult() {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		assertEquals(CriterionTestResult.empty(), criterionProcessor.process((SnmpGetCriterion) null));
		assertNull(criterionProcessor.process(SnmpGetCriterion.builder().oid(OID).build()).getResult());
	}

	@Test
	void testProcessSNMPGetExpectedResultMatches() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(EXECUTE_SNMP_GET_RESULT).when(clientsExecutorMock).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(
			SnmpGetCriterion.builder().oid(OID).expectedResult(EXPECTED_SNMP_RESULT).build()
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(SNMP_GET_EXPECTED_RESULT_MATCHES_MESSAGE)
			.result(EXECUTE_SNMP_GET_RESULT)
			.success(true)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetExpectedResultNotMatches() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(EXECUTE_SNMP_GET_RESULT).when(clientsExecutorMock).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(
			SnmpGetCriterion.builder().oid(OID).expectedResult(SNMP_VERSION).build()
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(SNMP_GET_EXPECTED_RESULT_NOT_MATCHES_MESSAGE)
			.result(EXECUTE_SNMP_GET_RESULT)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetSuccessWithNoExpectedResult() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(EXECUTE_SNMP_GET_RESULT).when(clientsExecutorMock).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(SnmpGetCriterion.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(SNMP_GET_SUCCESS_WITH_NO_EXPECTED_RESULT_MESSAGE)
			.result(EXECUTE_SNMP_GET_RESULT)
			.success(true)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetEmptyResult() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(EMPTY).when(clientsExecutorMock).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(SnmpGetCriterion.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(SNMP_GET_EMPTY_RESULT_MESSAGE)
			.result(EMPTY)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetNullResult() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(null).when(clientsExecutorMock).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(SnmpGetCriterion.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(SNMP_GET_NULL_RESULT_MESSAGE).build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSNMPGetException() throws Exception {
		initSNMP();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doThrow(new TimeoutException(SNMP_GET_TIMEOUT_MESSAGE))
			.when(clientsExecutorMock)
			.executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionProcessor.process(SnmpGetCriterion.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(SNMP_GET_EXCEPTION_MESSAGE).build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessProcessProcessNull() {
		final ProcessCriterion processCriterion = null;

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();

		assertEquals(CriterionTestResult.empty(), criterionProcessor.process(processCriterion));
	}

	@Test
	void testProcessProcessCommandLineEmpty() {
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine("");

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();

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
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
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
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
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
	void testProcessProcessWindowsEmptyResult() {
		// Init the mocks
		MockitoAnnotations.openMocks(this);

		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine(PROCESS_CRITERION_COMMAND_LINE);

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::getOS).thenReturn(Optional.of(LocalOsHandler.WINDOWS));
			mockedLocalOSHandler.when(LocalOsHandler::getSystemOsVersion).thenReturn(Optional.of("5.1"));

			doReturn(CriterionTestResult.error(processCriterion, WMI_QUERY_EMPTY_VALUE_MESSAGE))
				.when(wqlDetectionHelperMock)
				.performDetectionTest(any(), any(), any());

			final CriterionTestResult criterionTestResult = criterionProcessor.process(processCriterion);

			assertNotNull(criterionTestResult);
			assertFalse(criterionTestResult.isSuccess());
			assertTrue(criterionTestResult.getMessage().contains(WMI_QUERY_EMPTY_VALUE_MESSAGE));
			assertNull(criterionTestResult.getResult());
		}
	}

	@Test
	void testProcessProcessWindowsOK() throws Exception {
		final ProcessCriterion processCriterion = new ProcessCriterion();
		processCriterion.setCommandLine(PROCESS_CRITERION_COMMAND_LINE);

		final WmiConfiguration wmiConfiguration = WmiConfiguration.builder().timeout(STRATEGY_TIMEOUT).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(LOCALHOST)
					.hostId(LOCALHOST)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
					.build()
			)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
			mockedLocalOSHandler.when(LocalOsHandler::getOS).thenReturn(Optional.of(LocalOsHandler.WINDOWS));
			mockedLocalOSHandler.when(LocalOsHandler::getSystemOsVersion).thenReturn(Optional.of("5.1"));

			final WmiConfiguration localWmiConfiguration = WmiConfiguration
				.builder()
				.username(null)
				.password(null)
				.timeout(30L)
				.build();

			doReturn(EXECUTE_WMI_RESULT)
				.when(clientsExecutorMock)
				.executeWql(LOCALHOST, localWmiConfiguration, WMI_PROCESS_QUERY, CRITERION_WMI_NAMESPACE);

			final CriterionTestResult criterionTestResult = criterionProcessor.process(processCriterion);

			assertNotNull(criterionTestResult);
			assertTrue(criterionTestResult.isSuccess());
			assertEquals(WMI_CRITERION_TEST_SUCCEED_MESSAGE, criterionTestResult.getMessage());
			assertEquals("MBM6.exe", criterionTestResult.getResult());
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
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
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
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
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
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
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
	void testProcessServiceCheckServiceNull() {
		final ServiceCriterion serviceCriterion = null;
		assertTrue(criterionProcessor.process(serviceCriterion).getMessage().contains("Malformed Service criterion."));
	}

	@Test
	void testProcessServiceCheckProtocolNull() {
		final ServiceCriterion serviceCriterion = new ServiceCriterion();
		serviceCriterion.setName(TWGIPC);

		assertTrue(criterionProcessor.process(serviceCriterion).getMessage().contains(NEITHER_WMI_NOR_WINRM_ERROR));
	}

	@Test
	void testProcessServiceCheckOsNull() {
		final WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(STRATEGY_TIMEOUT)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_WIN)
					.hostId(HOST_WIN)
					.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
					.build()
			)
			.build();
		doReturn(wmiConfiguration).when(telemetryManagerMock).getWinConfiguration();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();

		final ServiceCriterion serviceCriterion = new ServiceCriterion();
		serviceCriterion.setName(TWGIPC);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(serviceCriterion);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertTrue(criterionTestResult.getMessage().contains(HOST_OS_IS_NOT_WINDOWS_MESSAGE));
		assertNull(criterionTestResult.getResult());
	}

	@Test
	void testProcessServiceCheckOsNotWindows() {
		final ServiceCriterion serviceCriterion = new ServiceCriterion();
		serviceCriterion.setName(TWGIPC);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(serviceCriterion);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertNull(criterionTestResult.getResult());
	}

	@Test
	@EnabledOnOs(WINDOWS)
	void testProcessServiceCheckServiceNameEmpty() {
		final WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(STRATEGY_TIMEOUT)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(LOCALHOST)
					.hostId(LOCALHOST)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(wmiConfiguration.getClass(), wmiConfiguration))
					.build()
			)
			.build();
		doReturn(wmiConfiguration).when(telemetryManagerMock).getWinConfiguration();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		final ServiceCriterion serviceCriterion = new ServiceCriterion();
		serviceCriterion.setName("");

		final CriterionTestResult criterionTestResult = criterionProcessor.process(serviceCriterion);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertTrue(criterionTestResult.getMessage().contains(SERVICE_NAME_NOT_SPECIFIED_MESSAGE));
		assertNotNull(criterionTestResult.getResult());
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

		// Init CriterionTestResult success and failure instances
		final CriterionTestResult successfulTestResult = CriterionTestResult
			.builder()
			.message(SUCCESSFUL_OS_DETECTION)
			.result(CONFIGURED_OS_NT_MESSAGE)
			.success(true)
			.build();

		final CriterionTestResult failedTestResult = CriterionTestResult
			.builder()
			.message(FAILED_OS_DETECTION)
			.result(CONFIGURED_OS_NT_MESSAGE)
			.success(false)
			.build();

		// Test configured NETWORK OS

		final DeviceTypeCriterion deviceTypeCriterion = DeviceTypeCriterion.builder().build();
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
	void HttpCriterionProcessHttpCriterionNullTest() {
		final HttpCriterion httpCriterion = null;
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_ID)
			.hostId(HOST_ID)
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(HttpConfiguration.class, HttpConfiguration.builder().build()))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		assertEquals(CriterionTestResult.empty(), criterionProcessor.process(httpCriterion));
	}

	@Test
	void HttpCriterionProcessHttpConfigurationNullTest() {
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

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();
		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		assertEquals(CriterionTestResult.empty(), criterionProcessor.process(httpCriterion));
	}

	@Test
	void HttpCriterionProcessRequestWrongResultTest() throws IOException {
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
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_ID)
			.hostId(HOST_ID)
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(HttpConfiguration.class, HttpConfiguration.builder().build()))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final String result = "Something went Wrong";
		final HttpRequest httpRequest = HttpRequest
			.builder()
			.hostname(HOST_ID)
			.method(HTTP_GET)
			.url(httpCriterion.getUrl())
			.header(httpCriterion.getHeader(), MY_CONNECTOR_1_NAME, HOST_ID)
			.body(httpCriterion.getBody(), MY_CONNECTOR_1_NAME, HOST_ID)
			.httpConfiguration(httpConfiguration)
			.resultContent(httpCriterion.getResultContent())
			.authenticationToken(httpCriterion.getAuthenticationToken())
			.build();
		doReturn(result).when(clientsExecutorMock).executeHttp(httpRequest, false);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		final String message = String.format(
			"Hostname %s - HTTP test failed - " +
			"The result (%s) returned by the HTTP test did not match the expected result (%s)." +
			"Expected value: %s - returned value %s.",
			HOST_ID,
			result,
			RESULT,
			RESULT,
			result
		);
		final CriterionTestResult criterionTestResult = criterionProcessor.process(httpCriterion);

		assertEquals(result, criterionTestResult.getResult());
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(message, criterionTestResult.getMessage());
		assertNull(criterionTestResult.getException());
	}

	@Test
	void HttpCriterionProcessOKTest() throws IOException {
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
		final HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_ID)
			.hostId(HOST_ID)
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(HttpConfiguration.class, HttpConfiguration.builder().build()))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final HttpRequest httpRequest = HttpRequest
			.builder()
			.hostname(HOST_ID)
			.method(HTTP_GET)
			.url(httpCriterion.getUrl())
			.header(httpCriterion.getHeader(), MY_CONNECTOR_1_NAME, HOST)
			.body(httpCriterion.getBody(), MY_CONNECTOR_1_NAME, HOST_ID)
			.httpConfiguration(httpConfiguration)
			.resultContent(httpCriterion.getResultContent())
			.authenticationToken(httpCriterion.getAuthenticationToken())
			.build();
		doReturn(RESULT).when(clientsExecutorMock).executeHttp(httpRequest, false);

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		final String message = "Hostname PC-120 - HTTP test succeeded. Returned result: result.";
		final CriterionTestResult criterionTestResult = criterionProcessor.process(httpCriterion);

		assertEquals(RESULT, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(message, criterionTestResult.getMessage());
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessIpmiWindowsSuccess() {
		// Init the mocks
		MockitoAnnotations.openMocks(this);

		// Init configurations
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		final WmiConfiguration wmiProtocol = WmiConfiguration
			.builder()
			.namespace(HOST_WIN)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(STRATEGY_TIMEOUT)
			.build();

		final IpmiCriterion ipmi = IpmiCriterion.builder().forceSerialization(true).build();

		// Add configurations to configurations Map
		configurations.put(wmiProtocol.getClass(), wmiProtocol);
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_WIN)
			.hostId(HOST_WIN)
			.hostType(DeviceKind.WINDOWS)
			.configurations(configurations)
			.build();

		// Create a TelemetryManager instance
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		// Mock getHostConfiguration and getWinConfiguration
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(configurations.get(wmiProtocol.getClass())).when(telemetryManagerMock).getWinConfiguration();

		// Mock performDetectionTest
		doReturn(CriterionTestResult.success(ipmi, IPMI_SUCCESS_MESSAGE))
			.when(wqlDetectionHelperMock)
			.performDetectionTest(any(), any(), any());

		final CriterionTestResult criterionTestResult = criterionProcessor.process(ipmi);

		assertNotNull(criterionTestResult);
		assertEquals(IPMI_SUCCESS_MESSAGE, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
	}

	@Test
	void testProcessIpmiWindowsFailure() {
		// Init the mocks
		MockitoAnnotations.openMocks(this);

		// Init configurations
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		final WmiConfiguration wmiProtocol = WmiConfiguration
			.builder()
			.namespace(HOST_WIN)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(STRATEGY_TIMEOUT)
			.build();

		final IpmiCriterion ipmi = IpmiCriterion.builder().forceSerialization(true).build();

		// Add configurations to configurations map
		configurations.put(wmiProtocol.getClass(), wmiProtocol);

		// Create a TelemetryManager instance
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_WIN)
			.hostId(HOST_WIN)
			.hostType(DeviceKind.WINDOWS)
			.configurations(configurations)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// mock getHostConfiguration, getWinConfiguration and performDetectionTest
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(configurations.get(wmiProtocol.getClass())).when(telemetryManagerMock).getWinConfiguration();
		doReturn(CriterionTestResult.success(ipmi, IPMI_FAILURE_MESSAGE))
			.when(wqlDetectionHelperMock)
			.performDetectionTest(any(), any(), any());

		final CriterionTestResult criterionTestResult = criterionProcessor.process(ipmi);

		assertNotNull(criterionTestResult);
		assertEquals(IPMI_FAILURE_MESSAGE, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
	}

	@Test
	void testProcessIpmiLinuxWithWrongIpmitoolCommand() {
		// Init configurations
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(STRATEGY_TIMEOUT)
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_LINUX)
			.hostId(HOST_LINUX)
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(
					HttpConfiguration.class,
					HttpConfiguration.builder().timeout(STRATEGY_TIMEOUT).build(),
					OsCommandConfiguration.class,
					OsCommandConfiguration.builder().timeout(STRATEGY_TIMEOUT).build()
				)
			)
			.build();
		// Create TelemetryManager instance
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();

		hostConfiguration.setConfigurations(
			Map.of(
				HttpConfiguration.class,
				HttpConfiguration.builder().build(),
				OsCommandConfiguration.class,
				OsCommandConfiguration.builder().useSudoCommands(Sets.newSet()).timeout(STRATEGY_TIMEOUT).build(),
				SshConfiguration.class,
				sshConfiguration
			)
		);
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();
		assertFalse(criterionProcessor.process(new IpmiCriterion()).isSuccess());
	}

	@Test
	void testProcessIpmiLinuxWithWrongSshCommandResult() {
		// Init configurations
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(STRATEGY_TIMEOUT)
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_LINUX)
			.hostId(HOST_LINUX)
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(
					HttpConfiguration.class,
					HttpConfiguration.builder().timeout(STRATEGY_TIMEOUT).build(),
					OsCommandConfiguration.class,
					OsCommandConfiguration.builder().useSudoCommands(Sets.newSet()).timeout(STRATEGY_TIMEOUT).build()
				)
			)
			.build();
		// Create TelemetryManager instance
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(HostProperties.builder().isLocalhost(false).build())
			.build();

		// Mock getHostConfiguration, getHostProperties and runSshCommand
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		try (MockedStatic<OsCommandHelper> osCommandHelper = mockStatic(OsCommandHelper.class)) {
			osCommandHelper
				.when(() ->
					OsCommandHelper.runSshCommand(anyString(), eq(HOST_LINUX), eq(sshConfiguration), anyInt(), isNull(), isNull())
				)
				.thenReturn(INVALID_SSH_RESPONSE);
			assertFalse(criterionProcessor.process(new IpmiCriterion()).isSuccess());
		}
	}

	@Test
	void testProcessIpmiLinuxWithNullOsConfiguration() {
		// Init configurations
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_LINUX)
			.hostId(HOST_LINUX)
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(HttpConfiguration.class, HttpConfiguration.builder().timeout(STRATEGY_TIMEOUT).build()))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();

		// Mock getHostConfiguration and getHostProperties
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		assertEquals(
			CriterionTestResult
				.builder()
				.result("")
				.success(false)
				.message("Hostname " + HOST_LINUX + NO_OS_CONFIGURATION_MESSAGE)
				.build(),
			criterionProcessor.process(new IpmiCriterion())
		);
	}

	@Test
	void testProcessIpmiLinuxWithLocalhost() {
		// Init configurations
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOST_LINUX)
			.hostId(HOST_LINUX)
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(
					HttpConfiguration.class,
					HttpConfiguration.builder().timeout(STRATEGY_TIMEOUT).build(),
					OsCommandConfiguration.class,
					OsCommandConfiguration.builder().useSudoCommands(Sets.newSet()).timeout(STRATEGY_TIMEOUT).build()
				)
			)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();

		// Mock getHostProperties and getHostConfiguration
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		try (MockedStatic<OsCommandHelper> osCommand = mockStatic(OsCommandHelper.class)) {
			osCommand.when(() -> OsCommandHelper.runLocalCommand(any(), anyInt(), isNull())).thenReturn(IPMI_RESULT_EXAMPLE);
			assertEquals(
				CriterionTestResult
					.builder()
					.result(IPMI_RESULT_EXAMPLE)
					.success(true)
					.message(IPMI_CONNECTION_SUCCESS_WITH_IN_BAND_DRIVER_MESSAGE)
					.build()
					.getMessage(),
				criterionProcessor.process(new IpmiCriterion()).getMessage()
			);
		}
	}

	@Test
	void testBuildIpmiCommand() {
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(STRATEGY_TIMEOUT)
			.build();
		final OsCommandConfiguration osCommandConfiguration = OsCommandConfiguration
			.builder()
			.sudoCommand(SUDO_KEYWORD)
			.useSudoCommands(Sets.newSet())
			.timeout(STRATEGY_TIMEOUT)
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.SOLARIS)
			.configurations(Map.of(OsCommandConfiguration.class, osCommandConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();

		doReturn(telemetryManager.getHostProperties()).when(telemetryManagerMock).getHostProperties();

		String commandResult;

		// Test successful command
		try (MockedStatic<OsCommandHelper> osCommandHelper = mockStatic(OsCommandHelper.class)) {
			osCommandHelper
				.when(() -> OsCommandHelper.runLocalCommand(any(), eq(STRATEGY_TIMEOUT), isNull()))
				.thenReturn(VALID_SOLARIS_VERSION_TEN);
			commandResult =
				criterionProcessor.buildIpmiCommand(
					DeviceKind.SOLARIS,
					LOCALHOST,
					sshConfiguration,
					osCommandConfiguration,
					STRATEGY_TIMEOUT.intValue()
				);
			assertNotNull(commandResult);
			assertTrue(commandResult.startsWith(PATH));
		}

		// Test failed command
		try (MockedStatic<OsCommandHelper> osCommandHelper = mockStatic(OsCommandHelper.class)) {
			osCommandHelper
				.when(() -> OsCommandHelper.runLocalCommand(any(), eq(STRATEGY_TIMEOUT), isNull()))
				.thenReturn(INVALID_SOLARIS_VERSION);
			commandResult =
				criterionProcessor.buildIpmiCommand(
					DeviceKind.SOLARIS,
					LOCALHOST,
					sshConfiguration,
					osCommandConfiguration,
					STRATEGY_TIMEOUT.intValue()
				);
			assertNotNull(commandResult);
			assertTrue(commandResult.contains(SOLARIS_VERSION_NOT_IDENTIFIED_MESSAGE_TOKEN)); // Not Successful command the response starts with Couldn't identify
		}

		// Test sudo command
		try (MockedStatic<OsCommandHelper> osCommandHelper = mockStatic(OsCommandHelper.class)) {
			osCommandConfiguration.setUseSudo(true);
			osCommandHelper
				.when(() -> OsCommandHelper.runLocalCommand(any(), eq(STRATEGY_TIMEOUT), isNull()))
				.thenReturn(VALID_SOLARIS_VERSION_TEN);
			commandResult =
				criterionProcessor.buildIpmiCommand(
					DeviceKind.SOLARIS,
					LOCALHOST,
					sshConfiguration,
					osCommandConfiguration,
					STRATEGY_TIMEOUT.intValue()
				);
			assertNotNull(commandResult);
			assertTrue(commandResult.contains(SUDO_KEYWORD)); // Successful sudo command
		}

		// Test Linux
		osCommandConfiguration.setUseSudo(false);
		commandResult =
			criterionProcessor.buildIpmiCommand(DeviceKind.LINUX, LOCALHOST, sshConfiguration, osCommandConfiguration, 120);
		assertEquals(LINUX_BUILD_IPMI_COMMAND, commandResult);
	}

	@Test
	void testGetIpmiCommandForSolaris() throws Exception {
		// Solaris Version 10 => bmc
		String commandResult = criterionProcessor.getIpmiCommandForSolaris(
			IPMI_TOOL_COMMAND,
			LOCALHOST,
			VALID_SOLARIS_VERSION_TEN
		);
		assertEquals(IPMI_TOOL_COMMAND + BMC, commandResult);

		// Solaris version 9 => lipmi
		commandResult =
			criterionProcessor.getIpmiCommandForSolaris(IPMI_TOOL_COMMAND, LOCALHOST, VALID_SOLARIS_VERSION_NINE);
		assertEquals(IPMI_TOOL_COMMAND + LIPMI, commandResult);

		// wrong String OS version
		Exception exception = assertThrows(
			Exception.class,
			() -> {
				criterionProcessor.getIpmiCommandForSolaris(IPMI_TOOL_COMMAND, LOCALHOST, INVALID_SOLARIS_VERSION);
			}
		);

		String actualMessage = exception.getMessage();

		assertTrue(actualMessage.contains(UNKNOWN_SOLARIS_VERSION));

		// old OS version
		exception =
			assertThrows(
				Exception.class,
				() -> {
					criterionProcessor.getIpmiCommandForSolaris(IPMI_TOOL_COMMAND, LOCALHOST, OLD_SOLARIS_VERSION);
				}
			);

		actualMessage = exception.getMessage();

		assertTrue(actualMessage.contains(OLD_SOLARIS_VERSION_MESSAGE));
	}

	@Test
	void testProcessIPMIOutOfBandConfigurationNotFound() {
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(MANAGEMENT_CARD_HOST)
					.hostType(DeviceKind.OOB)
					.hostname(MANAGEMENT_CARD_HOST)
					.configurations(Collections.emptyMap())
					.build()
			)
			.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		assertEquals(CriterionTestResult.empty(), criterionProcessor.process(new IpmiCriterion()));
	}

	@Test
	void testProcessIPMIOutOfBand() throws Exception {
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(MANAGEMENT_CARD_HOST)
					.hostType(DeviceKind.OOB)
					.hostname(MANAGEMENT_CARD_HOST)
					.configurations(
						Map.of(
							IpmiConfiguration.class,
							IpmiConfiguration.builder().username(USERNAME).password(PASSWORD.toCharArray()).build()
						)
					)
					.build()
			)
			.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(SYSTEM_POWER_UP_MESSAGE)
			.when(clientsExecutorMock)
			.executeIpmiDetection(eq(MANAGEMENT_CARD_HOST), any(IpmiConfiguration.class));
		assertEquals(
			CriterionTestResult
				.builder()
				.result(SYSTEM_POWER_UP_MESSAGE)
				.message(IPMI_CONNECTION_SUCCESS_WITH_IMPI_OVER_LAN_MESSAGE)
				.success(true)
				.build(),
			criterionProcessor.process(new IpmiCriterion())
		);
	}

	@Test
	void testProcessIPMIOutOfBandNullResult() throws Exception {
		final IpmiConfiguration ipmiConfiguration = IpmiConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();

		configurations.put(IpmiConfiguration.class, ipmiConfiguration);
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(MANAGEMENT_CARD_HOST)
			.hostType(DeviceKind.OOB)
			.hostname(MANAGEMENT_CARD_HOST)
			.configurations(configurations)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.hostConfiguration(hostConfiguration)
			.build();

		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(null)
			.when(clientsExecutorMock)
			.executeIpmiDetection(eq(MANAGEMENT_CARD_HOST), any(IpmiConfiguration.class));
		assertEquals(
			CriterionTestResult.builder().message(OOB_NULL_RESULT_MESSAGE).build().getMessage(),
			criterionProcessor.process(new IpmiCriterion()).getMessage()
		);
	}

	@Test
	void productRequirementsCriterionProcessCriterionNullTest() {
		final ProductRequirementsCriterion productRequirementsCriterion = null;

		assertTrue(new CriterionProcessor().process(productRequirementsCriterion).isSuccess());
	}

	@Test
	void productRequirementsCriterionProcessCriterionNullVersionTest() {
		final ProductRequirementsCriterion productRequirementsCriterion = ProductRequirementsCriterion.builder().build();

		assertTrue(new CriterionProcessor().process(productRequirementsCriterion).isSuccess());
	}

	@Test
	void productRequirementsCriterionProcessCriterionEmptyVersionTest() {
		final ProductRequirementsCriterion productRequirementsCriterion = ProductRequirementsCriterion
			.builder()
			.engineVersion("")
			.build();

		assertTrue(new CriterionProcessor().process(productRequirementsCriterion).isSuccess());
	}

	@Test
	void productRequirementsCriterionProcessCriterionOKTest() {
		final ProductRequirementsCriterion productRequirementsCriterion = ProductRequirementsCriterion
			.builder()
			.engineVersion(LOW_VERSION_NUMBER)
			.build();
		assertTrue(new CriterionProcessor().process(productRequirementsCriterion).isSuccess());
	}

	@Test
	void productRequirementsCriterionProcessCriterionNOKTest() {
		final ProductRequirementsCriterion productRequirementsCriterion = ProductRequirementsCriterion
			.builder()
			.engineVersion(HIGH_VERSION_NUMBER) // We will need to update the test once we reach metricshub-engine version 1000
			.build();

		assertFalse(new CriterionProcessor().process(productRequirementsCriterion).isSuccess());
	}

	private void initWmi() {
		wmiConfiguration = WmiConfiguration.builder().build();
		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(LOCALHOST)
						.hostId(LOCALHOST)
						.hostType(DeviceKind.WINDOWS)
						.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
						.build()
				)
				.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(telemetryManager.getHostConfiguration().getConfigurations().get(WmiConfiguration.class))
			.when(telemetryManagerMock)
			.getWinConfiguration();
	}

	@Test
	void testProcessWmiCriterionSuccess() throws Exception {
		initWmi();
		doReturn(EXCUTE_WBEM_RESULT).when(clientsExecutorMock).executeWql(any(), eq(wmiConfiguration), any(), any());
		final WmiCriterion wmiCriterion = WmiCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_SUCCESS_EXPECTED_RESULT)
			.build();

		final CriterionTestResult result = criterionProcessor.process(wmiCriterion);
		assertTrue(result.isSuccess());
	}

	@Test
	void testProcessWmiCriterionActualResultIsNotExpectedResult() throws Exception {
		initWmi();
		doReturn(EXCUTE_WBEM_RESULT).when(clientsExecutorMock).executeWql(any(), eq(wmiConfiguration), any(), any());
		final WmiCriterion wmiCriterion = WmiCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_FAILURE_EXPECTED_RESULT)
			.build();
		final CriterionTestResult result = criterionProcessor.process(wmiCriterion);
		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains(WMI_CRITERION_UNEXPECTED_RESULT_MESSAGE));
	}

	@Test
	void testProcessWmiCriterionMalformedCriterion() throws Exception {
		final CriterionTestResult result = criterionProcessor.process((WmiCriterion) null);
		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains(WBEM_MALFORMED_CRITERION_MESSAGE));
	}

	@Test
	void testProcessWmiEmptyQueryResult() throws Exception {
		initWmi();
		doReturn(List.of()).when(clientsExecutorMock).executeWql(any(), eq(wmiConfiguration), any(), any());
		final WmiCriterion wmiCriterion = WmiCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_SUCCESS_EXPECTED_RESULT)
			.build();

		final CriterionTestResult result = criterionProcessor.process(wmiCriterion);
		assertFalse(result.isSuccess());
		assertEquals(WBEM_CRITERION_NO_RESULT_MESSAGE, result.getResult());
	}

	@Test
	void testProcessWmiCriterionWithNullWmiConfiguration() throws Exception {
		wmiConfiguration = null;
		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(LOCALHOST)
						.hostId(LOCALHOST)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of())
						.build()
				)
				.build();
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		final WmiCriterion wmiCriterion = WmiCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_SUCCESS_EXPECTED_RESULT)
			.build();

		final CriterionTestResult result = criterionProcessor.process(wmiCriterion);
		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains(WMI_CREDENTIALS_NOT_CONFIGURED));
	}

	@Test
	void testProcessWmiCriterionWithClientException() throws Exception {
		initWmi();
		doThrow(ClientException.class).when(clientsExecutorMock).executeWql(any(), eq(wmiConfiguration), any(), any());
		final WmiCriterion wmiCriterion = WmiCriterion
			.builder()
			.query(WBEM_QUERY)
			.expectedResult(WEBM_CRITERION_SUCCESS_EXPECTED_RESULT)
			.build();
		final CriterionTestResult result = criterionProcessor.process(wmiCriterion);
		assertFalse(result.isSuccess());
		assertTrue(result.getException() instanceof ClientException);
	}

	@Test
	void testProcessOsCommandNotExpectedResult() {
		final OsCommandCriterion osCommandCriterion = OsCommandCriterion
			.builder()
			.commandLine(SSH_SUDO_COMMAND)
			.errorMessage(EMPTY)
			.expectedResult(RESULT)
			.executeLocally(true)
			.timeout(30L)
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		final SshConfiguration sshConfiguration = new SshConfiguration();
		sshConfiguration.setUsername(USERNAME);
		sshConfiguration.setPassword(PASSWORD.toCharArray());
		configurations.put(SshConfiguration.class, sshConfiguration);

		final OsCommandConfiguration osCommandConfiguration = OsCommandConfiguration.builder().useSudo(false).build();
		configurations.put(OsCommandConfiguration.class, osCommandConfiguration);

		final HostConfiguration hostConfiguration = new HostConfiguration();
		hostConfiguration.setHostname(LOCALHOST);
		hostConfiguration.setConfigurations(configurations);

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		// The result is not the same as the expected result
		OsCommandResult result = new OsCommandResult(ERROR, SSH_SUDO_COMMAND);

		try (MockedStatic<OsCommandHelper> osCommandHelper = mockStatic(OsCommandHelper.class)) {
			osCommandHelper
				.when(() -> OsCommandHelper.runOsCommand(SSH_SUDO_COMMAND, telemetryManager, 30L, true, true))
				.thenReturn(result);
			final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

			final String message = String.format(
				"OsCommandCriterion test ran but failed:\n" +
				"- CommandLine: %s\n" +
				"- ExecuteLocally: true\n" +
				"- ExpectedResult: %s\n" +
				"- Timeout: 30\n" +
				"\n" +
				"Actual result:\n" +
				"%s",
				SSH_SUDO_COMMAND,
				RESULT,
				ERROR
			);

			assertEquals(ERROR, criterionTestResult.getResult());
			assertFalse(criterionTestResult.isSuccess());
			assertEquals(message, criterionTestResult.getMessage());
			assertNull(criterionTestResult.getException());
		}
	}

	@Test
	void testProcessOsCommandOK() {
		final OsCommandCriterion osCommandCriterion = OsCommandCriterion
			.builder()
			.commandLine(SSH_SUDO_COMMAND)
			.errorMessage(EMPTY)
			.expectedResult(RESULT)
			.executeLocally(true)
			.timeout(30L)
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		final SshConfiguration sshConfiguration = new SshConfiguration();
		sshConfiguration.setUsername(USERNAME);
		sshConfiguration.setPassword(PASSWORD.toCharArray());
		configurations.put(SshConfiguration.class, sshConfiguration);

		final OsCommandConfiguration osCommandConfiguration = OsCommandConfiguration.builder().useSudo(false).build();
		configurations.put(OsCommandConfiguration.class, osCommandConfiguration);

		final HostConfiguration hostConfiguration = new HostConfiguration();
		hostConfiguration.setHostname(LOCALHOST);
		hostConfiguration.setConfigurations(configurations);

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		OsCommandResult result = new OsCommandResult(RESULT, SSH_SUDO_COMMAND);

		try (MockedStatic<OsCommandHelper> osCommandHelper = mockStatic(OsCommandHelper.class)) {
			osCommandHelper
				.when(() -> OsCommandHelper.runOsCommand(SSH_SUDO_COMMAND, telemetryManager, 30L, true, true))
				.thenReturn(result);
			final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

			final String message = String.format(
				"OsCommandCriterion test succeeded:\n" +
				"- CommandLine: %s\n" +
				"- ExecuteLocally: true\n" +
				"- ExpectedResult: %s\n" +
				"- Timeout: 30\n" +
				"\n" +
				"Result: %s",
				SSH_SUDO_COMMAND,
				RESULT,
				RESULT
			);

			assertEquals(RESULT, criterionTestResult.getResult());
			assertTrue(criterionTestResult.isSuccess());
			assertEquals(message, criterionTestResult.getMessage());
			assertNull(criterionTestResult.getException());
		}
	}

	@Test
	void testProcessOsCommandEmbeddedFileOK() {
		final OsCommandCriterion osCommandCriterion = OsCommandCriterion
			.builder()
			.commandLine(COMMAND_FILE_ABSOLUTE_PATH)
			.errorMessage(EMPTY)
			.expectedResult(RESULT)
			.executeLocally(true)
			.timeout(120L)
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		final SshConfiguration sshConfiguration = new SshConfiguration();
		sshConfiguration.setUsername(USERNAME);
		sshConfiguration.setPassword(PASSWORD.toCharArray());
		configurations.put(SshConfiguration.class, sshConfiguration);

		final OsCommandConfiguration osCommandConfiguration = OsCommandConfiguration.builder().useSudo(false).build();
		configurations.put(OsCommandConfiguration.class, osCommandConfiguration);

		final HostConfiguration hostConfiguration = new HostConfiguration();
		hostConfiguration.setHostname(LOCALHOST);
		hostConfiguration.setConfigurations(configurations);

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		OsCommandResult result = new OsCommandResult(RESULT, COMMAND_FILE_ABSOLUTE_PATH);

		try (MockedStatic<OsCommandHelper> osCommandHelper = mockStatic(OsCommandHelper.class)) {
			osCommandHelper
				.when(() -> OsCommandHelper.runOsCommand(COMMAND_FILE_ABSOLUTE_PATH, telemetryManager, 120L, true, true))
				.thenReturn(result);
			final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

			final String message = String.format(
				"OsCommandCriterion test succeeded:\n" +
				"- CommandLine: %s\n" +
				"- ExecuteLocally: true\n" +
				"- ExpectedResult: %s\n" +
				"- Timeout: 120\n" +
				"\n" +
				"Result: %s",
				COMMAND_FILE_ABSOLUTE_PATH,
				RESULT,
				RESULT
			);

			assertEquals(RESULT, criterionTestResult.getResult());
			assertTrue(criterionTestResult.isSuccess());
			assertEquals(message, criterionTestResult.getMessage());
			assertNull(criterionTestResult.getException());
		}
	}

	@Test
	void testVisitOsCommandNull() {
		final OsCommandCriterion osCommandCriterion = null;

		final CriterionTestResult criterionTestResult = new CriterionProcessor().process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertTrue(criterionTestResult.getMessage().toLowerCase().contains("malformed"));
		assertNull(criterionTestResult.getResult());
	}

	@Test
	void testVisitOsCommandExpectedResultNull() {
		final OsCommandCriterion osCommandCriterion = new OsCommandCriterion();
		osCommandCriterion.setCommandLine(
			"naviseccli -User %{USERNAME} -Password %{PASSWORD} -Address %{HOSTNAME} -Scope 1 getagent"
		);
		osCommandCriterion.setExecuteLocally(true);
		osCommandCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);
		final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"OsCommandCriterion test succeeded:\n" +
			osCommandCriterion.toString() +
			"\n\n" +
			"Result: CommandLine or ExpectedResult are empty. Skipping this test.",
			criterionTestResult.getMessage()
		);
		assertEquals("CommandLine or ExpectedResult are empty. Skipping this test.", criterionTestResult.getResult());
	}

	@Test
	void testVisitOsCommandLineEmpty() {
		final OsCommandCriterion osCommandCriterion = new OsCommandCriterion();
		osCommandCriterion.setCommandLine("");
		osCommandCriterion.setExpectedResult("Agent Rev:");
		osCommandCriterion.setExecuteLocally(true);
		osCommandCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);
		final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"OsCommandCriterion test succeeded:\n" +
			osCommandCriterion.toString() +
			"\n\n" +
			"Result: CommandLine or ExpectedResult are empty. Skipping this test.",
			criterionTestResult.getMessage()
		);
		assertEquals("CommandLine or ExpectedResult are empty. Skipping this test.", criterionTestResult.getResult());
	}

	@Test
	void testVisitOsCommandExpectedResultEmpty() {
		final OsCommandCriterion osCommandCriterion = new OsCommandCriterion();
		osCommandCriterion.setCommandLine(
			"naviseccli -User %{USERNAME} -Password %{PASSWORD} -Address %{HOSTNAME} -Scope 1 getagent"
		);
		osCommandCriterion.setExpectedResult("");
		osCommandCriterion.setExecuteLocally(true);
		osCommandCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);
		final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"OsCommandCriterion test succeeded:\n" +
			osCommandCriterion.toString() +
			"\n\n" +
			"Result: CommandLine or ExpectedResult are empty. Skipping this test.",
			criterionTestResult.getMessage()
		);
		assertEquals("CommandLine or ExpectedResult are empty. Skipping this test.", criterionTestResult.getResult());
	}

	@Test
	void testVisitOsCommandRemoteNoUser() {
		final OsCommandCriterion osCommandCriterion = new OsCommandCriterion();
		osCommandCriterion.setCommandLine(
			"naviseccli -User %{USERNAME} -Password %{PASSWORD} -Address %{HOSTNAME} -Scope 1 getagent"
		);
		osCommandCriterion.setExpectedResult("Agent Rev:");
		osCommandCriterion.setExecuteLocally(false);
		osCommandCriterion.setErrorMessage("Unable to connect using Navisphere");

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("host")
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Error in OsCommandCriterion test:\n" + osCommandCriterion.toString() + "\n\n" + "No credentials provided.",
			criterionTestResult.getMessage()
		);
		assertNull(criterionTestResult.getResult());
	}

	@Test
	@EnabledOnOs(WINDOWS)
	void testVisitOsCommandWindowsError() {
		final OsCommandCriterion osCommandCriterion = new OsCommandCriterion();
		osCommandCriterion.setCommandLine("PAUSE");
		osCommandCriterion.setExpectedResult(" ");
		osCommandCriterion.setExecuteLocally(true);
		osCommandCriterion.setErrorMessage("No date.");

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.build();

		final OsCommandConfiguration osCommandConfiguration = new OsCommandConfiguration();
		osCommandConfiguration.setTimeout(1L);

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("localhost")
			.hostType(DeviceKind.WINDOWS)
			.configurations(
				Map.of(sshConfiguration.getClass(), sshConfiguration, osCommandConfiguration.getClass(), osCommandConfiguration)
			)
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Error in OsCommandCriterion test:\n" +
			osCommandCriterion.toString() +
			"\n\n" +
			"TimeoutException: Command \"PAUSE\" execution has timed out after 1 s",
			criterionTestResult.getMessage()
		);
		assertNull(criterionTestResult.getResult());
	}

	@Test
	@EnabledOnOs(LINUX)
	void testVisitOsCommandLinuxError() {
		final OsCommandCriterion osCommandCriterion = new OsCommandCriterion();
		osCommandCriterion.setCommandLine("sleep 5");
		osCommandCriterion.setExpectedResult(" ");
		osCommandCriterion.setExecuteLocally(true);
		osCommandCriterion.setErrorMessage("No date.");
		osCommandCriterion.setTimeout(1L);

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.build();

		final OsCommandConfiguration osCommandConfiguration = new OsCommandConfiguration();
		osCommandConfiguration.setTimeout(1L);

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("localhost")
			.hostType(DeviceKind.WINDOWS)
			.configurations(
				Map.of(sshConfiguration.getClass(), sshConfiguration, osCommandConfiguration.getClass(), osCommandConfiguration)
			)
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Error in OsCommandCriterion test:\n" +
			osCommandCriterion.toString() +
			"\n\n" +
			"TimeoutException: Command \"sleep 5\" execution has timed out after 1 s",
			criterionTestResult.getMessage()
		);
		assertNull(criterionTestResult.getResult());
	}

	@Test
	@EnabledOnOs(WINDOWS)
	void testVisitOsCommandLocalWindowsFailedToMatchCriteria() {
		final String result = "Test";

		final OsCommandCriterion osCommandCriterion = new OsCommandCriterion();
		osCommandCriterion.setCommandLine("ECHO Test");
		osCommandCriterion.setExpectedResult("Nothing");
		osCommandCriterion.setExecuteLocally(true);
		osCommandCriterion.setErrorMessage("No display.");

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("localhost")
			.hostType(DeviceKind.WINDOWS)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"OsCommandCriterion test ran but failed:\n" +
			osCommandCriterion.toString() +
			"\n\n" +
			"Actual result:\n" +
			result,
			criterionTestResult.getMessage()
		);
		assertEquals(result, criterionTestResult.getResult());
	}

	@Test
	@EnabledOnOs(LINUX)
	void testVisitOsCommandLocalLinuxFailedToMatchCriteria() {
		final String result = "Test";

		final OsCommandCriterion osCommandCriterion = new OsCommandCriterion();
		osCommandCriterion.setCommandLine("echo Test");
		osCommandCriterion.setExpectedResult("Nothing");
		osCommandCriterion.setExecuteLocally(true);
		osCommandCriterion.setErrorMessage("No display.");

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.timeout(1L)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"OsCommandCriterion test ran but failed:\n" +
			osCommandCriterion.toString() +
			"\n\n" +
			"Actual result:\n" +
			result,
			criterionTestResult.getMessage()
		);
		assertEquals(result, criterionTestResult.getResult());
	}

	@Test
	@EnabledOnOs(WINDOWS)
	void testVisitOsCommandLocalWindows() {
		final String result = "Test";

		final OsCommandCriterion osCommandCriterion = new OsCommandCriterion();
		osCommandCriterion.setCommandLine("ECHO Test");
		osCommandCriterion.setExpectedResult(result);
		osCommandCriterion.setExecuteLocally(true);
		osCommandCriterion.setErrorMessage("No display.");

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.timeout(1L)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("localhost")
			.hostType(DeviceKind.WINDOWS)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"OsCommandCriterion test succeeded:\n" + osCommandCriterion.toString() + "\n\n" + "Result: " + result,
			criterionTestResult.getMessage()
		);
		assertEquals(result, criterionTestResult.getResult());
	}

	@Test
	@EnabledOnOs(LINUX)
	void testVisitOsCommandLocalLinux() {
		final String result = "Test";

		final OsCommandCriterion osCommandCriterion = new OsCommandCriterion();
		osCommandCriterion.setCommandLine("echo Test");
		osCommandCriterion.setExpectedResult(result);
		osCommandCriterion.setExecuteLocally(true);
		osCommandCriterion.setErrorMessage("No display.");

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.timeout(1L)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutorMock,
			telemetryManager,
			MY_CONNECTOR_1_NAME
		);

		final CriterionTestResult criterionTestResult = criterionProcessor.process(osCommandCriterion);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"OsCommandCriterion test succeeded:\n" + osCommandCriterion.toString() + "\n\n" + "Result: " + result,
			criterionTestResult.getMessage()
		);
		assertEquals(result, criterionTestResult.getResult());
	}
}
