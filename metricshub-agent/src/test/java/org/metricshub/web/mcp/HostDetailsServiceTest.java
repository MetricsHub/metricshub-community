package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

		HostDetails result = hostDetailsService.getHostDetails(List.of(HOSTNAME), null).getHosts().get(0).getResponse();

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

		HostDetails result = hostDetailsService.getHostDetails(List.of(HOSTNAME), 3).getHosts().get(0).getResponse();

		// No error message is displayed as the hostname exists.
		assertTrue(result.getWorkingConnectors().isEmpty());
		assertTrue(result.getConfiguredProtocols().isEmpty());
		assertTrue(result.getCollectors().isEmpty());
		assertNull(result.getErrorMessage());
	}

	@Test
	void returnsProtocolsConnectorsAndCollectorsWhenPresent() {
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		// Populating the telemetry managers
		final HostConfiguration snmpHostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of())
			.build();

		final HostConfiguration sshHostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of())
			.build();

		// Create a telemetry Manager
		TelemetryManager snmpTelemetryManager = TelemetryManager.builder().hostConfiguration(snmpHostConfiguration).build();
		TelemetryManager sshTelemetryManager = TelemetryManager.builder().hostConfiguration(sshHostConfiguration).build();

		// Create host up metrics
		Map<String, AbstractMetric> snmpMetrics = Map.of(
			"metricshub.host.up{protocol=\"snmp\"}",
			NumberMetric.builder().attributes(Map.of("protocol", "snmp")).build()
		);
		Map<String, AbstractMetric> sshMetrics = Map.of(
			"metricshub.host.up{protocol=\"ssh\"}",
			NumberMetric.builder().attributes(Map.of("protocol", "ssh")).build()
		);

		// Create two connector monitors and an endpoint monitor
		Monitor monitor1 = Monitor.builder().attributes(Map.of("connector_id", "CiscoUCSBladeSNMP")).build();
		Monitor endpointHostMonitor = Monitor.builder().isEndpoint(true).metrics(snmpMetrics).build();

		Monitor monitor2 = Monitor.builder().attributes(Map.of("connector_id", "Linux")).build();
		Monitor endpointHostMonitor2 = Monitor.builder().isEndpoint(true).metrics(sshMetrics).build();
		// Inject the created monitors within the telemetry manager
		snmpTelemetryManager.setMonitors(
			Map.of("connector", Map.of("monitor1", monitor1), "host", Map.of("endpointMonitor", endpointHostMonitor))
		);
		sshTelemetryManager.setMonitors(
			Map.of("connector", Map.of("monitor2", monitor2), "host", Map.of("endpointMonitor", endpointHostMonitor2))
		);

		// Mock TelemetryManagers in the Agent Context
		when(agentContext.getTelemetryManagers())
			.thenReturn(Map.of("Paris", Map.of("HostId1", snmpTelemetryManager, "HostId2", sshTelemetryManager)));

		// call the getHostDetails() method to test
		List<HostToolResponse<HostDetails>> results = hostDetailsService.getHostDetails(List.of(HOSTNAME), null).getHosts();

		assertEquals(2, results.size());

		// Pull out the payloads (you may have >2 if multiple managers; we only care about snmp/ssh)
		List<HostDetails> details = results.stream().map(HostToolResponse::getResponse).toList();

		HostDetails snmp = details
			.stream()
			.filter(h -> h.getConfiguredProtocols().contains("snmp"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No SNMP HostDetails found"));

		HostDetails ssh = details
			.stream()
			.filter(h -> h.getConfiguredProtocols().contains("ssh"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No SSH HostDetails found"));

		// Ensure they are distinct objects
		assertNotSame(snmp, ssh, "SNMP and SSH HostDetails should be different");

		// SNMP-side assertions
		assertAll(
			"SNMP host assertions",
			() ->
				assertEquals(
					Set.of("CiscoUCSBladeSNMP"),
					snmp.getWorkingConnectors(),
					() -> "SNMP host: expected workingConnectors=[CiscoUCSBladeSNMP] but was " + snmp.getWorkingConnectors()
				),
			() ->
				assertEquals(
					Set.of("snmp"),
					snmp.getConfiguredProtocols(),
					() -> "SNMP host: expected configuredProtocols=[snmp] but was " + snmp.getConfiguredProtocols()
				),
			() ->
				assertTrue(
					snmp.getCollectors().contains("SNMP Get"),
					() -> "SNMP host: expected collectors to contain 'SNMP Get' but had " + snmp.getCollectors()
				),
			() ->
				assertTrue(
					snmp.getCollectors().contains("SNMP Walk"),
					() -> "SNMP host: expected collectors to contain 'SNMP Walk' but had " + snmp.getCollectors()
				),
			() ->
				assertTrue(
					snmp.getCollectors().size() >= 4,
					() ->
						"SNMP host: expected at least 4 collectors but had " +
						snmp.getCollectors().size() +
						" -> " +
						snmp.getCollectors()
				),
			() ->
				assertNull(
					snmp.getErrorMessage(),
					() -> "SNMP host: expected no errorMessage but was '" + snmp.getErrorMessage() + "'"
				)
		);

		// SSH-side assertions
		assertAll(
			"SSH host assertions",
			() ->
				assertEquals(
					Set.of("Linux"),
					ssh.getWorkingConnectors(),
					() -> "SSH host: expected workingConnectors=[Linux] but was " + ssh.getWorkingConnectors()
				),
			() ->
				assertEquals(
					Set.of("ssh"),
					ssh.getConfiguredProtocols(),
					() -> "SSH host: expected configuredProtocols=[ssh] but was " + ssh.getConfiguredProtocols()
				),
			() ->
				assertTrue(
					ssh.getCollectors().contains("CommandLine via SSH"),
					() -> "SSH host: expected collectors to contain 'CommandLine via SSH' but had " + ssh.getCollectors()
				),
			() ->
				assertTrue(
					ssh.getCollectors().size() >= 1,
					() ->
						"SSH host: expected at least 1 collector but had " +
						ssh.getCollectors().size() +
						" -> " +
						ssh.getCollectors()
				),
			() ->
				assertNull(
					ssh.getErrorMessage(),
					() -> "SSH host: expected no errorMessage but was '" + ssh.getErrorMessage() + "'"
				)
		);
	}
}
