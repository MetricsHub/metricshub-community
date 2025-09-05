package org.metricshub.extension.snmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.extension.snmp.AbstractSnmpExtension.GET;
import static org.metricshub.extension.snmp.AbstractSnmpExtension.GET_NEXT;
import static org.metricshub.extension.snmp.AbstractSnmpExtension.TABLE;
import static org.metricshub.extension.snmp.AbstractSnmpExtension.WALK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.SnmpConfiguration.SnmpVersion;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnmpExtensionTest {

	private static final String CONNECTOR_ID = "connector";
	public static final String OID = "1.3.6.1.4.1.674.10893.1.20";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	public static final String SNMP_GET_NEXT_SECOND_RESULT = "1.3.6.1.4.1.674.10893.1.20.1 ASN_INTEGER 1";
	public static final String SNMP_GET_NEXT_THIRD_RESULT = "1.3.6.1.4.1.674.10893.1.20.1 ASN_OCT 2.4.6";
	public static final String SNMP_GET_NEXT_FOURTH_RESULT = "1.3.6.1.4.1.674.10893.1.20.1 ASN_OCT";
	public static final String EXECUTE_SNMP_GET_RESULT = "CMC DELL";
	public static final String SNMP_VERSION = "2.4.6";
	public static final String SUCCESSFUL_SNMP_QUERY_RESULT = "Successful SNMP %s Query";
	public static final String[] COLUMNS_ARRAY = { "value1a", "value1b" };
	public static final List<List<String>> SNMP_TABLE_RESULT = Arrays.asList(
		Arrays.asList("value1a", "value2a", "value3a"),
		Arrays.asList("value1b", "value2b", "value3b")
	);

	@Mock
	private SnmpRequestExecutor snmpRequestExecutorMock;

	@InjectMocks
	private SnmpExtension snmpExtension;

	private TelemetryManager telemetryManager;

	/**
	 * Creates a TelemetryManager instance with an SNMP configuration.
	 */
	private void initSnmp() {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final SnmpConfiguration snmpConfiguration = SnmpConfiguration
			.builder()
			.community("public".toCharArray())
			.version(SnmpConfiguration.SnmpVersion.V1)
			.hostname(HOST_NAME)
			.port(161)
			.timeout(120L)
			.build();

		telemetryManager =
			TelemetryManager
				.builder()
				.monitors(monitors)
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(HOST_NAME)
						.hostId(HOST_NAME)
						.hostType(DeviceKind.LINUX)
						.configurations(Map.of(SnmpConfiguration.class, snmpConfiguration))
						.build()
				)
				.build();
	}

	@Test
	void testProcessSnmpGetNextCriterionException() throws Exception {
		initSnmp();

		doThrow(new TimeoutException("SNMPGetNext timeout"))
			.when(snmpRequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetNextCriterion.builder().oid(OID).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - SNMP test failed - SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 " +
					"was unsuccessful due to an exception. " +
					"Message: SNMPGetNext timeout. Connector ID: %s.",
					HOST_NAME,
					CONNECTOR_ID
				)
			)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetNextCriterionNullResult() throws Exception {
		initSnmp();

		doReturn(null).when(snmpRequestExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetNextCriterion.builder().oid(OID).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - SNMP test failed - SNMP GetNext " +
					"of 1.3.6.1.4.1.674.10893.1.20 was unsuccessful " +
					"due to a null result.",
					HOST_NAME
				)
			)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetNextCriterionEmptyResult() throws Exception {
		initSnmp();

		doReturn("").when(snmpRequestExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetNextCriterion.builder().oid(OID).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - SNMP test failed - SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 " +
					"was unsuccessful due to an empty result.",
					HOST_NAME
				)
			)
			.result("")
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetNextCriterionNotSameSubTreeOID() throws Exception {
		initSnmp();

		final String snmpGetNextResult = "1.3.6.1.4.1.674.99999.1.20.1 ASN_INTEGER 1";

		doReturn(snmpGetNextResult)
			.when(snmpRequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetNextCriterion.builder().oid(OID).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - SNMP test failed - SNMP " +
					"GetNext of 1.3.6.1.4.1.674.10893.1.20 was successful but the returned OID is" +
					" not under the same tree. Returned OID: 1.3.6.1.4.1.674.99999.1.20.1.",
					HOST_NAME
				)
			)
			.result(snmpGetNextResult)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetNextCriterionSuccessWithNoExpectedResult() throws Exception {
		initSnmp();

		doReturn(SNMP_GET_NEXT_SECOND_RESULT)
			.when(snmpRequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetNextCriterion.builder().oid(OID).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - Successful SNMP GetNext of " +
					"1.3.6.1.4.1.674.10893.1.20. Returned result: " +
					"1.3.6.1.4.1.674.10893.1.20.1 ASN_INTEGER 1.",
					HOST_NAME
				)
			)
			.result(SNMP_GET_NEXT_SECOND_RESULT)
			.success(true)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetNextCriterionExpectedResultNotMatches() throws Exception {
		initSnmp();

		doReturn(SNMP_GET_NEXT_SECOND_RESULT)
			.when(snmpRequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetNextCriterion.builder().oid(OID).expectedResult(SNMP_VERSION).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - SNMP test failed - " +
					"SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 was successful but the value of " +
					"the returned OID did not match with the expected result. Expected value: 2.4.6 - returned value 1.",
					HOST_NAME
				)
			)
			.result(SNMP_GET_NEXT_SECOND_RESULT)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetNextExpectedResultMatches() throws Exception {
		initSnmp();

		doReturn(SNMP_GET_NEXT_THIRD_RESULT)
			.when(snmpRequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetNextCriterion.builder().oid(OID).expectedResult(SNMP_VERSION).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - Successful SNMP GetNext of " +
					"1.3.6.1.4.1.674.10893.1.20. " +
					"Returned result: 1.3.6.1.4.1.674.10893.1.20.1 ASN_OCT 2.4.6.",
					HOST_NAME
				)
			)
			.result(SNMP_GET_NEXT_THIRD_RESULT)
			.success(true)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetNextCriterionExpectedResultCannotExtract() throws Exception {
		initSnmp();

		doReturn(SNMP_GET_NEXT_FOURTH_RESULT)
			.when(snmpRequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetNextCriterion.builder().oid(OID).expectedResult(SNMP_VERSION).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - SNMP test failed - SNMP GetNext " +
					"of 1.3.6.1.4.1.674.10893.1.20 was successful " +
					"but the value cannot be extracted. Returned result: 1.3.6.1.4.1.674.10893.1.20.1 ASN_OCT.",
					HOST_NAME
				)
			)
			.result(SNMP_GET_NEXT_FOURTH_RESULT)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetNextReturnsEmptyResult() {
		initSnmp();

		assertNull(
			snmpExtension
				.processCriterion(SnmpGetNextCriterion.builder().oid(OID).build(), CONNECTOR_ID, telemetryManager)
				.getResult()
		);
	}

	@Test
	void testProcessSnmpGetCriterionReturnsEmptyResult() {
		initSnmp();

		assertNull(
			snmpExtension
				.processCriterion(SnmpGetCriterion.builder().oid(OID).build(), CONNECTOR_ID, telemetryManager)
				.getResult()
		);
	}

	@Test
	void testProcessSnmpGetExpectedResultMatches() throws Exception {
		initSnmp();

		doReturn(EXECUTE_SNMP_GET_RESULT)
			.when(snmpRequestExecutorMock)
			.executeSNMPGet(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetCriterion.builder().oid(OID).expectedResult("CMC").build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - Successful SNMP Get of 1.3.6.1.4.1.674.10893.1.20. Returned result: CMC DELL",
					HOST_NAME
				)
			)
			.result(EXECUTE_SNMP_GET_RESULT)
			.success(true)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetExpectedResultNotMatches() throws Exception {
		initSnmp();

		doReturn(EXECUTE_SNMP_GET_RESULT)
			.when(snmpRequestExecutorMock)
			.executeSNMPGet(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetCriterion.builder().oid(OID).expectedResult(SNMP_VERSION).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - SNMP test failed - " +
					"SNMP Get of 1.3.6.1.4.1.674.10893.1.20 was successful but the value of the returned " +
					"OID did not match with the expected result. Expected value: 2.4.6 - returned value CMC DELL.",
					HOST_NAME
				)
			)
			.result(EXECUTE_SNMP_GET_RESULT)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetSuccessWithNoExpectedResult() throws Exception {
		initSnmp();

		doReturn(EXECUTE_SNMP_GET_RESULT)
			.when(snmpRequestExecutorMock)
			.executeSNMPGet(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetCriterion.builder().oid(OID).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - Successful SNMP Get of 1.3.6.1.4.1.674.10893.1.20. Returned result: CMC DELL.",
					HOST_NAME
				)
			)
			.result(EXECUTE_SNMP_GET_RESULT)
			.success(true)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetEmptyResult() throws Exception {
		initSnmp();

		doReturn("").when(snmpRequestExecutorMock).executeSNMPGet(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetCriterion.builder().oid(OID).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - SNMP test failed - SNMP Get " +
					"of 1.3.6.1.4.1.674.10893.1.20 was unsuccessful due to an empty result.",
					HOST_NAME
				)
			)
			.result("")
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetNullResult() throws Exception {
		initSnmp();

		doReturn(null).when(snmpRequestExecutorMock).executeSNMPGet(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetCriterion.builder().oid(OID).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - SNMP test failed - SNMP Get of 1.3.6.1.4.1.674.10893.1.20 was " +
					"unsuccessful due to a null result",
					HOST_NAME
				)
			)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetException() throws Exception {
		initSnmp();

		doThrow(new TimeoutException("SNMPGet timeout"))
			.when(snmpRequestExecutorMock)
			.executeSNMPGet(any(), any(), any(), eq(false), isNull());
		final CriterionTestResult actual = snmpExtension.processCriterion(
			SnmpGetCriterion.builder().oid(OID).build(),
			CONNECTOR_ID,
			telemetryManager
		);
		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.message(
				String.format(
					"Hostname %s - SNMP test failed - SNMP Get of 1.3.6.1.4.1.674.10893.1.20 was unsuccessful " +
					"due to an exception. " +
					"Message: SNMPGet timeout. Connector ID: %s.",
					HOST_NAME,
					CONNECTOR_ID
				)
			)
			.build();
		assertEquals(expected, actual);
	}

	@Test
	void testCheckSnmpUpHealth() throws InterruptedException, ExecutionException, TimeoutException {
		// Create a telemetry manager using an SNMP HostConfiguration.
		initSnmp();

		// Mock SNMP protocol health check response
		doReturn("success")
			.when(snmpRequestExecutorMock)
			.executeSNMPGetNext(
				eq(SnmpExtension.SNMP_OID),
				any(SnmpConfiguration.class),
				anyString(),
				anyBoolean(),
				isNull()
			);

		// Start the SNMP protocol check
		Optional<Boolean> result = snmpExtension.checkProtocol(telemetryManager);

		assertTrue(result.get());
	}

	@Test
	void testCheckSnmpDownHealth() throws InterruptedException, ExecutionException, TimeoutException {
		// Create a telemetry manager using an SNMP HostConfiguration.
		initSnmp();

		// Mock SNMP protocol health check response
		doReturn(null)
			.when(snmpRequestExecutorMock)
			.executeSNMPGetNext(
				eq(SnmpExtension.SNMP_OID),
				any(SnmpConfiguration.class),
				anyString(),
				anyBoolean(),
				isNull()
			);

		// Start the SNMP protocol check
		Optional<Boolean> result = snmpExtension.checkProtocol(telemetryManager);

		assertFalse(result.get());
	}

	@Test
	void testIsValidConfiguration() {
		assertTrue(snmpExtension.isValidConfiguration(SnmpConfiguration.builder().build()));
		assertFalse(
			snmpExtension.isValidConfiguration(
				new IConfiguration() {
					@Override
					public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {}

					@Override
					public String getHostname() {
						return null;
					}

					@Override
					public void setHostname(String hostname) {}

					@Override
					public IConfiguration copy() {
						return null;
					}

					@Override
					public void setTimeout(Long timeout) {}
				}
			)
		);
	}

	@Test
	void testGetSupportedSources() {
		assertFalse(snmpExtension.getSupportedSources().isEmpty());
	}

	@Test
	void testGetSupportedCriteria() {
		assertFalse(snmpExtension.getSupportedCriteria().isEmpty());
	}

	@Test
	void testGetConfigurationToSourceMapping() {
		assertFalse(snmpExtension.getConfigurationToSourceMapping().isEmpty());
	}

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(snmpExtension.isSupportedConfigurationType("snmp"));
		assertFalse(snmpExtension.isSupportedConfigurationType("http"));
	}

	@Test
	void testBuildConfiguration() throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		configuration.set("version", new TextNode("1"));
		configuration.set("community", new TextNode("public"));
		configuration.set("port", new IntNode(161));
		configuration.set("timeout", new TextNode("2m"));

		assertEquals(
			SnmpConfiguration
				.builder()
				.community("public".toCharArray())
				.port(161)
				.timeout(120L)
				.version(SnmpVersion.V1)
				.build(),
			snmpExtension.buildConfiguration("snmp", configuration, value -> value)
		);
		assertEquals(
			SnmpConfiguration
				.builder()
				.community("public".toCharArray())
				.port(161)
				.timeout(120L)
				.version(SnmpVersion.V1)
				.build(),
			snmpExtension.buildConfiguration("snmp", configuration, null)
		);

		final ObjectNode invalidConfiguration = JsonNodeFactory.instance.objectNode();
		invalidConfiguration.set("version", new TextNode("version un"));
		final InvalidConfigurationException exception = assertThrows(
			InvalidConfigurationException.class,
			() -> snmpExtension.buildConfiguration("snmp", invalidConfiguration, null)
		);
		assertTrue(exception.getMessage().contains("Error while reading SNMP Configuration"));
	}

	@Test
	void testExecuteGetQuery() throws Exception {
		final String message = String.format(SUCCESSFUL_SNMP_QUERY_RESULT, GET);
		initSnmp();
		doReturn(message)
			.when(snmpRequestExecutorMock)
			.executeSNMPGet(anyString(), any(SnmpConfiguration.class), anyString(), eq(false), isNull());

		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpQueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpQueryConfiguration.set("action", new TextNode(GET));
		snmpQueryConfiguration.set("oid", new TextNode(SnmpExtension.SNMP_OID));

		assertEquals(message, snmpExtension.executeQuery(snmpConfiguration, snmpQueryConfiguration));
	}

	@Test
	void testExecuteGetNextQuery() throws Exception {
		final String message = String.format(SUCCESSFUL_SNMP_QUERY_RESULT, GET_NEXT);
		initSnmp();
		doReturn(message)
			.when(snmpRequestExecutorMock)
			.executeSNMPGetNext(anyString(), any(SnmpConfiguration.class), anyString(), eq(false), isNull());

		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpQueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpQueryConfiguration.set("action", new TextNode(GET_NEXT));
		snmpQueryConfiguration.set("oid", new TextNode(SnmpExtension.SNMP_OID));

		assertEquals(message, snmpExtension.executeQuery(snmpConfiguration, snmpQueryConfiguration));
	}

	@Test
	void testExecuteWalkQuery() throws Exception {
		final String message = String.format(SUCCESSFUL_SNMP_QUERY_RESULT, WALK);
		initSnmp();
		doReturn(message)
			.when(snmpRequestExecutorMock)
			.executeSNMPWalk(anyString(), any(SnmpConfiguration.class), anyString(), eq(false), isNull());

		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpQueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpQueryConfiguration.set("action", new TextNode(WALK));
		snmpQueryConfiguration.set("oid", new TextNode(SnmpExtension.SNMP_OID));

		assertEquals(message, snmpExtension.executeQuery(snmpConfiguration, snmpQueryConfiguration));
	}

	@Test
	void testExecuteTableQuery() throws Exception {
		initSnmp();
		doReturn(SNMP_TABLE_RESULT)
			.when(snmpRequestExecutorMock)
			.executeSNMPTable(
				anyString(),
				any(String[].class),
				any(SnmpConfiguration.class),
				anyString(),
				eq(false),
				isNull()
			);

		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpQueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpQueryConfiguration.set("action", new TextNode(TABLE));

		final ArrayNode columns = snmpQueryConfiguration.putArray("columns");
		Arrays.stream(COLUMNS_ARRAY).forEach(columns::add);
		snmpQueryConfiguration.set("oid", new TextNode(SnmpExtension.SNMP_OID));

		assertEquals(
			TextTableHelper.generateTextTable(COLUMNS_ARRAY, SNMP_TABLE_RESULT),
			snmpExtension.executeQuery(snmpConfiguration, snmpQueryConfiguration)
		);
	}

	@Test
	void testThrowsExceptionWithQuery() throws Exception {
		initSnmp();
		doThrow(new InterruptedException())
			.when(snmpRequestExecutorMock)
			.executeSNMPWalk(anyString(), any(SnmpConfiguration.class), anyString(), eq(false), isNull());

		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpQueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpQueryConfiguration.set("action", new TextNode(WALK));
		snmpQueryConfiguration.set("oid", new TextNode(SnmpExtension.SNMP_OID));

		assertEquals("Failed Executing SNMP query", snmpExtension.executeQuery(snmpConfiguration, snmpQueryConfiguration));
	}

	@Test
	void testThrowsExceptionQuery() {
		initSnmp();

		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpQueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpQueryConfiguration.set("action", new TextNode(""));
		snmpQueryConfiguration.set("oid", new TextNode(SnmpExtension.SNMP_OID));

		assertEquals("Failed Executing SNMP query", snmpExtension.executeQuery(snmpConfiguration, snmpQueryConfiguration));
	}
}
