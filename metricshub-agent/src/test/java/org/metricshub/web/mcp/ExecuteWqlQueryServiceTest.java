package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.web.mcp.ExecuteWqlQueryService.DEFAULT_WBEM_NAMESPACE;
import static org.metricshub.web.mcp.ExecuteWqlQueryService.DEFAULT_WQL_NAMESPACE;
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
		final MultiHostToolResponse<QueryResponse> resultWrapper = executeQuery(
			WMI_IDENTIFIER,
			WQL_QUERY,
			NAMESPACE,
			TIMEOUT
		);

		assertEquals(1, resultWrapper.getHosts().size(), () -> "One host response expected");
		final QueryResponse result = resultWrapper.getHosts().get(0).getResponse();

		assertNotNull(result, "Result should not be null when executing a query");

		assertEquals(
			"No valid wmi configuration found for %s.".formatted(HOSTNAME),
			result.getError(),
			() -> "Unexpected error message when host has no configurations. "
		);
	}

	@Test
	void testExecuteWqlQueryWithoutExtension() {
		setup();
		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Calling execute query
		final MultiHostToolResponse<QueryResponse> result = executeQuery(WMI_IDENTIFIER, WQL_QUERY, NAMESPACE, TIMEOUT);

		assertEquals(
			"The %s extension is not available".formatted(WMI_IDENTIFIER),
			result.getErrorMessage(),
			() -> "Unexpected error message when the WMI extension isn't found"
		);
		assertTrue(result.getHosts().isEmpty(), () -> "No host responses expected when extension missing");
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

		final QueryResponse result = executeQuery(WMI_IDENTIFIER, WQL_QUERY, NAMESPACE, TIMEOUT)
			.getHosts()
			.get(0)
			.getResponse();

		assertEquals(
			TextTableHelper.generateTextTable(List.of(List.of("Value1", "Value2"))),
			result.getResponse(),
			() -> "WMI response mismatch for mocked values [Value1, Value2]"
		);
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
		QueryResponse result = executeQuery(WMI_IDENTIFIER, WQL_QUERY, NAMESPACE, TIMEOUT).getHosts().get(0).getResponse();

		// Assertions
		assertNotNull(result.getError(), () -> "Error message should be returned when an exception is throws");
		assertTrue(
			result.getError().contains("An error has occurred"),
			() -> "Error message should contain 'An error has occurred'"
		);
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

		assertTrue(result.isPresent(), () -> "Configuration should be present after building with namespace");

		final WmiConfiguration newConfiguration = (WmiConfiguration) result.get();

		wmiConfiguration.setNamespace(NAMESPACE);
		assertEquals(
			wmiConfiguration,
			newConfiguration,
			() -> "Built configuration should match input configuration with namespace set"
		);
	}

	@Test
	void normalizeNamespace_core() {
		wqlQueryService = new ExecuteWqlQueryService(agentContextHolder);
		final String namespace = "root/custom";

		assertEquals(
			namespace,
			wqlQueryService.normalizeNamespace("wbem", namespace),
			() -> "Should return provided namespace root/custom"
		);
		assertEquals(
			namespace,
			wqlQueryService.normalizeNamespace("wmi", namespace),
			() -> "Should return provided namespace root/custom"
		);
		assertEquals(
			DEFAULT_WBEM_NAMESPACE,
			wqlQueryService.normalizeNamespace("WbEm", "   "),
			() -> "Should return default WBEM namespace when the given namespace is blank"
		);
		assertEquals(
			DEFAULT_WQL_NAMESPACE,
			wqlQueryService.normalizeNamespace("wmi", null),
			() -> "Should return default WMI namespace when the given namespace is blank"
		);
	}

	private MultiHostToolResponse<QueryResponse> executeQuery(
		final String protocol,
		final String query,
		final String namespace,
		final Long timeout
	) {
		return wqlQueryService.executeQuery(List.of(HOSTNAME), protocol, query, namespace, timeout, null);
	}
}
