package org.metricshub.extension.snmp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractSnmpExtensionTest {

	private static final String HOSTNAME = "test-host";
	private static final String TEST_OID = "1.3.6.1";
	private static final String IDENTIFIER = "snmp";

	@Mock
	private AbstractSnmpRequestExecutor requestExecutor;

	@Mock
	private ISnmpConfiguration snmpConfiguration;

	private AbstractSnmpExtension extension;

	@BeforeEach
	void setUp() {
		extension =
			new AbstractSnmpExtension() {
				@Override
				protected AbstractSnmpRequestExecutor getRequestExecutor() {
					return requestExecutor;
				}

				@Override
				protected Class<? extends ISnmpConfiguration> getConfigurationClass() {
					return ISnmpConfiguration.class;
				}

				@Override
				public String getIdentifier() {
					return IDENTIFIER;
				}

				@Override
				public IConfiguration buildConfiguration(
					String configurationType,
					JsonNode jsonNode,
					UnaryOperator<char[]> decrypt
				) throws InvalidConfigurationException {
					return null;
				}

				@Override
				public boolean isValidConfiguration(IConfiguration configuration) {
					return configuration instanceof ISnmpConfiguration;
				}
			};
	}

	private TelemetryManager createTelemetryManager(final ISnmpConfiguration config) {
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = config != null
			? Map.of(ISnmpConfiguration.class, config)
			: Map.of();
		return TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).configurations(configurations).build())
			.build();
	}

	@Test
	void testGetSupportedSources() {
		final var sources = extension.getSupportedSources();
		assertEquals(2, sources.size());
		assertTrue(sources.contains(SnmpTableSource.class));
		assertTrue(sources.contains(SnmpGetSource.class));
	}

	@Test
	void testGetSupportedCriteria() {
		final var criteria = extension.getSupportedCriteria();
		assertEquals(2, criteria.size());
		assertTrue(criteria.contains(SnmpGetCriterion.class));
		assertTrue(criteria.contains(SnmpGetNextCriterion.class));
	}

	@Test
	void testCheckProtocolNoConfiguration() {
		final TelemetryManager telemetryManager = createTelemetryManager(null);
		assertEquals(Optional.empty(), extension.checkProtocol(telemetryManager));
	}

	@Test
	void testCheckProtocolSuccess() throws Exception {
		when(snmpConfiguration.getHostname()).thenReturn(HOSTNAME);
		final TelemetryManager telemetryManager = createTelemetryManager(snmpConfiguration);
		when(requestExecutor.executeSNMPGetNext(anyString(), any(), anyString(), anyBoolean(), isNull()))
			.thenReturn("result");
		final Optional<Boolean> result = extension.checkProtocol(telemetryManager);
		assertTrue(result.isPresent());
		assertTrue(result.get());
	}

	@Test
	void testCheckProtocolFailure() throws Exception {
		when(snmpConfiguration.getHostname()).thenReturn(HOSTNAME);
		final TelemetryManager telemetryManager = createTelemetryManager(snmpConfiguration);
		when(requestExecutor.executeSNMPGetNext(anyString(), any(), anyString(), anyBoolean(), isNull()))
			.thenThrow(new TimeoutException("timeout"));
		final Optional<Boolean> result = extension.checkProtocol(telemetryManager);
		assertTrue(result.isPresent());
		assertFalse(result.get());
	}

	@Test
	void testProcessSourceSnmpTable() throws Exception {
		final TelemetryManager telemetryManager = createTelemetryManager(snmpConfiguration);
		final SnmpTableSource source = SnmpTableSource.builder().oid(TEST_OID).selectColumns("1,2").build();
		when(requestExecutor.executeSNMPTable(anyString(), any(String[].class), any(), anyString(), anyBoolean(), isNull()))
			.thenReturn(List.of(List.of("a", "b")));
		final SourceTable result = extension.processSource(source, "connector1", telemetryManager);
		assertNotNull(result);
	}

	@Test
	void testProcessSourceSnmpGet() throws Exception {
		final TelemetryManager telemetryManager = createTelemetryManager(snmpConfiguration);
		final SnmpGetSource source = SnmpGetSource.builder().oid(TEST_OID).build();
		when(requestExecutor.executeSNMPGet(anyString(), any(), anyString(), anyBoolean(), isNull())).thenReturn("value");
		final SourceTable result = extension.processSource(source, "connector1", telemetryManager);
		assertNotNull(result);
	}

	@Test
	void testProcessCriterionSnmpGet() throws Exception {
		final TelemetryManager telemetryManager = createTelemetryManager(snmpConfiguration);
		final SnmpGetCriterion criterion = SnmpGetCriterion.builder().oid(TEST_OID).build();
		when(requestExecutor.executeSNMPGet(anyString(), any(), anyString(), anyBoolean(), isNull())).thenReturn("value");
		final CriterionTestResult result = extension.processCriterion(criterion, "connector1", telemetryManager, true);
		assertNotNull(result);
	}

	@Test
	void testProcessCriterionSnmpGetNext() throws Exception {
		final TelemetryManager telemetryManager = createTelemetryManager(snmpConfiguration);
		final SnmpGetNextCriterion criterion = SnmpGetNextCriterion.builder().oid(TEST_OID).build();
		when(requestExecutor.executeSNMPGetNext(anyString(), any(), anyString(), anyBoolean(), isNull()))
			.thenReturn("value");
		final CriterionTestResult result = extension.processCriterion(criterion, "connector1", telemetryManager, true);
		assertNotNull(result);
	}

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(extension.isSupportedConfigurationType("snmp"));
		assertTrue(extension.isSupportedConfigurationType("SNMP"));
		assertFalse(extension.isSupportedConfigurationType("wbem"));
	}

	@Test
	void testGetConfigurationToSourceMapping() {
		final var mapping = extension.getConfigurationToSourceMapping();
		assertNotNull(mapping);
		assertTrue(mapping.containsKey(ISnmpConfiguration.class));
		assertEquals(2, mapping.get(ISnmpConfiguration.class).size());
	}

	@Test
	void testExecuteQueryGet() throws Exception {
		final ObjectMapper mapper = new ObjectMapper();
		final JsonNode queryNode = mapper.readTree("{\"action\": \"get\", \"oid\": \"1.3.6.1\"}");
		when(snmpConfiguration.getHostname()).thenReturn(HOSTNAME);
		when(requestExecutor.executeSNMPGet(anyString(), any(), anyString(), anyBoolean(), isNull())).thenReturn("result");
		final String result = extension.executeQuery(snmpConfiguration, queryNode);
		assertEquals("result", result);
	}

	@Test
	void testExecuteQueryGetNext() throws Exception {
		final ObjectMapper mapper = new ObjectMapper();
		final JsonNode queryNode = mapper.readTree("{\"action\": \"getnext\", \"oid\": \"1.3.6.1\"}");
		when(snmpConfiguration.getHostname()).thenReturn(HOSTNAME);
		when(requestExecutor.executeSNMPGetNext(anyString(), any(), anyString(), anyBoolean(), isNull()))
			.thenReturn("result");
		final String result = extension.executeQuery(snmpConfiguration, queryNode);
		assertEquals("result", result);
	}

	@Test
	void testExecuteQueryWalk() throws Exception {
		final ObjectMapper mapper = new ObjectMapper();
		final JsonNode queryNode = mapper.readTree("{\"action\": \"walk\", \"oid\": \"1.3.6.1\"}");
		when(snmpConfiguration.getHostname()).thenReturn(HOSTNAME);
		when(requestExecutor.executeSNMPWalk(anyString(), any(), anyString(), anyBoolean(), isNull()))
			.thenReturn("walk-result");
		final String result = extension.executeQuery(snmpConfiguration, queryNode);
		assertEquals("walk-result", result);
	}

	@Test
	void testExecuteQueryTable() throws Exception {
		final ObjectMapper mapper = new ObjectMapper();
		final JsonNode queryNode = mapper.readTree(
			"{\"action\": \"table\", \"oid\": \"1.3.6.1\", \"columns\": [\"1\", \"2\"]}"
		);
		when(snmpConfiguration.getHostname()).thenReturn(HOSTNAME);
		when(requestExecutor.executeSNMPTable(anyString(), any(String[].class), any(), anyString(), anyBoolean(), isNull()))
			.thenReturn(List.of(List.of("a", "b")));
		final String result = extension.executeQuery(snmpConfiguration, queryNode);
		assertNotNull(result);
	}

	@Test
	void testExecuteQueryInvalidAction() throws Exception {
		final ObjectMapper mapper = new ObjectMapper();
		final JsonNode queryNode = mapper.readTree("{\"action\": \"invalid\", \"oid\": \"1.3.6.1\"}");
		when(snmpConfiguration.getHostname()).thenReturn(HOSTNAME);
		// Should not throw because the exception is caught internally
		final String result = extension.executeQuery(snmpConfiguration, queryNode);
		assertEquals("Failed Executing SNMP query", result);
	}

	@Test
	void testNewObjectMapper() {
		assertNotNull(AbstractSnmpExtension.newObjectMapper());
	}
}
