package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.WinCommandService;
import org.metricshub.extension.win.detection.WmiDetectionService;
import org.metricshub.extension.wmi.WmiConfiguration;
import org.metricshub.extension.wmi.WmiExtension;
import org.metricshub.extension.wmi.WmiRequestExecutor;
import org.metricshub.web.AgentContextHolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecuteWqlQueryServiceTest {

	/**
	 * A hostname where the WQL query is executed.
	 */
	private static final String HOSTNAME = "hostname";

	/**
	 * A namespace for the WQL query.
	 */
	private static final String NAMESPACE = "root\\cimv2";

	/**
	 * A WQL query to execute.
	 */
	private static final String WQL_QUERY = "SELECT * FROM Win32_OperatingSystem";

	/**
	 * WMI protocol identifier
	 */
	private static final String WMI_IDENTIFIER = "wmi";

	/**
	 * A Timeout for the query execution
	 */

	private static final Long TIMEOUT = 10L;

	private AgentContextHolder agentContextHolder;
	private AgentContext agentContext;
	private ExtensionManager extensionManager;
	private ExecuteWqlQueryService wqlQueryService;

	@InjectMocks
	private WmiExtension wmiExtension;

	@Mock
	private WmiRequestExecutor wmiRequestExecutorMock;

	@Mock
	private WmiDetectionService wmiDetectionServiceMock;

	@Mock
	private WinCommandService winCommandServiceMock;

	void setup() {
		// Mocking the agent context holder
		agentContextHolder = mock(AgentContextHolder.class);

		// Mocking the agent context
		agentContext = mock(AgentContext.class);

		// Creating an extension manager with the WMI Extension
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(wmiExtension)).build();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		// Injecting the agent context holder in the WQL query service.
		wqlQueryService = new ExecuteWqlQueryService(agentContextHolder);
	}

	@Test
	void testExecuteWqlQueryWithoutConfiguration() {
		setup();
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
		final QueryResponse result = wqlQueryService.executeQuery(HOSTNAME, WMI_IDENTIFIER, WQL_QUERY, NAMESPACE, TIMEOUT);

		assertEquals("No configuration found.".formatted(HOSTNAME), result.getIsError());
	}

	@Test
	void testExecuteWqlQueryWithoutExtension() {
		setup();
		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Calling execute query
		final QueryResponse result = wqlQueryService.executeQuery(HOSTNAME, WMI_IDENTIFIER, WQL_QUERY, NAMESPACE, TIMEOUT);

		assertEquals("No Extension found for wmi protocol.", result.getIsError());
		assertNull(result.getResponse());
	}

	@Test
	void testExecuteWqlQueryOnWmi() throws ClientException {
		setup();
		// Creating a WMI Configuration for the host
		WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the WMI configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Mocking executeWmi query on WMI request executor
		when(wmiRequestExecutorMock.executeWmi(eq(HOSTNAME), any(WmiConfiguration.class), eq(WQL_QUERY), eq(NAMESPACE)))
			.thenReturn(List.of(List.of("Value1", "Value2")));

		final QueryResponse result = wqlQueryService.executeQuery(HOSTNAME, WMI_IDENTIFIER, WQL_QUERY, NAMESPACE, TIMEOUT);

		assertEquals(TextTableHelper.generateTextTable(List.of(List.of("Value1", "Value2"))), result.getResponse());
	}

	@Test
	void testExecuteWqlQueryExtensionThrows() throws Exception {
		// Setup the mocks
		setup();

		// Creating a WMI Configuration for the host
		WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the WMI configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Make the executor throw when the extension tries to execute
		when(wmiRequestExecutorMock.executeWmi(eq(HOSTNAME), any(WmiConfiguration.class), eq(WQL_QUERY), eq(NAMESPACE)))
			.thenThrow(new RuntimeException("An error has occurred"));

		// Call the execute query method
		QueryResponse result = wqlQueryService.executeQuery(HOSTNAME, WMI_IDENTIFIER, WQL_QUERY, NAMESPACE, TIMEOUT);

		// Assertions
		assertNotNull(result.getIsError());
		assertTrue(result.getIsError().contains("An error has occurred"));
	}

	@Test
	void testBuildConfigurationWithNamespace() {
		final WmiExtension wmiExtension = new WmiExtension();
		final ExecuteWqlQueryService wqlQueryService = new ExecuteWqlQueryService(agentContextHolder);
		final WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.username("username")
			.hostname(HOSTNAME)
			.password("password".toCharArray())
			.build();
		final Optional<IConfiguration> result = wqlQueryService.buildConfigurationWithNamespace(
			wmiExtension,
			wmiConfiguration,
			NAMESPACE
		);

		assertTrue(result.isPresent());

		final WmiConfiguration newConfiguration = (WmiConfiguration) result.get();

		wmiConfiguration.setNamespace(NAMESPACE);
		assertEquals(wmiConfiguration, newConfiguration);
	}
}
