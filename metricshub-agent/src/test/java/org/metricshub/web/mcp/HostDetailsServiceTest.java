package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.web.AgentContextHolder;

class HostDetailsServiceTest {

	private HostDetailsService hostDetailsService;
	private AgentContextHolder agentContextHolder;
	private AgentContext agentContext;
	private static final String HOSTNAME = "Hostname";

	@BeforeEach
	void setUp() {
		agentContextHolder = mock(AgentContextHolder.class);
		agentContext = mock(AgentContext.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		hostDetailsService = new HostDetailsService(agentContextHolder);
	}

	@Test
	void returnsErrorWhenHostnameNotFound() {
		// An empty telemetry managers list
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of()));

		List<HostDetails> hostResults = hostDetailsService
			.getHostDetails(List.of(HOSTNAME), null)
			.getHosts()
			.get(0)
			.getResponse();
		final HostDetails result = hostResults.get(0);

		assertEquals("Hostname not found in the current configuration.", result.getErrorMessage());
		assertNull(result.getCollectors());
		assertNull(result.getConfiguredProtocols());
		assertNull(result.getWorkingConnectors());
	}

	@Test
	void returnsEmptySetsWhenNoMonitorsAndNoMetrics() {
		// Populating the telemetry manager
		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOSTNAME).build();
		SshConfiguration sshConfiguration = SshConfiguration.sshConfigurationBuilder().hostname(HOSTNAME).build();

		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SnmpConfiguration.class, snmpConfiguration, SshConfiguration.class, sshConfiguration))
			.build();

		// An telemetry manager without monitors nor metrics.
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		List<HostDetails> hostResults = hostDetailsService
			.getHostDetails(List.of(HOSTNAME), 3)
			.getHosts()
			.get(0)
			.getResponse();
		final HostDetails result = hostResults.get(0);

		// No error message is displayed as the hostname exists.
		assertTrue(result.getWorkingConnectors().isEmpty());
		assertTrue(result.getConfiguredProtocols().isEmpty());
		assertTrue(result.getCollectors().isEmpty());
		assertNull(result.getErrorMessage());
	}

	@Test
	void returnsProtocolsConnectorsAndCollectorsWhenPresent() {
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		// Populating the telemetry manager
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of())
			.build();

		// Create a telemetry Manager
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Create host up metrics
		Map<String, AbstractMetric> metrics = Map.of(
			"metricshub.host.up{protocol=\"snmp\"}",
			NumberMetric.builder().attributes(Map.of("protocol", "snmp")).build(),
			"metricshub.host.up{protocol=\"ssh\"}",
			NumberMetric.builder().attributes(Map.of("protocol", "ssh")).build()
		);

		// Create two connector monitors and an endpoint monitor
		Monitor monitor1 = Monitor.builder().attributes(Map.of("connector_id", "CiscoUCSBladeSNMP")).build();
		Monitor monitor2 = Monitor.builder().attributes(Map.of("connector_id", "Linux")).build();
		Monitor endpointHostMonitor = Monitor.builder().isEndpoint(true).metrics(metrics).build();

		// Inject the created monitors within the telemetry manager
		telemetryManager.setMonitors(
			Map.of(
				"connector",
				Map.of("monitor1", monitor1, "monitor2", monitor2),
				"host",
				Map.of("endpointMonitor", endpointHostMonitor)
			)
		);

		// Mock TelemetryManager in the Agent Context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// call the getHostDetails() method to test
		List<HostDetails> hostResults = hostDetailsService
			.getHostDetails(List.of(HOSTNAME), null)
			.getHosts()
			.get(0)
			.getResponse();
		final HostDetails result = hostResults.get(0);

		assertEquals(Set.of("CiscoUCSBladeSNMP", "Linux"), result.getWorkingConnectors());

		assertEquals(Set.of("snmp", "ssh"), result.getConfiguredProtocols());

		assertTrue(result.getCollectors().contains("CommandLine via SSH"));
		assertTrue(result.getCollectors().contains("SNMP Get"));
		assertTrue(result.getCollectors().contains("SNMP Walk"));
		assertTrue(result.getCollectors().size() >= 5);
		assertNull(result.getErrorMessage());
	}
}
