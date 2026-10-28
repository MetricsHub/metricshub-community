package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSelSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.ipmi.IpmiRequestExecutor.SelCursor;
import org.metricshub.extension.ipmi.IpmiRequestExecutor.SelRead;
import org.metricshub.ipmi.client.IpmiClientResults;
import org.metricshub.ipmi.client.IpmiTimeoutException;
import org.metricshub.ipmi.client.SelEntry;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IpmiExtensionTest {

	private static final String CONNECTOR_ID = "connector";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID();
	private static final String SUCCESS_RESPONSE = "Success";
	private static final String BMC_KEY = "0x0102";
	private static final String USERNAME = "testUser";
	private static final String PASSWORD = "testPassword";
	private static final String IPMI_CRITERION_TYPE = "ipmi";
	private static final String RESULT = "result";

	@Mock
	private IpmiRequestExecutor ipmiRequestExecutorMock;

	@InjectMocks
	private IpmiExtension ipmiExtension;

	private TelemetryManager telemetryManager;

	private void initIpmi() {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();

		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final IpmiConfiguration ipmiConfiguration = IpmiConfiguration.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.bmcKey(BMC_KEY)
			.timeout(120L)
			.build();

		telemetryManager = TelemetryManager.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.OOB)
					.configurations(Map.of(IpmiConfiguration.class, ipmiConfiguration))
					.build()
			)
			.build();
	}

	@Test
	void testCheckIpmiUpHealth() {
		initIpmi();

		// Mock successful IPMI protocol health check response
		doReturn(SUCCESS_RESPONSE)
			.when(ipmiRequestExecutorMock)
			.executeIpmiProbe(anyString(), any(IpmiConfiguration.class));

		// Start the IPMI Health Check strategy
		Optional<Boolean> result = ipmiExtension.checkProtocol(telemetryManager);

		assertTrue(result.get());
	}

	@Test
	void testCheckIpmiDownHealth() {
		initIpmi();

		// Mock null IPMI protocol health check response
		doReturn(null).when(ipmiRequestExecutorMock).executeIpmiProbe(anyString(), any(IpmiConfiguration.class));

		// Start the IPMI Health Check strategy
		Optional<Boolean> result = ipmiExtension.checkProtocol(telemetryManager);

		assertFalse(result.get());
	}

	@Test
	void testIsValidConfiguration() {
		assertTrue(ipmiExtension.isValidConfiguration(IpmiConfiguration.builder().build()));
		assertFalse(
			ipmiExtension.isValidConfiguration(
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

					@Override
					public String getProperty(String property) {
						return null;
					}

					@Override
					public boolean isCorrespondingProtocol(String protocol) {
						return false;
					}
				}
			)
		);
	}

	@Test
	void testGetSupportedSources() {
		assertFalse(ipmiExtension.getSupportedSources().isEmpty());
		assertTrue(ipmiExtension.getSupportedSources().contains(IpmiSource.class));
	}

	@Test
	void testGetSupportedCriteria() {
		assertFalse(ipmiExtension.getSupportedCriteria().isEmpty());
		assertTrue(ipmiExtension.getSupportedCriteria().contains(IpmiCriterion.class));
	}

	@Test
	void testGetConfigurationToSourceMapping() {
		assertFalse(ipmiExtension.getConfigurationToSourceMapping().isEmpty());
	}

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(ipmiExtension.isSupportedConfigurationType("ipmi"));
		assertFalse(ipmiExtension.isSupportedConfigurationType("snmp"));
	}

	@Test
	void testBuildConfiguration() throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		configuration.set("username", new TextNode(USERNAME));
		configuration.set("password", new TextNode(PASSWORD));
		configuration.set("timeout", new TextNode("120"));
		configuration.set("bmcKey", new TextNode(BMC_KEY));
		configuration.set("skipAuth", BooleanNode.valueOf(false));

		assertEquals(
			IpmiConfiguration.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.bmcKey(BMC_KEY)
				.timeout(120L)
				.build(),
			ipmiExtension.buildConfiguration(IPMI_CRITERION_TYPE, configuration, value -> value)
		);

		assertEquals(
			IpmiConfiguration.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.bmcKey(BMC_KEY)
				.timeout(120L)
				.build(),
			ipmiExtension.buildConfiguration(IPMI_CRITERION_TYPE, configuration, null)
		);
	}

	@Test
	void testProcessCriterionConfigurationNullTest() {
		initIpmi();

		telemetryManager.getHostConfiguration().setConfigurations(Map.of());

		final IpmiCriterion ipmiCriterion = IpmiCriterion.builder().type(IPMI_CRITERION_TYPE).build();

		assertEquals(
			CriterionTestResult.empty(),
			ipmiExtension.processCriterion(ipmiCriterion, CONNECTOR_ID, telemetryManager, true)
		);
	}

	@Test
	void testProcessCriterionNullResultTest() {
		initIpmi();

		final IpmiCriterion ipmiCriterion = IpmiCriterion.builder().type(IPMI_CRITERION_TYPE).build();

		final IpmiConfiguration configuration = (IpmiConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(IpmiConfiguration.class);

		doReturn(null).when(ipmiRequestExecutorMock).executeIpmiDetection(HOST_NAME, configuration);

		final String message =
			"Received <null> result after connecting to the IPMI BMC chip with the IPMI-over-LAN interface.";
		final CriterionTestResult criterionTestResult = ipmiExtension.processCriterion(
			ipmiCriterion,
			CONNECTOR_ID,
			telemetryManager,
			true
		);

		assertEquals(null, criterionTestResult.getResult());
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(message, criterionTestResult.getMessage());
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessCriterionOk() {
		initIpmi();

		final IpmiCriterion ipmiCriterion = IpmiCriterion.builder().type(IPMI_CRITERION_TYPE).build();

		final IpmiConfiguration configuration = (IpmiConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(IpmiConfiguration.class);

		doReturn(RESULT).when(ipmiRequestExecutorMock).executeIpmiDetection(HOST_NAME, configuration);

		final String message = "Successfully connected to the IPMI BMC chip with the IPMI-over-LAN interface.";
		final CriterionTestResult criterionTestResult = ipmiExtension.processCriterion(
			ipmiCriterion,
			CONNECTOR_ID,
			telemetryManager,
			true
		);

		assertEquals(RESULT, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(message, criterionTestResult.getMessage());
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessIpmiSourceOk() {
		initIpmi();

		final IpmiConfiguration configuration = (IpmiConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(IpmiConfiguration.class);

		doReturn(RESULT).when(ipmiRequestExecutorMock).executeIpmiGetSensors(HOST_NAME, configuration);
		final SourceTable actual = ipmiExtension.processSource(
			IpmiSource.builder().build(),
			CONNECTOR_ID,
			telemetryManager
		);

		final SourceTable expected = SourceTable.builder().rawData(RESULT).build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessIpmiSourceThrowsException() {
		initIpmi();

		final IpmiConfiguration configuration = (IpmiConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(IpmiConfiguration.class);

		doThrow(new RuntimeException("exception"))
			.when(ipmiRequestExecutorMock)
			.executeIpmiGetSensors(HOST_NAME, configuration);
		final SourceTable actual = ipmiExtension.processSource(
			IpmiSource.builder().build(),
			CONNECTOR_ID,
			telemetryManager
		);

		final SourceTable expected = SourceTable.empty();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessIpmiSourceEmptyResult() {
		initIpmi();

		final IpmiConfiguration configuration = (IpmiConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(IpmiConfiguration.class);

		{
			doReturn(null).when(ipmiRequestExecutorMock).executeIpmiGetSensors(HOST_NAME, configuration);
			final SourceTable actual = ipmiExtension.processSource(
				IpmiSource.builder().build(),
				CONNECTOR_ID,
				telemetryManager
			);

			final SourceTable expected = SourceTable.empty();
			assertEquals(expected, actual);
		}

		{
			doReturn("").when(ipmiRequestExecutorMock).executeIpmiGetSensors(HOST_NAME, configuration);
			final SourceTable actual = ipmiExtension.processSource(
				IpmiSource.builder().build(),
				CONNECTOR_ID,
				telemetryManager
			);

			final SourceTable expected = SourceTable.builder().rawData("").build();
			assertEquals(expected, actual);
		}
	}

	@Test
	void testProcessIpmiSourceNoIpmiConfiguration() {
		initIpmi();

		telemetryManager.getHostConfiguration().setConfigurations(Map.of());

		assertEquals(
			SourceTable.empty(),
			ipmiExtension.processSource(IpmiSource.builder().build(), CONNECTOR_ID, telemetryManager)
		);
	}

	@Test
	void testExecuteQuery() throws Exception {
		final String message = String.format("Successful IPMI query");
		initIpmi();
		doReturn(message).when(ipmiRequestExecutorMock).executeIpmiGetSensors(anyString(), any(IpmiConfiguration.class));
		IpmiConfiguration ipmiConfiguration = IpmiConfiguration.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(120L)
			.skipAuth(false)
			.bmcKey(BMC_KEY)
			.build();
		assertEquals(message, ipmiExtension.executeQuery(ipmiConfiguration, null));
	}

	@Test
	void testExecuteQueryThrows() throws Exception {
		initIpmi();
		doThrow(IpmiTimeoutException.class)
			.when(ipmiRequestExecutorMock)
			.executeIpmiGetSensors(anyString(), any(IpmiConfiguration.class));
		IpmiConfiguration ipmiConfiguration = IpmiConfiguration.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(120L)
			.skipAuth(false)
			.bmcKey(BMC_KEY)
			.build();
		assertThrows(Exception.class, () -> ipmiExtension.executeQuery(ipmiConfiguration, null));
	}

	@Test
	void testProcessIpmiRawSource() {
		initIpmi();
		final IpmiConfiguration configuration = (IpmiConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(IpmiConfiguration.class);
		final IpmiRawSource source = IpmiRawSource.builder().key("raw").netFn("6").command("1").build();

		doReturn("00;20 01").when(ipmiRequestExecutorMock).executeIpmiRaw(HOST_NAME, configuration, source);
		assertEquals(
			SourceTable.builder().table(List.of(List.of("00", "20 01"))).build(),
			ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager)
		);

		// A source that does not hold numbers is an error of the source
		final IpmiRawSource bad = IpmiRawSource.builder().key("bad").netFn("x").command("1").build();
		doThrow(new IllegalArgumentException("x"))
			.when(ipmiRequestExecutorMock)
			.executeIpmiRaw(HOST_NAME, configuration, bad);
		assertEquals(SourceTable.empty(), ipmiExtension.processSource(bad, CONNECTOR_ID, telemetryManager));
	}

	/**
	 * @return the configuration of the host with its hostname, as the MCP tool and the CLI pass it to executeQuery
	 */
	private IpmiConfiguration queryConfiguration() {
		final IpmiConfiguration configuration = (IpmiConfiguration) configuration().copy();
		configuration.setHostname(HOST_NAME);
		return configuration;
	}

	private IpmiConfiguration configuration() {
		return (IpmiConfiguration) telemetryManager.getHostConfiguration().getConfigurations().get(IpmiConfiguration.class);
	}

	/**
	 * @param id             the record ID
	 * @param epochSeconds   the timestamp
	 * @return the system event of {@link IpmiResultConverterTest#PSU_FAILURE_RECORD} at that ID and time
	 */
	private static SelEntry psuFailureAt(final int id, final long epochSeconds) {
		final byte[] record = IpmiResultConverterTest.PSU_FAILURE_RECORD.clone();
		record[0] = (byte) id;
		record[1] = (byte) (id >> 8);
		for (int i = 0; i < 4; i++) {
			record[3 + i] = (byte) (epochSeconds >> (8 * i));
		}
		return IpmiClientResults.selEntry(record, 0);
	}

	@Test
	void testProcessIpmiSelSource() {
		initIpmi();
		final IpmiConfiguration configuration = configuration();
		final ConnectorNamespace namespace = telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID);
		final IpmiSelSource source = IpmiSelSource.builder().key("sel").maxEventsPerPoll(1).build();
		final SelEntry entry5 = IpmiResultConverterTest.psuFailure(5);
		final SelEntry entry6 = IpmiResultConverterTest.psuFailure(6);
		final SelEntry entry7 = IpmiResultConverterTest.psuFailure(7);

		// First poll: the end of the log is located, nothing is read nor returned
		doReturn(5).when(ipmiRequestExecutorMock).executeIpmiSelLastId(HOST_NAME, configuration);
		assertEquals(SourceTable.empty(), ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager));
		assertEquals(new SelCursor(null, 5), namespace.getSourceCursor("sel"));
		verify(ipmiRequestExecutorMock, never()).executeIpmiSel(any(), any(), any(), anyBoolean());

		// Next poll: two new entries after record 5, one returned (maxEventsPerPoll), the cursor stops at it
		doReturn(new SelRead(IpmiClientResults.selEntryList(List.of(entry6, entry7), null), null))
			.when(ipmiRequestExecutorMock)
			.executeIpmiSel(HOST_NAME, configuration, new SelCursor(null, 5), true);
		final SourceTable table = ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager);
		assertEquals(1, table.getTable().size());
		assertEquals("6", table.getTable().get(0).get(0));
		assertEquals("", table.getTable().get(0).get(4));
		assertEquals(SelCursor.of(entry6), namespace.getSourceCursor("sel"));

		// Nothing new: the cursor stays
		doReturn(new SelRead(IpmiClientResults.selEntryList(List.of(), null), null))
			.when(ipmiRequestExecutorMock)
			.executeIpmiSel(HOST_NAME, configuration, SelCursor.of(entry6), true);
		assertTrue(ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager).getTable().isEmpty());
		assertEquals(SelCursor.of(entry6), namespace.getSourceCursor("sel"));

		// The log was cleared and refilled: its entries come back from the first one
		doReturn(new SelRead(IpmiClientResults.selEntryList(List.of(entry5), null), null))
			.when(ipmiRequestExecutorMock)
			.executeIpmiSel(HOST_NAME, configuration, SelCursor.of(entry6), true);
		assertEquals("5", ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager).getTable().get(0).get(0));
		assertEquals(SelCursor.of(entry5), namespace.getSourceCursor("sel"));
	}

	@Test
	void testProcessIpmiSelSourceEmptyLog() {
		initIpmi();
		final IpmiConfiguration configuration = configuration();
		final ConnectorNamespace namespace = telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID);
		final IpmiSelSource source = IpmiSelSource.builder().key("sel").build();
		final SelEntry entry5 = IpmiResultConverterTest.psuFailure(5);

		// An empty log on the first poll still marks the poll as done
		doReturn(0).when(ipmiRequestExecutorMock).executeIpmiSelLastId(HOST_NAME, configuration);
		assertTrue(ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager).getTable().isEmpty());
		assertEquals(new SelCursor(null, 0), namespace.getSourceCursor("sel"));

		// So that the first entry logged is returned by the next poll, which reads the whole log
		doReturn(new SelRead(IpmiClientResults.selEntryList(List.of(entry5), null), null))
			.when(ipmiRequestExecutorMock)
			.executeIpmiSel(HOST_NAME, configuration, new SelCursor(null, 0), true);
		assertEquals("5", ipmiExtension.processSource(source, CONNECTOR_ID, telemetryManager).getTable().get(0).get(0));
		assertEquals(SelCursor.of(entry5), namespace.getSourceCursor("sel"));
	}

	@Test
	void testExecuteQuerySel() throws Exception {
		initIpmi();
		final IpmiConfiguration configuration = queryConfiguration();
		final SelEntry old = IpmiResultConverterTest.psuFailure(5);
		final SelEntry recent = psuFailureAt(6, Instant.now().getEpochSecond() - 60);
		doReturn(new SelRead(IpmiClientResults.selEntryList(List.of(old, recent), null), null))
			.when(ipmiRequestExecutorMock)
			.executeIpmiSel(HOST_NAME, configuration, null, true);

		final String whole = ipmiExtension.executeQuery(
			configuration,
			JsonNodeFactory.instance.objectNode().put("query", "sel")
		);
		assertTrue(whole.startsWith(IpmiResultConverter.SEL_HEADER + "\n5;1593835520;"), whole);
		assertTrue(whole.contains("\n6;"), whole);

		final String lastHour = ipmiExtension.executeQuery(
			configuration,
			JsonNodeFactory.instance.objectNode().put("query", "SEL").put("since", "1h")
		);
		assertFalse(lastHour.contains("5;1593835520;"), lastHour);
		assertTrue(lastHour.contains("\n6;"), lastHour);
	}

	@Test
	void testExecuteQueryRaw() throws Exception {
		initIpmi();
		final IpmiConfiguration configuration = queryConfiguration();
		final IpmiRawSource expected = IpmiRawSource.builder().netFn("0x06").command("0x01").data("0x00").build();
		doReturn("00;20 01").when(ipmiRequestExecutorMock).executeIpmiRaw(HOST_NAME, configuration, expected);

		final ObjectNode query = JsonNodeFactory.instance.objectNode().put("query", "raw").put("netFn", "0x06");
		query.put("command", "0x01").put("data", "0x00");
		assertEquals("00;20 01", ipmiExtension.executeQuery(configuration, query));

		final ObjectNode unknown = JsonNodeFactory.instance.objectNode().put("query", "sol");
		assertThrows(IllegalArgumentException.class, () -> ipmiExtension.executeQuery(configuration, unknown));
	}
}
