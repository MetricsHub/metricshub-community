package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.ISnmpConfiguration;
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.extension.snmp.SnmpExtension;
import org.metricshub.extension.snmp.SnmpRequestExecutor;
import org.metricshub.web.AgentContextHolder;
import org.mockito.Mock;

class ExecuteSnmpQueryServiceTest {

	private static final String HOSTNAME = "hostname";

	private static final String OID = "1.3.6.1.4.1.674.10892.5";

	private static final String SUCCESS_MESSAGE = "Success";

	private static final Long TIMEOUT = 10L;

	private AgentContextHolder agentContextHolder;
	private AgentContext agentContext;
	private ExecuteSnmpQueryService snmpQueryService;
	private ExtensionManager extensionManager;
	private SnmpExtension snmpExtension;

	@Mock
	private SnmpRequestExecutor snmpRequestExecutor;

	@BeforeEach
	void setup() {
		// Mocking the agent context holder
		agentContextHolder = mock(AgentContextHolder.class);

		// Mocking the agent context
		agentContext = mock(AgentContext.class);

		// Mocking Snmp Request Executor
		snmpRequestExecutor = mock(SnmpRequestExecutor.class);

		// injecting the mocked SNMP Request Executor in the SNMP Extension
		snmpExtension = new SnmpExtension(snmpRequestExecutor);

		// Creating an extension manager with the SNMP Extension
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(snmpExtension)).build();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		// Injecting the agent context holder in the SNMP query service.
		snmpQueryService = new ExecuteSnmpQueryService(agentContextHolder);
	}

	@Test
	void testExecuteSNMPQueryWithoutConfiguration() {
		// creating a host configuration without configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of())
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Calling execute query
		final QueryResponse result = snmpQueryService.executeQuery(HOSTNAME, "get", OID, null, TIMEOUT);

		assertEquals(
			"No SNMP configuration found for %s.".formatted(HOSTNAME),
			result.getIsError(),
			() -> "Should return `missing SNMP configuration` message"
		);
	}

	@Test
	void testExecuteQueryWithoutExtension() {
		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Calling execute query
		final QueryResponse result = snmpQueryService.executeQuery(HOSTNAME, "get", OID, null, TIMEOUT);

		assertEquals(
			"SNMP Extension is not available",
			result.getIsError(),
			() -> "Should return `missing SNMP extension` message"
		);
		assertNull(result.getResponse(), () -> "Response should be null when extension is missing");
	}

	@Test
	void testExecuteSNMPGetQuery() throws InterruptedException, ExecutionException, TimeoutException {
		// Creating an SNMP Configuration for the host
		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOSTNAME).build();

		// creating a host configuration with the SNMP configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SnmpConfiguration.class, snmpConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Mocking executeSNMPGet query on SNMP request executor
		when(snmpRequestExecutor.executeSNMPGet(eq(OID), any(ISnmpConfiguration.class), eq(HOSTNAME), anyBoolean()))
			.thenReturn("Success");

		// Calling execute query
		QueryResponse result = snmpQueryService.executeQuery(HOSTNAME, "get", OID, null, TIMEOUT);

		assertNull(result.getIsError(), () -> "Error should be null on successful GET");
		assertEquals(SUCCESS_MESSAGE, result.getResponse(), () -> "GET should return `Success`");

		// Test a wrong SNMP query type
		result = snmpQueryService.executeQuery(HOSTNAME, "aw", OID, null, TIMEOUT);

		assertTrue(
			result.getIsError().contains("Unknown SNMP query"),
			() -> "Should return `unknown SNMP query type` message"
		);
	}

	@Test
	void testExecuteSNMPGetNextQuery() throws InterruptedException, ExecutionException, TimeoutException {
		// Creating an SNMP Configuration for the host
		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOSTNAME).build();

		// creating a host configuration with the SNMP configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SnmpConfiguration.class, snmpConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Mocking executeSNMPGetNext query on SNMP request executor
		when(snmpRequestExecutor.executeSNMPGetNext(eq(OID), any(ISnmpConfiguration.class), eq(HOSTNAME), anyBoolean()))
			.thenReturn("Success");

		// Calling execute query
		final QueryResponse result = snmpQueryService.executeQuery(HOSTNAME, "getNext", OID, null, TIMEOUT);

		assertNull(result.getIsError(), () -> "Error should be null on successful GETNEXT");
		assertEquals(SUCCESS_MESSAGE, result.getResponse(), () -> "GETNEXT should return `Success`");
	}

	@Test
	void testExecuteSNMPWalkQuery() throws InterruptedException, ExecutionException, TimeoutException {
		// Creating an SNMP Configuration for the host
		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOSTNAME).build();

		// creating a host configuration with the SNMP configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SnmpConfiguration.class, snmpConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Mocking executeSNMPWalk query on SNMP request executor
		when(snmpRequestExecutor.executeSNMPWalk(eq(OID), any(ISnmpConfiguration.class), eq(HOSTNAME), anyBoolean()))
			.thenReturn("Success");

		// Calling execute query
		final QueryResponse result = snmpQueryService.executeQuery(HOSTNAME, "walk", OID, null, TIMEOUT);

		assertNull(result.getIsError(), () -> "Error should be null on successful WALK");
		assertEquals(SUCCESS_MESSAGE, result.getResponse(), () -> "WALK should return `Success`");
	}

	@Test
	void testExecuteSNMPTableQuery() throws InterruptedException, ExecutionException, TimeoutException {
		// Creating an SNMP Configuration for the host
		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOSTNAME).build();

		// creating a host configuration with the SNMP configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SnmpConfiguration.class, snmpConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		final String[] columns = { "1", "3", "5" };
		// Mocking executeSNMPGet query on SNMP request executor
		when(
			snmpRequestExecutor.executeSNMPTable(
				eq(OID),
				eq(columns),
				any(ISnmpConfiguration.class),
				eq(HOSTNAME),
				anyBoolean()
			)
		)
			.thenReturn(List.of(List.of("Column1", "Column2", "Column3")));

		// Calling execute query
		QueryResponse result = snmpQueryService.executeQuery(HOSTNAME, "table", OID, "1, 3, 5 ,", TIMEOUT);

		assertNull(result.getIsError(), () -> "Error should be null on successful TABLE");
		assertEquals(
			TextTableHelper.generateTextTable("1;3;5", List.of(List.of("Column1", "Column2", "Column3"))),
			result.getResponse(),
			() -> "TABLE should return formatted text table"
		);

		// Wrong columns value
		result = snmpQueryService.executeQuery(HOSTNAME, "table", OID, "1, 3, a", TIMEOUT);
		assertTrue(
			result.getIsError().contains("Exception occurred when parsing columns:"),
			() -> "Should return `parsing error for invalid columns` message"
		);
	}
}
