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
import org.metricshub.extension.wbem.WbemConfiguration;
import org.metricshub.extension.wbem.WbemExtension;
import org.metricshub.extension.wbem.WbemRequestExecutor;
import org.metricshub.web.AgentContextHolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecuteWbemQueryServiceTest {

	/**
	 * A hostname where the WQL query is executed.
	 */
	private static final String HOSTNAME = "hostname";

	/**
	 * A namespace for the WQL query.
	 */
	private static final String NAMESPACE = "root/cimv2";

	/**
	 * A WQL query to execute.
	 */
	private static final String WBEM_QUERY = "SELECT MajorVersion FROM VMware_HypervisorSoftwareIdentity";

	/**
	 * .
	 */
	private static final String VCENTER = "vcenter";

	/**
	 * A Timeout for the query execution
	 */

	private static final Long TIMEOUT = 10L;

	private AgentContextHolder agentContextHolder;
	private AgentContext agentContext;
	private ExtensionManager extensionManager;
	private ExecuteWbemQueryService wbemQueryService;

	@InjectMocks
	private WbemExtension wbemExtension;

	@Mock
	private WbemRequestExecutor wbemRequestExecutorMock;

	void setup() {
		// Mocking the agent context holder
		agentContextHolder = mock(AgentContextHolder.class);

		// Mocking the agent context
		agentContext = mock(AgentContext.class);

		// Creating an extension manager with the Wbem Extension
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(wbemExtension)).build();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		// Injecting the agent context holder in the WQL query service.
		wbemQueryService = new ExecuteWbemQueryService(agentContextHolder);
	}

	@Test
	void testExecuteWbemQueryWithoutConfiguration() {
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
		final QueryResponse result = wbemQueryService.executeQuery(HOSTNAME, WBEM_QUERY, NAMESPACE, VCENTER, TIMEOUT);

		assertEquals("No configuration found.".formatted(HOSTNAME), result.getIsError());
	}

	@Test
	void testExecuteWbemQueryWithoutExtension() {
		setup();
		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Calling execute query
		final QueryResponse result = wbemQueryService.executeQuery(HOSTNAME, WBEM_QUERY, NAMESPACE, VCENTER, TIMEOUT);

		assertEquals("No Extension found for Wbem protocol.", result.getIsError());
		assertNull(result.getResponse());
	}

	@Test
	void testExecuteWbemQuery() throws ClientException {
		setup();
		// Creating a WBEM Configuration for the host
		WbemConfiguration wbemConfiguration = WbemConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the WBEM configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(WbemConfiguration.class, wbemConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Mocking executeWbem query on WBEM request executor
		when(
			wbemRequestExecutorMock.executeWbem(
				eq(HOSTNAME),
				any(WbemConfiguration.class),
				eq(WBEM_QUERY),
				eq(NAMESPACE),
				any(TelemetryManager.class)
			)
		)
			.thenReturn(List.of(List.of("Value1")));

		final QueryResponse result = wbemQueryService.executeQuery(HOSTNAME, WBEM_QUERY, NAMESPACE, VCENTER, TIMEOUT);

		assertEquals(TextTableHelper.generateTextTable("MajorVersion", List.of(List.of("Value1"))), result.getResponse());
	}

	@Test
	void testExecuteWqlQueryExtensionThrows() throws Exception {
		// Setup the mocks
		setup();

		// Creating a WBEM Configuration for the host
		WbemConfiguration wbemConfiguration = WbemConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the WBEM configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(WbemConfiguration.class, wbemConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Make the executor throw when the extension tries to execute
		when(
			wbemRequestExecutorMock.executeWbem(
				eq(HOSTNAME),
				any(WbemConfiguration.class),
				eq(WBEM_QUERY),
				eq(NAMESPACE),
				any(TelemetryManager.class)
			)
		)
			.thenThrow(new RuntimeException("An error has occurred"));

		// Call the execute query method
		QueryResponse result = wbemQueryService.executeQuery(HOSTNAME, WBEM_QUERY, NAMESPACE, VCENTER, TIMEOUT);

		// Assertions
		assertNotNull(result.getIsError());
		assertTrue(result.getIsError().contains("An error has occurred"));
	}

	@Test
	void testBuildConfigurationWithNamespace() {
		final WbemExtension wbemExtension = new WbemExtension();
		final ExecuteWbemQueryService wbemQueryService = new ExecuteWbemQueryService(agentContextHolder);
		final WbemConfiguration wbemConfiguration = WbemConfiguration
			.builder()
			.username("username")
			.hostname(HOSTNAME)
			.password("password".toCharArray())
			.build();
		final Optional<IConfiguration> result = wbemQueryService.buildConfigurationWithNamespaceAndVcenter(
			wbemExtension,
			wbemConfiguration,
			NAMESPACE,
			VCENTER
		);

		assertTrue(result.isPresent());

		final WbemConfiguration resultedConfiguration = (WbemConfiguration) result.get();

		assertAll(
			() -> assertEquals(NAMESPACE, resultedConfiguration.getNamespace()),
			() -> assertEquals(VCENTER, resultedConfiguration.getVCenter()),
			() -> assertEquals(HOSTNAME, resultedConfiguration.getHostname()),
			() -> assertEquals("username", resultedConfiguration.getUsername())
		);
	}
}
