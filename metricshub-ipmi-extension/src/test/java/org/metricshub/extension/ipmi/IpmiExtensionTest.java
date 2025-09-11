package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientConfiguration;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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

		final IpmiConfiguration ipmiConfiguration = IpmiConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.bmcKey(BMC_KEY)
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
		try (MockedStatic<IpmiClient> staticIpmiClient = Mockito.mockStatic(IpmiClient.class)) {
			staticIpmiClient
				.when(() -> IpmiClient.getChassisStatusAsStringResult(any(IpmiClientConfiguration.class)))
				.thenReturn(SUCCESS_RESPONSE);

			// Start the IPMI Health Check strategy
			Optional<Boolean> result = ipmiExtension.checkProtocol(telemetryManager);

			assertTrue(result.get());
		}
	}

	@Test
	void testCheckIpmiDownHealth() {
		initIpmi();

		// Mock null IPMI protocol health check response
		try (MockedStatic<IpmiClient> staticIpmiClient = Mockito.mockStatic(IpmiClient.class)) {
			staticIpmiClient
				.when(() -> IpmiClient.getChassisStatusAsStringResult(any(IpmiClientConfiguration.class)))
				.thenReturn(null);

			// Start the IPMI Health Check strategy
			Optional<Boolean> result = ipmiExtension.checkProtocol(telemetryManager);

			assertFalse(result.get());
		}
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
			IpmiConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.bmcKey(BMC_KEY)
				.timeout(120L)
				.build(),
			ipmiExtension.buildConfiguration(IPMI_CRITERION_TYPE, configuration, value -> value)
		);

		assertEquals(
			IpmiConfiguration
				.builder()
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
			ipmiExtension.processCriterion(ipmiCriterion, CONNECTOR_ID, telemetryManager)
		);
	}

	@Test
	void testProcessCriterionNullResultTest() throws ExecutionException, InterruptedException, TimeoutException {
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
			telemetryManager
		);

		assertEquals(null, criterionTestResult.getResult());
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(message, criterionTestResult.getMessage());
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessCriterionOk() throws ExecutionException, InterruptedException, TimeoutException {
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
			telemetryManager
		);

		assertEquals(RESULT, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(message, criterionTestResult.getMessage());
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessIpmiSourceOk() throws ExecutionException, InterruptedException, TimeoutException {
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
	void testProcessIpmiSourceThrowsException() throws ExecutionException, InterruptedException, TimeoutException {
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
	void testProcessIpmiSourceEmptyResult() throws ExecutionException, InterruptedException, TimeoutException {
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
		IpmiConfiguration ipmiConfiguration = IpmiConfiguration
			.builder()
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
		doThrow(TimeoutException.class)
			.when(ipmiRequestExecutorMock)
			.executeIpmiGetSensors(anyString(), any(IpmiConfiguration.class));
		IpmiConfiguration ipmiConfiguration = IpmiConfiguration
			.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(120L)
			.skipAuth(false)
			.bmcKey(BMC_KEY)
			.build();
		assertThrows(Exception.class, () -> ipmiExtension.executeQuery(ipmiConfiguration, null));
	}
}
