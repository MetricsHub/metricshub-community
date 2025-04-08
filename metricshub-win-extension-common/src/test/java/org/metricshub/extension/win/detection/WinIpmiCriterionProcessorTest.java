package org.metricshub.extension.win.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WmiTestConfiguration;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WinIpmiCriterionProcessorTest {

	@Mock
	WmiDetectionService wmiDetectionServiceMock;

	@Mock
	Function<TelemetryManager, IWinConfiguration> configurationRetrieverMock;

	@InjectMocks
	WinIpmiCriterionProcessor winIpmiCriterionProcessor;

	private static final String SUCCESS_RESULT_VALUE = "success";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final char[] PASSWORD = "pwd".toCharArray();
	private static final String USERNAME = "user";

	@Test
	void testProcessIpmiNoConfiguration() {
		// Init configurations
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

		doReturn(null).when(configurationRetrieverMock).apply(telemetryManager);

		final IpmiCriterion ipmi = IpmiCriterion.builder().forceSerialization(true).build();

		final CriterionTestResult criterionTestResult = winIpmiCriterionProcessor.process(ipmi, telemetryManager);

		assertNotNull(criterionTestResult);
		assertTrue(
			criterionTestResult.getMessage().contains("Neither WMI nor WinRM credentials are configured for this host.")
		);
		assertFalse(criterionTestResult.isSuccess());
	}

	@Test
	void testProcessIpmi() {
		// Init configuration
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

		final IpmiCriterion ipmi = IpmiCriterion.builder().forceSerialization(true).build();

		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);

		// Mock performDetectionTest
		doReturn(CriterionTestResult.success(ipmi, SUCCESS_RESULT_VALUE))
			.when(wmiDetectionServiceMock)
			.performDetectionTest(any(), any(), any());

		final CriterionTestResult criterionTestResult = winIpmiCriterionProcessor.process(ipmi, telemetryManager);

		assertNotNull(criterionTestResult);
		assertEquals(SUCCESS_RESULT_VALUE, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
	}
}
