package org.metricshub.extension.snmp.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SnmpGetNextCriterionProcessorTest {

	@Mock
	private AbstractSnmpRequestExecutor snmpRequestExecutor;

	@Mock
	private Function<TelemetryManager, ISnmpConfiguration> configurationRetriever;

	private SnmpGetNextCriterionProcessor snmpGetNextCriterionProcessor;

	@BeforeEach
	public void setUp() {
		snmpGetNextCriterionProcessor = new SnmpGetNextCriterionProcessor(snmpRequestExecutor, configurationRetriever);
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
		when(configurationRetriever.apply(telemetryManager)).thenReturn(snmpConfiguration);

		String expectedOid = "1.3.6.1.2.1.1.1.0";
		String expectedResult = "1.3.6.1.2.1.1.1.0 OID_TYPE TestValue";
		String expectedHostname = "hostname";

		when(snmpRequestExecutor.executeSNMPGetNext(expectedOid, snmpConfiguration, expectedHostname, false))
			.thenReturn(expectedResult);

		SnmpGetNextCriterion snmpGetNextCriterion = SnmpGetNextCriterion
			.builder()
			.oid(expectedOid)
			.expectedResult("TestValue")
			.build();

		CriterionTestResult criterionTestResult = snmpGetNextCriterionProcessor.process(
			snmpGetNextCriterion,
			"connectorId",
			telemetryManager
		);

		assertNotNull(snmpConfiguration);
		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(expectedResult, criterionTestResult.getResult());
	}

	// Test case when snmpGetCriterion is null
	@Test
	public void testProcess_NullCriterion() {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		CriterionTestResult result = snmpGetNextCriterionProcessor.process(null, "connectorId", telemetryManager);
		assertFalse(result.isSuccess());
	}

	// Test case when snmpConfiguration is null
	@Test
	public void testProcess_NullSnmpConfiguration() {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		when(configurationRetriever.apply(any(TelemetryManager.class))).thenReturn(null);

		SnmpGetNextCriterion snmpGetNextCriterion = SnmpGetNextCriterion.builder().oid("1.3.6.1.2.1.1.1.0").build();

		CriterionTestResult result = snmpGetNextCriterionProcessor.process(
			snmpGetNextCriterion,
			"connectorId",
			telemetryManager
		);

		assertFalse(result.isSuccess());
	}

	// Test case when the requestExecutor throws an exception.
	@Test
	public void testProcess_SnmpRequestException() throws Exception {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		ISnmpConfiguration snmpConfiguration = mock(ISnmpConfiguration.class);
		when(configurationRetriever.apply(telemetryManager)).thenReturn(snmpConfiguration);

		when(
			snmpRequestExecutor.executeSNMPGetNext(
				any(String.class),
				any(ISnmpConfiguration.class),
				any(String.class),
				any(Boolean.class)
			)
		)
			.thenThrow((new RuntimeException("Test exception")));

		SnmpGetNextCriterion snmpGetNextCriterion = SnmpGetNextCriterion.builder().oid("1.3.6.1.2.1.1.1.0").build();

		CriterionTestResult result = snmpGetNextCriterionProcessor.process(
			snmpGetNextCriterion,
			"connectorId",
			telemetryManager
		);

		assertFalse(result.isSuccess());
	}

	@Test
	void testCheckSNMPGetNextValue_NullResult() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String result = null;
		String expectedResult = "expectedResult";

		CriterionTestResult criterionTestResult = SnmpGetNextCriterionProcessor.checkSNMPGetNextExpectedValue(
			hostname,
			oid,
			expectedResult,
			result
		);

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
	void testCheckSNMPGetNextValue_EmptyResult() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String result = "";

		CriterionTestResult criterionTestResult = SnmpGetNextCriterionProcessor.checkSNMPGetNextValue(
			hostname,
			oid,
			result
		);

		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Hostname " +
			hostname +
			" - SNMP test failed - SNMP GetNext of " +
			oid +
			" was unsuccessful due to an empty result.",
			criterionTestResult.getMessage()
		);
	}

	@Test
	void testCheckSNMPGetNextValue_InvalidOid() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String result = "1.4.6.1.0.0.1.2.0 Value";

		CriterionTestResult criterionTestResult = SnmpGetNextCriterionProcessor.checkSNMPGetNextValue(
			hostname,
			oid,
			result
		);

		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Hostname " +
			hostname +
			" - SNMP test failed - SNMP GetNext of " +
			oid +
			" was successful but the returned OID is not under the same tree. Returned OID: 1.4.6.1.0.0.1.2.0.",
			criterionTestResult.getMessage()
		);
	}

	@Test
	void testCheckSNMPGetNextValue_ValidResult() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String result = "1.3.6.1.2.1.1.1.0 Value";

		CriterionTestResult criterionTestResult = SnmpGetNextCriterionProcessor.checkSNMPGetNextValue(
			hostname,
			oid,
			result
		);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"Hostname " + hostname + " - Successful SNMP GetNext of " + oid + ". Returned result: " + result + ".",
			criterionTestResult.getMessage()
		);
	}

	@Test
	void testCheckSNMPGetNextResult_ExpectedValueNull_ValidResult() {
		String hostname = "hostname";
		String oid = "1.3.6.1.2.1.1.1.0";
		String expected = null;
		String result = "1.3.6.1.2.1.1.1.0 Value";

		CriterionTestResult criterionTestResult = SnmpGetNextCriterionProcessor.checkSNMPGetNextResult(
			hostname,
			oid,
			expected,
			result
		);

		assertEquals(
			"Hostname hostname - Successful SNMP GetNext of 1.3.6.1.2.1.1.1.0. Returned result: 1.3.6.1.2.1.1.1.0 Value.",
			criterionTestResult.getMessage()
		);
		assertTrue(criterionTestResult.isSuccess());
	}
}
