package org.metricshub.extension.win.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
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
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class WmiCriterionProcessorTest {

	@Mock
	WmiDetectionService wmiDetectionServiceMock;

	@Mock
	Function<TelemetryManager, IWinConfiguration> configurationRetrieverMock;

	WmiCriterionProcessor wmiCriterionProcessor;

	private static final String CONNECTOR_ID = "connector_id";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final char[] PASSWORD = "pwd".toCharArray();
	private static final String USERNAME = "user";
	private static final String WMI_QUERY = "SELECT Name FROM Win32_Process";
	private static final String FORCED_NAMESPACE = "namespace";
	private static final String FIRST_NAMESPACE = "namespace1";
	private static final String SECOND_NAMESPACE = "namespace2";
	private static final String WQL_CUSTOM = "SELECT Name from Win32_Process WHERE Name = 'MetricsHub'";
	private static final String WQL_RESULT_VALUE = "metricshub";
	private static final String CRITERION_DEFAULT_WMI_NAMESPACE = "root\\cimv2";
	private static final String TIMEOUT_MSG = "timeout";
	private static final String CLIENT_ERROR_MSG = "error";
	private static final String NOT_MATCHING_EXPECTED_RESULT = "^metrics[^huuuu]b";

	@BeforeEach
	void setup() {
		wmiCriterionProcessor =
			new WmiCriterionProcessor(wmiDetectionServiceMock, configurationRetrieverMock, CONNECTOR_ID);
	}

	@Test
	void testProcessWmiCriterionAutomaticNamespace() throws ClientException {
		final IWinRequestExecutor winRequestExecutorMock = spy(IWinRequestExecutor.class);
		doReturn(winRequestExecutorMock).when(wmiDetectionServiceMock).getWinRequestExecutor();
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
		final WmiCriterion wmiCriterion = WmiCriterion
			.builder()
			.query(WQL_CUSTOM)
			.namespace(MetricsHubConstants.AUTOMATIC_NAMESPACE)
			.build();
		doReturn(CriterionTestResult.success(wmiCriterion, WQL_RESULT_VALUE))
			.when(wmiDetectionServiceMock)
			.performDetectionTest(any(), eq(wmiConfiguration), any());
		doReturn(List.of(List.of(FIRST_NAMESPACE)))
			.when(winRequestExecutorMock)
			.executeWmi(any(), eq(wmiConfiguration), any(), any());

		final CriterionTestResult result = wmiCriterionProcessor.process(wmiCriterion, telemetryManager);
		assertTrue(result.isSuccess());
		assertTrue(result.getMessage().contains("WmiCriterion test succeeded"));
	}

	@Test
	void testProcessWmiCriterionNamespaceDefined() throws ClientException {
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
		final String namespace = "root/" + FIRST_NAMESPACE;
		telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID).setAutomaticWmiNamespace(namespace);
		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);
		final WmiCriterion wmiCriterion = WmiCriterion.builder().query(WQL_CUSTOM).namespace(namespace).build();
		doReturn(CriterionTestResult.success(wmiCriterion, WQL_RESULT_VALUE))
			.when(wmiDetectionServiceMock)
			.performDetectionTest(any(), eq(wmiConfiguration), eq(wmiCriterion));

		final CriterionTestResult result = wmiCriterionProcessor.process(wmiCriterion, telemetryManager);
		assertTrue(result.isSuccess());
		final String message = result.getMessage();
		assertTrue(message.contains("WmiCriterion test succeeded"));
		assertTrue(message.contains(namespace));
	}

	@Test
	void testProcessWmiCriterionAutomaticNamespaceCached() throws ClientException {
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
		final String cachedNamespace = "root/" + FIRST_NAMESPACE;
		telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID).setAutomaticWmiNamespace(cachedNamespace);
		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);
		final WmiCriterion wmiCriterion = WmiCriterion
			.builder()
			.query(WQL_CUSTOM)
			.namespace(MetricsHubConstants.AUTOMATIC_NAMESPACE)
			.build();
		final WmiCriterion cachedNamespaceCriterion = wmiCriterion.copy();
		cachedNamespaceCriterion.setNamespace(cachedNamespace);
		doReturn(CriterionTestResult.success(cachedNamespaceCriterion, WQL_RESULT_VALUE))
			.when(wmiDetectionServiceMock)
			.performDetectionTest(any(), eq(wmiConfiguration), eq(cachedNamespaceCriterion));

		final CriterionTestResult result = wmiCriterionProcessor.process(wmiCriterion, telemetryManager);
		assertTrue(result.isSuccess());
		final String message = result.getMessage();
		assertTrue(message.contains("WmiCriterion test succeeded"));
		assertTrue(message.contains(cachedNamespace));
	}

	@Test
	void testProcessWmiCriterionMalformed() throws Exception {
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

		final CriterionTestResult result = wmiCriterionProcessor.process(null, telemetryManager);
		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains("Malformed criterion. Cannot perform detection."));
	}

	@Test
	void testProcessWmiCriterionWithNullWmiTestConfiguration() throws Exception {
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(HOST_NAME).hostId(HOST_NAME).configurations(Map.of()).build()
			)
			.build();
		doReturn(null).when(configurationRetrieverMock).apply(telemetryManager);

		final WmiCriterion wmiCriterion = WmiCriterion.builder().query(WMI_QUERY).build();

		final CriterionTestResult result = wmiCriterionProcessor.process(wmiCriterion, telemetryManager);
		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains("Neither WMI nor WinRM credentials are configured for this host."));
	}

	@Test
	void testWmiFindPossibleNamespacesForcedProtocol() {
		// Namespace is forced
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration.builder().namespace(FORCED_NAMESPACE).build();

		final WmiCriterionProcessor.PossibleNamespacesResult result = wmiCriterionProcessor.findPossibleNamespaces(
			null,
			wmiConfiguration
		);
		assertTrue(result.isSuccess());
		assertEquals(FORCED_NAMESPACE, result.getPossibleNamespaces().stream().findFirst().orElseThrow());
	}

	@Test
	void testWmiFindPossibleNamespacesNoResponse() throws Exception {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.build();

		final IWinRequestExecutor winRequestExecutorMock = spy(IWinRequestExecutor.class);
		doReturn(winRequestExecutorMock).when(wmiDetectionServiceMock).getWinRequestExecutor();
		// No response from the host
		doThrow(new ClientException(CLIENT_ERROR_MSG, new TimeoutException(TIMEOUT_MSG)))
			.when(winRequestExecutorMock)
			.executeWmi(any(), eq(wmiConfiguration), any(), any());

		final WmiCriterionProcessor.PossibleNamespacesResult result = wmiCriterionProcessor.findPossibleNamespaces(
			HOST_NAME,
			wmiConfiguration
		);
		assertFalse(result.isSuccess());
		assertTrue(result.getErrorMessage().contains(TIMEOUT_MSG));
	}

	@Test
	void testWmiFindPossibleNamespacesEmpty() throws Exception {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.build();

		final IWinRequestExecutor winRequestExecutorMock = spy(IWinRequestExecutor.class);
		doReturn(winRequestExecutorMock).when(wmiDetectionServiceMock).getWinRequestExecutor();

		// We return an empty list
		doReturn(Collections.emptyList())
			.when(winRequestExecutorMock)
			.executeWmi(any(), eq(wmiConfiguration), any(), eq(WmiCriterionProcessor.ROOT_NAMESPACE));

		final WmiCriterionProcessor.PossibleNamespacesResult result = wmiCriterionProcessor.findPossibleNamespaces(
			HOST_NAME,
			wmiConfiguration
		);
		assertFalse(result.isSuccess());
	}

	@Test
	void testWmiFindPossibleNamespaces() throws Exception {
		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.build();

		final IWinRequestExecutor winRequestExecutorMock = spy(IWinRequestExecutor.class);
		doReturn(winRequestExecutorMock).when(wmiDetectionServiceMock).getWinRequestExecutor();

		doReturn(List.of(List.of(FIRST_NAMESPACE), List.of(SECOND_NAMESPACE)))
			.when(winRequestExecutorMock)
			.executeWmi(any(), eq(wmiConfiguration), any(), any());

		final WmiCriterionProcessor.PossibleNamespacesResult result = wmiCriterionProcessor.findPossibleNamespaces(
			HOST_NAME,
			wmiConfiguration
		);
		assertTrue(result.isSuccess());
		assertEquals(2, result.getPossibleNamespaces().size());
		assertTrue(result.getPossibleNamespaces().contains(WmiCriterionProcessor.ROOT_NAMESPACE + "/" + FIRST_NAMESPACE));
		assertTrue(result.getPossibleNamespaces().contains(WmiCriterionProcessor.ROOT_NAMESPACE + "/" + SECOND_NAMESPACE));
	}

	@Test
	void testDetectNamespaceNoResponse() throws Exception {
		// No response at all => we fail early (we don't try every single namespace)
		final IWinRequestExecutor winRequestExecutorMock = spy(IWinRequestExecutor.class);
		doReturn(winRequestExecutorMock).when(wmiDetectionServiceMock).getWinRequestExecutor();

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration.builder().build();
		final WmiCriterion wmiCriterion = WmiCriterion
			.builder()
			.query(WQL_CUSTOM)
			.expectedResult(NOT_MATCHING_EXPECTED_RESULT)
			.build();
		doReturn(
			CriterionTestResult.error(
				wmiCriterion,
				TimeoutException.class.getSimpleName(),
				new ClientException(CLIENT_ERROR_MSG, new TimeoutException())
			)
		)
			.when(wmiDetectionServiceMock)
			.performDetectionTest(any(), eq(wmiConfiguration), any());

		final WmiCriterionProcessor.NamespaceResult result = wmiCriterionProcessor.detectNamespace(
			HOST_NAME,
			wmiConfiguration,
			wmiCriterion,
			Set.of(FIRST_NAMESPACE, SECOND_NAMESPACE)
		);
		assertFalse(result.getResult().isSuccess());
		assertTrue(result.getResult().getMessage().contains(TimeoutException.class.getSimpleName()));
		verify(wmiDetectionServiceMock).performDetectionTest(any(), eq(wmiConfiguration), any());
	}

	@Test
	void testDetectNamespaceEmpty() throws Exception {
		// Non-matching result AND empty result (with an error that doesn't stop the loop)

		final IWinRequestExecutor winRequestExecutorMock = spy(IWinRequestExecutor.class);
		doReturn(winRequestExecutorMock).when(wmiDetectionServiceMock).getWinRequestExecutor();

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration.builder().build();
		final WmiCriterion wmiCriterion = WmiCriterion
			.builder()
			.query(WQL_CUSTOM)
			.expectedResult(NOT_MATCHING_EXPECTED_RESULT)
			.build();
		final ClientException clientException = new ClientException(
			CLIENT_ERROR_MSG,
			new Exception("WBEM_E_INVALID_NAMESPACE")
		);
		final WmiCriterion copyCriterion1 = wmiCriterion.copy();
		copyCriterion1.setNamespace(FIRST_NAMESPACE);
		doReturn(CriterionTestResult.error(wmiCriterion, TimeoutException.class.getSimpleName(), clientException))
			.when(wmiDetectionServiceMock)
			.performDetectionTest(any(), any(), eq(copyCriterion1));
		doReturn(true).when(winRequestExecutorMock).isAcceptableException(clientException);
		final WmiCriterion copyCriterion2 = wmiCriterion.copy();
		copyCriterion2.setNamespace(SECOND_NAMESPACE);
		doReturn(CriterionTestResult.error(wmiCriterion, CLIENT_ERROR_MSG))
			.when(wmiDetectionServiceMock)
			.performDetectionTest(any(), any(), eq(copyCriterion2));

		final WmiCriterionProcessor.NamespaceResult result = wmiCriterionProcessor.detectNamespace(
			HOST_NAME,
			wmiConfiguration,
			wmiCriterion,
			Set.of(FIRST_NAMESPACE, SECOND_NAMESPACE)
		);
		assertFalse(result.getResult().isSuccess());
		assertNull(result.getResult().getException());
		verify(wmiDetectionServiceMock, times(2)).performDetectionTest(anyString(), any(), any());
	}

	@Test
	void testDetectNamespace() throws Exception {
		// 3 matching result, and root\\cimv2 must be removed

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration.builder().build();
		final WmiCriterion wmiCriterion = WmiCriterion.builder().query(WQL_CUSTOM).build();
		doReturn(CriterionTestResult.success(wmiCriterion, WQL_RESULT_VALUE))
			.when(wmiDetectionServiceMock)
			.performDetectionTest(any(), eq(wmiConfiguration), any());

		final WmiCriterionProcessor.NamespaceResult result = wmiCriterionProcessor.detectNamespace(
			HOST_NAME,
			wmiConfiguration,
			wmiCriterion,
			Set.of(CRITERION_DEFAULT_WMI_NAMESPACE, FIRST_NAMESPACE)
		);
		assertTrue(result.getResult().isSuccess());
		assertNull(result.getResult().getException());
		assertEquals(FIRST_NAMESPACE, result.getNamespace());
		verify(wmiDetectionServiceMock, times(2)).performDetectionTest(any(), any(), any());
	}

	@Test
	void testDetectNamespaceCimv2() throws Exception {
		// 1 single matching result: root\\cimv2 which must not be removed

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration.builder().build();
		final WmiCriterion wmiCriterion = WmiCriterion.builder().query(WQL_CUSTOM).build();
		doReturn(CriterionTestResult.success(wmiCriterion, WQL_RESULT_VALUE))
			.when(wmiDetectionServiceMock)
			.performDetectionTest(any(), eq(wmiConfiguration), any());

		final WmiCriterionProcessor.NamespaceResult result = wmiCriterionProcessor.detectNamespace(
			HOST_NAME,
			wmiConfiguration,
			wmiCriterion,
			Set.of(CRITERION_DEFAULT_WMI_NAMESPACE)
		);
		assertTrue(result.getResult().isSuccess());
		assertEquals(CRITERION_DEFAULT_WMI_NAMESPACE, result.getNamespace());
	}
}
