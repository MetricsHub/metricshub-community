package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.ipmi.IpmiConfiguration;
import org.metricshub.extension.ipmi.IpmiExtension;
import org.metricshub.extension.ipmi.IpmiRequestExecutor;
import org.metricshub.web.AgentContextHolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecuteIpmiQueryServiceTest {

	/**
	 * A hostname where the IPMI query is executed.
	 */
	private static final String HOSTNAME = "hostname";

	private AgentContextHolder agentContextHolder;
	private AgentContext agentContext;
	private ExtensionManager extensionManager;
	private ExecuteIpmiQueryService ipmiQueryService;

	@InjectMocks
	private IpmiExtension ipmiExtension;

	@Mock
	private IpmiRequestExecutor ipmiRequestExecutorMock;

	@BeforeEach
	void setup() {
		// Mocking the agent context holder
		agentContextHolder = mock(AgentContextHolder.class);

		// Mocking the agent context
		agentContext = mock(AgentContext.class);

		// Creating an extension manager with the IPMI Extension
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(ipmiExtension)).build();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		// Injecting the agent context holder in the IPMI query service.
		ipmiQueryService = new ExecuteIpmiQueryService(agentContextHolder);
	}

	@Test
	void testExecuteIpmiQueryWithoutConfiguration() {
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
		final QueryResponse result = ipmiQueryService.executeQuery(HOSTNAME, null);

		assertEquals("No IPMI configuration found for hostname.", result.getIsError());
	}

	@Test
	void testExecuteIpmiQueryWithoutExtension() {
		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Calling execute query
		final QueryResponse result = ipmiQueryService.executeQuery(HOSTNAME, null);

		assertEquals("No Extension found for IPMI protocol.", result.getIsError());
		assertNull(result.getResponse());
	}

	@Test
	void testExecuteIpmiQuery() throws ClientException, InterruptedException, ExecutionException, TimeoutException {
		// Creating a IPMI Configuration for the host
		IpmiConfiguration ipmiConfiguration = IpmiConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the IPMI configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IpmiConfiguration.class, ipmiConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Mocking executeIpmi query on IPMI request executor
		when(ipmiRequestExecutorMock.executeIpmiGetSensors(eq(HOSTNAME), any(IpmiConfiguration.class)))
			.thenReturn("Success");

		final QueryResponse result = ipmiQueryService.executeQuery(HOSTNAME, null);

		assertEquals("Success", result.getResponse());
	}

	@Test
	void testExecuteIpmiQueryExtensionThrows() throws Exception {
		// Creating a IPMI Configuration for the host
		IpmiConfiguration ipmiConfiguration = IpmiConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the IPMI configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IpmiConfiguration.class, ipmiConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Make the executor throw when the extension tries to execute
		when(ipmiRequestExecutorMock.executeIpmiGetSensors(eq(HOSTNAME), any(IpmiConfiguration.class)))
			.thenThrow(new RuntimeException("An error has occurred"));

		// Call the execute query method
		QueryResponse result = ipmiQueryService.executeQuery(HOSTNAME, null);

		// Assertions
		assertNotNull(result.getIsError());
		assertTrue(result.getIsError().contains("An error has occurred"));
	}
}
