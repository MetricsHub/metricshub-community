package org.metricshub.extension.snmpv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.extension.snmp.AbstractSnmpExtension.GET;
import static org.metricshub.extension.snmp.AbstractSnmpExtension.GET_NEXT;
import static org.metricshub.extension.snmp.AbstractSnmpExtension.TABLE;
import static org.metricshub.extension.snmp.AbstractSnmpExtension.WALK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType.HOST;

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
import org.metricshub.extension.snmpv3.SnmpV3Configuration.AuthType;
import org.metricshub.extension.snmpv3.SnmpV3Configuration.Privacy;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

@ExtendWith(MockitoExtension.class)
class SnmpV3ExtensionTest {

	private static final String CONNECTOR_ID = "connector";
	public static final String OID = "1.3.6.1.4.1.674.10893.1.20";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	public static final String SNMP_GET_NEXT_SECOND_RESULT = "1.3.6.1.4.1.674.10893.1.20.1 ASN_INTEGER 1";
	public static final String SNMP_GET_NEXT_THIRD_RESULT = "1.3.6.1.4.1.674.10893.1.20.1 ASN_OCT 2.4.6";
	public static final String SNMP_GET_NEXT_FOURTH_RESULT = "1.3.6.1.4.1.674.10893.1.20.1 ASN_OCT";
	public static final String EXECUTE_SNMP_GET_RESULT = "CMC DELL";
	public static final String SNMP_VERSION = "2.4.6";
	public static final String SUCCESSFUL_SNMPV3_QUERY_RESULT = "Successful SNMPv3 %s Query";
	public static final String[] COLUMNS_ARRAY = { "value1a", "value1b" };
	public static final List<List<String>> SNMP_TABLE_RESULT = Arrays.asList(
		Arrays.asList("value1a", "value2a", "value3a"),
		Arrays.asList("value1b", "value2b", "value3b")
	);

	@Mock
	private SnmpV3RequestExecutor snmpv3RequestExecutorMock;

	@InjectMocks
	private SnmpV3Extension snmpV3Extension;

	private TelemetryManager telemetryManager;

	/**
	 * Creates a TelemetryManager instance with an SNMP V3 configuration.
	 */
	private void initSnmp() {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final SnmpV3Configuration snmpV3Configuration = SnmpV3Configuration
			.builder()
			.port(161)
			.timeout(120L)
			.username("username")
			.retryIntervals(new int[] { 20, 30, 50 })
			.password("password".toCharArray())
			.authType(AuthType.NO_AUTH)
			.privacy(Privacy.AES)
			.privacyPassword("authPass".toCharArray())
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
						.configurations(Map.of(SnmpV3Configuration.class, snmpV3Configuration))
						.build()
				)
				.build();
	}

	@Test
	void testProcessSnmpGetNextCriterionException() throws Exception {
		initSnmp();

		doThrow(new TimeoutException("SNMPGetNext timeout"))
			.when(snmpv3RequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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

		doReturn(null).when(snmpv3RequestExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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

		doReturn("").when(snmpv3RequestExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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

		doReturn(snmpGetNextResult).when(snmpv3RequestExecutorMock).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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
			.when(snmpv3RequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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
			.when(snmpv3RequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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
			.when(snmpv3RequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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
			.when(snmpv3RequestExecutorMock)
			.executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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
			snmpV3Extension
				.processCriterion(SnmpGetNextCriterion.builder().oid(OID).build(), CONNECTOR_ID, telemetryManager)
				.getResult()
		);
	}

	@Test
	void testProcessSnmpGetCriterionReturnsEmptyResult() {
		initSnmp();

		assertNull(
			snmpV3Extension
				.processCriterion(SnmpGetCriterion.builder().oid(OID).build(), CONNECTOR_ID, telemetryManager)
				.getResult()
		);
	}

	@Test
	void testProcessSnmpGetExpectedResultMatches() throws Exception {
		initSnmp();

		doReturn(EXECUTE_SNMP_GET_RESULT).when(snmpv3RequestExecutorMock).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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

		doReturn(EXECUTE_SNMP_GET_RESULT).when(snmpv3RequestExecutorMock).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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

		doReturn(EXECUTE_SNMP_GET_RESULT).when(snmpv3RequestExecutorMock).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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

		doReturn("").when(snmpv3RequestExecutorMock).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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

		doReturn(null).when(snmpv3RequestExecutorMock).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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
			.when(snmpv3RequestExecutorMock)
			.executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = snmpV3Extension.processCriterion(
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
			.when(snmpv3RequestExecutorMock)
			.executeSNMPGetNext(eq(SnmpV3Extension.SNMP_OID), any(SnmpV3Configuration.class), anyString(), anyBoolean());

		// Start the SNMP protocol check
		Optional<Boolean> result = snmpV3Extension.checkProtocol(telemetryManager);

		assertTrue(result.get());
	}

	@Test
	void testCheckSnmpDownHealth() throws InterruptedException, ExecutionException, TimeoutException {
		// Create a telemetry manager using an SNMP HostConfiguration.
		initSnmp();

		// Mock SNMP protocol health check response
		doReturn(null)
			.when(snmpv3RequestExecutorMock)
			.executeSNMPGetNext(eq(SnmpV3Extension.SNMP_OID), any(SnmpV3Configuration.class), anyString(), anyBoolean());

		// Start the SNMP protocol check
		Optional<Boolean> result = snmpV3Extension.checkProtocol(telemetryManager);

		assertFalse(result.get());
	}

	@Test
	void testIsValidConfiguration() {
		assertTrue(snmpV3Extension.isValidConfiguration(SnmpV3Configuration.builder().build()));
		assertFalse(
			snmpV3Extension.isValidConfiguration(
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
				}
			)
		);
	}

	@Test
	void testGetSupportedSources() {
		assertFalse(snmpV3Extension.getSupportedSources().isEmpty());
	}

	@Test
	void testGetSupportedCriteria() {
		assertFalse(snmpV3Extension.getSupportedCriteria().isEmpty());
	}

	@Test
	void testGetConfigurationToSourceMapping() {
		assertFalse(snmpV3Extension.getConfigurationToSourceMapping().isEmpty());
	}

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(snmpV3Extension.isSupportedConfigurationType("snmpv3"));
		assertFalse(snmpV3Extension.isSupportedConfigurationType("http"));
	}

	@Test
	void testBuildConfiguration() throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		configuration.set("community", new TextNode("public"));
		configuration.set("password", new TextNode("passwordTest"));
		configuration.set("privacyPassword", new TextNode("privacyPasswordTest"));
		configuration.set("port", new IntNode(161));
		configuration.set("timeout", new TextNode("2m"));

		assertEquals(
			SnmpV3Configuration
				.builder()
				.password("passwordTest".toCharArray())
				.privacyPassword("privacyPasswordTest".toCharArray())
				.port(161)
				.timeout(120L)
				.build(),
			snmpV3Extension.buildConfiguration("snmpv3", configuration, value -> value)
		);

		assertEquals(
			SnmpV3Configuration
				.builder()
				.password("passwordTest".toCharArray())
				.privacyPassword("privacyPasswordTest".toCharArray())
				.port(161)
				.timeout(120L)
				.build(),
			snmpV3Extension.buildConfiguration("snmpv3", configuration, null)
		);
	}

	@Test
	void testExecuteGetQuery() throws Exception {
		final String message = String.format(SUCCESSFUL_SNMPV3_QUERY_RESULT, GET);
		initSnmp();
		doReturn(message)
			.when(snmpv3RequestExecutorMock)
			.executeSNMPGet(anyString(), any(SnmpV3Configuration.class), anyString(), eq(false));

		SnmpV3Configuration snmpV3Configuration = SnmpV3Configuration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpV3QueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpV3QueryConfiguration.set("action", new TextNode(GET));
		snmpV3QueryConfiguration.set("oid", new TextNode(SnmpV3Extension.SNMP_OID));

		assertEquals(message, snmpV3Extension.executeQuery(snmpV3Configuration, snmpV3QueryConfiguration));
	}

	@Test
	void testExecuteGetNextQuery() throws Exception {
		final String message = String.format(SUCCESSFUL_SNMPV3_QUERY_RESULT, GET_NEXT);
		initSnmp();
		doReturn(message)
			.when(snmpv3RequestExecutorMock)
			.executeSNMPGetNext(anyString(), any(SnmpV3Configuration.class), anyString(), eq(false));

		SnmpV3Configuration snmpV3Configuration = SnmpV3Configuration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpV3QueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpV3QueryConfiguration.set("action", new TextNode(GET_NEXT));
		snmpV3QueryConfiguration.set("oid", new TextNode(SnmpV3Extension.SNMP_OID));

		assertEquals(message, snmpV3Extension.executeQuery(snmpV3Configuration, snmpV3QueryConfiguration));
	}

	@Test
	void testExecuteWalkQuery() throws Exception {
		final String message = String.format(SUCCESSFUL_SNMPV3_QUERY_RESULT, WALK);
		initSnmp();
		doReturn(message)
			.when(snmpv3RequestExecutorMock)
			.executeSNMPWalk(anyString(), any(SnmpV3Configuration.class), anyString(), eq(false));

		SnmpV3Configuration snmpV3Configuration = SnmpV3Configuration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpV3QueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpV3QueryConfiguration.set("action", new TextNode(WALK));
		snmpV3QueryConfiguration.set("oid", new TextNode(SnmpV3Extension.SNMP_OID));

		assertEquals(message, snmpV3Extension.executeQuery(snmpV3Configuration, snmpV3QueryConfiguration));
	}

	@Test
	void testExecuteTableQuery() throws Exception {
		initSnmp();
		doReturn(SNMP_TABLE_RESULT)
			.when(snmpv3RequestExecutorMock)
			.executeSNMPTable(anyString(), any(String[].class), any(SnmpV3Configuration.class), anyString(), eq(false));

		SnmpV3Configuration snmpV3Configuration = SnmpV3Configuration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpv3QueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpv3QueryConfiguration.set("action", new TextNode(TABLE));
		final String[] columnsArray = { "value1a", "value1b" };
		final ArrayNode columns = snmpv3QueryConfiguration.putArray("columns");
		Arrays.stream(columnsArray).forEach(columns::add);
		snmpv3QueryConfiguration.set("oid", new TextNode(SnmpV3Extension.SNMP_OID));

		assertEquals(
			TextTableHelper.generateTextTable(columnsArray, SNMP_TABLE_RESULT),
			snmpV3Extension.executeQuery(snmpV3Configuration, snmpv3QueryConfiguration)
		);
	}

	@Test
	void testThrowsExceptionWithQuery() throws Exception {
		initSnmp();
		doThrow(new InterruptedException())
			.when(snmpv3RequestExecutorMock)
			.executeSNMPWalk(anyString(), any(SnmpV3Configuration.class), anyString(), eq(false));

		SnmpV3Configuration snmpV3Configuration = SnmpV3Configuration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpV3QueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpV3QueryConfiguration.set("action", new TextNode(WALK));
		snmpV3QueryConfiguration.set("oid", new TextNode(SnmpV3Extension.SNMP_OID));

		assertEquals(
			"Failed Executing SNMP query",
			snmpV3Extension.executeQuery(snmpV3Configuration, snmpV3QueryConfiguration)
		);
	}

	@Test
	void testThrowsExceptionQuery() {
		initSnmp();

		SnmpV3Configuration snmpV3Configuration = SnmpV3Configuration.builder().hostname(HOST_NAME).build();
		ObjectNode snmpV3QueryConfiguration = JsonNodeFactory.instance.objectNode();
		snmpV3QueryConfiguration.set("action", new TextNode(""));
		snmpV3QueryConfiguration.set("oid", new TextNode(SnmpV3Extension.SNMP_OID));

		assertEquals(
			"Failed Executing SNMP query",
			snmpV3Extension.executeQuery(snmpV3Configuration, snmpV3QueryConfiguration)
		);
	}
}
