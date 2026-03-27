package org.metricshub.extension.snmp.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SnmpGetCriterionProcessorTest {

	@Mock
	private AbstractSnmpRequestExecutor snmpRequestExecutor;

	@Mock
	private Function<TelemetryManager, ISnmpConfiguration> configurationRetriever;

	private SnmpGetCriterionProcessor snmpGetCriterionProcessor;

	@BeforeEach
	public void setUp() {
		snmpGetCriterionProcessor = new SnmpGetCriterionProcessor(snmpRequestExecutor, configurationRetriever, true);
	}

	/**
	 * Utility method to create a telemetryManager
	 *
	 * @return a configured telemetryManager instance
	 */
	private TelemetryManager createTelemetryManagerWithHostConfiguration() {
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("hostname")
			.configurations(Map.of())
			.build();
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		return telemetryManager;
	}

	// Test case for successful process
	@Test
	public void testProcess_Success() throws Exception {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		ISnmpConfiguration snmpConfiguration = mock(ISnmpConfiguration.class);
		when(configurationRetriever.apply(any(TelemetryManager.class))).thenReturn(snmpConfiguration);

		String expectedOid = "1.3.6.1.2.1.1.1.0";
		String expectedResult = "TestValue";
		String expectedHostname = "hostname";

		when(snmpRequestExecutor.executeSNMPGet(expectedOid, snmpConfiguration, expectedHostname, false, null, "hostname"))
			.thenReturn(expectedResult);

		SnmpGetCriterion snmpGetCriterion = SnmpGetCriterion
			.builder()
			.oid(expectedOid)
			.expectedResult(expectedResult)
			.build();

		CriterionTestResult criterionTestResult = snmpGetCriterionProcessor.process(
			snmpGetCriterion,
			"connectorId",
			telemetryManager
		);

		assertNotNull(snmpConfiguration);
		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
	}

	// Test case when snmpGetCriterion is null
	@Test
	public void testProcess_NullCriterion() {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		CriterionTestResult result = snmpGetCriterionProcessor.process(null, "connectorId", telemetryManager);
		assertFalse(result.isSuccess());
	}

	// Test case when snmpConfiguration is null
	@Test
	public void testProcess_NullSnmpConfiguration() {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		when(configurationRetriever.apply(any(TelemetryManager.class))).thenReturn(null);

		SnmpGetCriterion snmpGetCriterion = SnmpGetCriterion.builder().oid("1.3.6.1.2.1.1.1.0").build();

		CriterionTestResult result = snmpGetCriterionProcessor.process(snmpGetCriterion, "connectorId", telemetryManager);

		assertFalse(result.isSuccess());
	}

	// Test case when the requestExecutor throws an exception.
	@Test
	public void testProcess_SnmpRequestException() throws Exception {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		ISnmpConfiguration snmpConfiguration = mock(ISnmpConfiguration.class);
		when(configurationRetriever.apply(telemetryManager)).thenReturn(snmpConfiguration);

		when(
			snmpRequestExecutor.executeSNMPGet(
				any(String.class),
				any(ISnmpConfiguration.class),
				any(String.class),
				any(Boolean.class),
				isNull(),
				any()
			)
		)
			.thenThrow(new InterruptedException("Test exception"));

		SnmpGetCriterion snmpGetCriterion = SnmpGetCriterion.builder().oid("1.3.6.1.2.1.1.1.0").build();

		CriterionTestResult result = snmpGetCriterionProcessor.process(snmpGetCriterion, "connectorId", telemetryManager);

		assertFalse(result.isSuccess());
	}

	@Test
	void testCheckSNMPGetNextValue_NullResult() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String result = null;
		String expectedResult = "expectedResult";

		CriterionTestResult criterionTestResult = new SnmpGetNextCriterionProcessor(
			snmpRequestExecutor,
			configurationRetriever,
			true
		)
			.checkSNMPGetNextExpectedValue(hostname, oid, expectedResult, result);

		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Hostname " +
			hostname +
			" - SNMP test failed - SNMP GetNext of " +
			oid +
			" was unsuccessful due to a null result.",
			criterionTestResult.getMessage()
		);
	}

	@Test
	public void testCheckSNMPGetValue_ValidResult() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String result = "ValidResult";

		CriterionTestResult criterionTestResult = snmpGetCriterionProcessor.checkSNMPGetValue(hostname, oid, result);

		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"Hostname hostname - Successful SNMP Get of 1.3.6.1.2.1.1.1.0. Returned result: ValidResult.",
			criterionTestResult.getMessage()
		);
	}

	@Test
	public void testCheckSNMPGetResult_ExpectedNull_ValidResult() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String result = "ValidResult";

		CriterionTestResult criterionTestResult = snmpGetCriterionProcessor.checkSNMPGetResult(hostname, oid, null, result);

		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"Hostname hostname - Successful SNMP Get of 1.3.6.1.2.1.1.1.0. Returned result: ValidResult.",
			criterionTestResult.getMessage()
		);
	}

	@Test
	public void testCheckSNMPGetResult_ResultDoesNotMatchExpected() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String expected = "ExpectedValue";
		String result = "ActualValue";

		CriterionTestResult criterionTestResult = snmpGetCriterionProcessor.checkSNMPGetResult(
			hostname,
			oid,
			expected,
			result
		);

		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Hostname hostname - SNMP test failed - SNMP Get of 1.3.6.1.2.1.1.1.0 was successful but the value of the returned OID did not match with the expected result. Expected value: ExpectedValue - returned value ActualValue.",
			criterionTestResult.getMessage()
		);
	}

	@Test
	public void testCheckSNMPGetValue_NullResult() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String result = null;

		CriterionTestResult criterionTestResult = snmpGetCriterionProcessor.checkSNMPGetValue(hostname, oid, result);

		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Hostname hostname - SNMP test failed - SNMP Get of 1.3.6.1.2.1.1.1.0 was unsuccessful due to a null result",
			criterionTestResult.getMessage()
		);
	}

	@Test
	public void testCheckSNMPGetValue_EmptyResult() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String result = "";

		CriterionTestResult criterionTestResult = snmpGetCriterionProcessor.checkSNMPGetValue(hostname, oid, result);

		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Hostname hostname - SNMP test failed - SNMP Get of 1.3.6.1.2.1.1.1.0 was unsuccessful due to an empty result.",
			criterionTestResult.getMessage()
		);
	}

	// Test case when the requestExecutor throws a TimeoutException - should be marked as transient.
	@Test
	public void testProcessSnmpTimeoutException() throws Exception {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		ISnmpConfiguration snmpConfiguration = mock(ISnmpConfiguration.class);
		when(configurationRetriever.apply(telemetryManager)).thenReturn(snmpConfiguration);

		when(
			snmpRequestExecutor.executeSNMPGet(
				any(String.class),
				any(ISnmpConfiguration.class),
				any(String.class),
				any(Boolean.class),
				isNull(),
				any()
			)
		)
			.thenThrow(new TimeoutException("SNMP request timed out"));

		SnmpGetCriterion snmpGetCriterion = SnmpGetCriterion.builder().oid("1.3.6.1.2.1.1.1.0").build();

		CriterionTestResult result = snmpGetCriterionProcessor.process(snmpGetCriterion, "connectorId", telemetryManager);

		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().contains("timed out"));
	}

	// Test case when the requestExecutor throws a regular exception.
	@Test
	public void testProcessSnmpNonTransientException() throws Exception {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		ISnmpConfiguration snmpConfiguration = mock(ISnmpConfiguration.class);
		when(configurationRetriever.apply(telemetryManager)).thenReturn(snmpConfiguration);

		when(
			snmpRequestExecutor.executeSNMPGet(
				any(String.class),
				any(ISnmpConfiguration.class),
				any(String.class),
				any(Boolean.class),
				isNull(),
				any()
			)
		)
			.thenThrow(new RuntimeException("SNMP agent not available"));

		SnmpGetCriterion snmpGetCriterion = SnmpGetCriterion.builder().oid("1.3.6.1.2.1.1.1.0").build();

		CriterionTestResult result = snmpGetCriterionProcessor.process(snmpGetCriterion, "connectorId", telemetryManager);

		assertFalse(result.isSuccess());
	}
}
