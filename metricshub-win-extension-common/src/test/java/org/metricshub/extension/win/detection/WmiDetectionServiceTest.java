package org.metricshub.extension.win.detection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;
import org.metricshub.extension.win.WmiTestConfiguration;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;

@ExtendWith(MockitoExtension.class)
class WmiDetectionServiceTest {

	private static final String WMI_QUERY = "SELECT Name FROM Win32_Process";
	private static final String WMI_NAMESPACE = "root/cimv2";
	private static final String WQL_RESULT_VALUE = "metricshub";
	private static final List<List<String>> WQL_RESULT = List.of(List.of(WQL_RESULT_VALUE));
	private static final String CLIENT_ERROR_MSG = "error";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final String WQL_CUSTOM = "SELECT Name from Win32_Process WHERE Name = 'MetricsHub'";
	private static final String RESULT_MESSAGE_SHOULD_CONTAIN_RESULT = "Result message must contain " + WQL_RESULT_VALUE;

	@Mock
	IWinRequestExecutor winRequestExecutorMock;

	@InjectMocks
	WmiDetectionService wmiDetectionService;

	@Test
	void testProcessWmiCriterionSuccess() throws Exception {
		final IWinConfiguration winConfiguration = WmiTestConfiguration.builder().build();
		doReturn(List.of(List.of("MetricsHubServiceManager"), List.of("otelcol-contrib")))
			.when(winRequestExecutorMock)
			.executeWmi(HOST_NAME, winConfiguration, WMI_QUERY, WMI_NAMESPACE);
		final WmiCriterion wmiCriterion = WmiCriterion.builder().query(WMI_QUERY).expectedResult("metricshub").build();

		final CriterionTestResult result = wmiDetectionService.performDetectionTest(
			HOST_NAME,
			winConfiguration,
			wmiCriterion
		);
		assertTrue(result.isSuccess());
	}

	@Test
	void testProcessWmiCriterionActualResultIsNotExpectedResult() throws Exception {
		final IWinConfiguration winConfiguration = WmiTestConfiguration.builder().build();
		doReturn(List.of(List.of("otelcol-contrib")))
			.when(winRequestExecutorMock)
			.executeWmi(HOST_NAME, winConfiguration, WMI_QUERY, WMI_NAMESPACE);
		final WmiCriterion wmiCriterion = WmiCriterion.builder().query(WMI_QUERY).expectedResult("metricshub").build();

		final CriterionTestResult result = wmiDetectionService.performDetectionTest(
			HOST_NAME,
			winConfiguration,
			wmiCriterion
		);
		assertFalse(result.isSuccess());
	}

	@Test
	void testProcessWmiCriterionNoExpectedResult() throws Exception {
		final IWinConfiguration winConfiguration = WmiTestConfiguration.builder().build();
		doReturn(List.of(List.of("otelcol-contrib")))
			.when(winRequestExecutorMock)
			.executeWmi(HOST_NAME, winConfiguration, WMI_QUERY, WMI_NAMESPACE);
		final WmiCriterion wmiCriterion = WmiCriterion.builder().query(WMI_QUERY).build();

		final CriterionTestResult result = wmiDetectionService.performDetectionTest(
			HOST_NAME,
			winConfiguration,
			wmiCriterion
		);
		assertTrue(result.isSuccess());
	}

	@Test
	void testPerformDetectionTest() throws Exception {
		// Invalid parameters

		assertThrows(IllegalArgumentException.class, () -> wmiDetectionService.performDetectionTest(HOST_NAME, null, null));

		// ClientException

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration.builder().build();
		doThrow(new ClientException(CLIENT_ERROR_MSG, new TimeoutException()))
			.when(winRequestExecutorMock)
			.executeWmi(any(), eq(wmiConfiguration), any(), any());
		WmiCriterion wmiCriterion = WmiCriterion.builder().query(WQL_CUSTOM).build();
		CriterionTestResult result = wmiDetectionService.performDetectionTest(HOST_NAME, wmiConfiguration, wmiCriterion);
		assertFalse(result.isSuccess());
		assertNotNull(result.getException());
		assertTrue(result.getException() instanceof TimeoutException);

		// Empty result
		// ClientException
		doReturn(Collections.emptyList())
			.when(winRequestExecutorMock)
			.executeWmi(any(), eq(wmiConfiguration), any(), any());
		result = wmiDetectionService.performDetectionTest(HOST_NAME, wmiConfiguration, wmiCriterion);
		assertFalse(result.isSuccess());
		assertNull(result.getException());

		// Non-empty result, and no expected result => success

		doReturn(WQL_RESULT).when(winRequestExecutorMock).executeWmi(any(), eq(wmiConfiguration), any(), any());
		result = wmiDetectionService.performDetectionTest(HOST_NAME, wmiConfiguration, wmiCriterion);
		assertTrue(result.isSuccess());
		assertTrue(result.getMessage().contains(WQL_RESULT_VALUE), RESULT_MESSAGE_SHOULD_CONTAIN_RESULT);

		// Non-empty result, and matching expected result => success
		wmiCriterion = WmiCriterion.builder().query(WQL_CUSTOM).expectedResult(WQL_RESULT_VALUE).build();
		result = wmiDetectionService.performDetectionTest(HOST_NAME, wmiConfiguration, wmiCriterion);
		assertTrue(result.isSuccess());
		assertTrue(result.getMessage().contains(WQL_RESULT_VALUE), RESULT_MESSAGE_SHOULD_CONTAIN_RESULT);

		// Non-empty result, and non-matching expected result => failure
		doReturn(List.of(List.of("non-matching")))
			.when(winRequestExecutorMock)
			.executeWmi(any(), eq(wmiConfiguration), any(), any());
		wmiCriterion = WmiCriterion.builder().query(WQL_CUSTOM).expectedResult(WQL_RESULT_VALUE).build();
		result = wmiDetectionService.performDetectionTest(HOST_NAME, wmiConfiguration, wmiCriterion);
		assertFalse(result.isSuccess());
		assertNull(result.getException());
		assertTrue(result.getMessage().contains(WQL_RESULT_VALUE), RESULT_MESSAGE_SHOULD_CONTAIN_RESULT);
	}
}
