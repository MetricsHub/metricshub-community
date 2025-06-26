package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.HttpExtension;
import org.metricshub.extension.http.HttpRequestExecutor;
import org.metricshub.extension.ipmi.IpmiConfiguration;
import org.metricshub.extension.ipmi.IpmiExtension;
import org.metricshub.extension.jdbc.JdbcExtension;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.extension.snmp.SnmpExtension;
import org.metricshub.extension.wbem.WbemConfiguration;
import org.metricshub.extension.wbem.WbemExtension;
import org.metricshub.extension.winrm.WinRmConfiguration;
import org.metricshub.extension.winrm.WinRmExtension;
import org.metricshub.extension.wmi.WmiConfiguration;
import org.metricshub.extension.wmi.WmiExtension;
import org.metricshub.web.AgentContextHolder;
import org.mockito.Mock;

class ProtocolCheckServiceTest {

	private ProtocolCheckService protocolCheckService;

	private static final String HOSTNAME = "hostname";

	private static final String USERNAME = "username";

	private static final String PASSWORD = "password";

	private static final Long TIMEOUT = 10L;

	@Mock
	private HttpExtension httpExtension;

	@Mock
	private IpmiExtension ipmiExtension;

	@Mock
	private SnmpExtension snmpExtension;

	@Mock
	private OsCommandExtension osCommandExtension;

	@Mock
	private WbemExtension wbemExtension;

	@Mock
	private WmiExtension wmiExtension;

	@Mock
	private WinRmExtension winrmExtension;

	@Mock
	private HttpRequestExecutor httpRequestExecutorMock;

	void setup(final List<IProtocolExtension> extensions, Map<String, TelemetryManager> telemetryManagers) {
		final AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		final AgentContext agentContext = mock(AgentContext.class);
		final ExtensionManager extensionManager = ExtensionManager.builder().withProtocolExtensions(extensions).build();

		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("resourceGroup", telemetryManagers));

		protocolCheckService = new ProtocolCheckService(agentContextHolder);
	}

	private Map<Class<? extends IConfiguration>, IConfiguration> getConfigurations() {
		HttpConfiguration httpConfiguration = HttpConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();

		IpmiConfiguration ipmiConfiguration = IpmiConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();

		SnmpConfiguration snmpConfiguration = SnmpConfiguration.builder().hostname(HOSTNAME).build();

		SshConfiguration sshConfiguration = SshConfiguration.sshConfigurationBuilder().hostname(HOSTNAME).build();

		WbemConfiguration wbemConfiguration = WbemConfiguration.builder().hostname(HOSTNAME).build();

		WmiConfiguration wmiConfiguration = WmiConfiguration.builder().hostname(HOSTNAME).build();

		WinRmConfiguration winrmConfiguration = WinRmConfiguration.builder().hostname(HOSTNAME).build();

		return Map.of(
			HttpConfiguration.class,
			httpConfiguration,
			IpmiConfiguration.class,
			ipmiConfiguration,
			SnmpConfiguration.class,
			snmpConfiguration,
			SshConfiguration.class,
			sshConfiguration,
			WbemConfiguration.class,
			wbemConfiguration,
			WmiConfiguration.class,
			wmiConfiguration,
			WinRmConfiguration.class,
			winrmConfiguration
		);
	}

	@Test
	void testCheckProtocol_twoProtocols() throws Exception {
		// Mocking extensions behavior
		httpExtension = mock(HttpExtension.class);
		snmpExtension = mock(SnmpExtension.class);

		doCallRealMethod().when(httpExtension).buildConfiguration(eq("http"), any(), any());
		doCallRealMethod().when(snmpExtension).buildConfiguration(eq("snmp"), any(), any());

		doReturn(true).when(httpExtension).isSupportedConfigurationType("http");
		doReturn(true).when(snmpExtension).isSupportedConfigurationType("snmp");

		doReturn(Optional.of(Boolean.TRUE)).when(httpExtension).checkProtocol(any(TelemetryManager.class));
		doReturn(Optional.of(Boolean.TRUE)).when(snmpExtension).checkProtocol(any(TelemetryManager.class));

		// Create a TelemetryManager that contain all the configurations except Snmpv3
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).configurations(getConfigurations()).build())
			.build();

		// Inject the TelemetryManager in the AgentContext and setup
		setup(List.of(httpExtension, snmpExtension), Map.of("host-id", telemetryManager));

		// Call check protocol method
		Map<String, ProtocolCheckResponse> responses = protocolCheckService.checkProtocol(
			"http,snmp,snmpv3",
			HOSTNAME,
			TIMEOUT
		);

		// Tests
		assertEquals(3, responses.size());
		ProtocolCheckResponse response = responses.get("http");
		assertNotNull(response);
		assertNull(response.getErrorMessage());
		assertTrue(response.isReachable());

		response = responses.get("snmp");
		assertNotNull(response);
		assertNull(response.getErrorMessage());
		assertTrue(response.isReachable());

		response = responses.get("snmpv3");
		assertNotNull(response);
		assertTrue(response.getErrorMessage().contains("No extension is available for the protocol snmpv3"));
		assertFalse(response.isReachable());
	}

	@Test
	void testCheckProtocol_noConfigurations() {
		// Mock extension that would normally handle "http"
		httpExtension = mock(HttpExtension.class);
		doReturn("http").when(httpExtension).getIdentifier();
		doReturn(true).when(httpExtension).isSupportedConfigurationType("http");

		// Extension returns true for checkProtocol, but there are no configs
		doReturn(Optional.of(Boolean.TRUE)).when(httpExtension).checkProtocol(any());

		// Setup agent context with no telemetry managers for the hostname
		final AgentContextHolder contextHolder = mock(AgentContextHolder.class);
		final AgentContext agentContext = mock(AgentContext.class);
		when(contextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager())
			.thenReturn(ExtensionManager.builder().withProtocolExtensions(List.of(httpExtension)).build());
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of());

		protocolCheckService = new ProtocolCheckService(contextHolder);

		Map<String, ProtocolCheckResponse> responses = protocolCheckService.checkProtocol("http", HOSTNAME, TIMEOUT);

		assertEquals(1, responses.size());
		ProtocolCheckResponse response = responses.get("http");
		assertNotNull(response);
		assertFalse(response.isReachable()); // Nothing to evaluate
	}

	@Test
	void testConvertConfigurationForProtocol_nullFieldsHandled() throws InvalidConfigurationException {
		// Base config with null password and timeout
		HttpConfiguration httpConfig = HttpConfiguration.builder().username(USERNAME).password(null).timeout(null).build();

		// Extension that receives the config
		osCommandExtension = mock(OsCommandExtension.class);
		doCallRealMethod().when(osCommandExtension).buildConfiguration(eq("ssh"), any(), any());

		SshConfiguration sshConfiguration = (SshConfiguration) new ProtocolCheckService(mock(AgentContextHolder.class))
			.convertConfigurationForProtocol(httpConfig, "ssh", TIMEOUT, osCommandExtension);

		assertNotNull(sshConfiguration);
		assertEquals(USERNAME, sshConfiguration.getUsername());
		assertNull(sshConfiguration.getPassword());
		assertEquals(10, sshConfiguration.getTimeout());
	}

	@Test
	void testCheckProtocol_NoAvailableExtension() throws InvalidConfigurationException {
		// Mocking extensions behavior
		httpExtension = mock(HttpExtension.class);
		doCallRealMethod().when(httpExtension).buildConfiguration(eq("http"), any(), any());
		doReturn(false).when(httpExtension).isSupportedConfigurationType("http");
		doReturn(Optional.of(Boolean.TRUE)).when(httpExtension).checkProtocol(any(TelemetryManager.class));

		// Create a TelemetryManager that contain all the configurations except Snmpv3
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).configurations(getConfigurations()).build())
			.build();

		// Inject the TelemetryManager in the AgentContext and setup
		setup(List.of(httpExtension), Map.of("host-id", telemetryManager));

		// Call check protocol method
		Map<String, ProtocolCheckResponse> responses = protocolCheckService.checkProtocol("http", HOSTNAME, TIMEOUT);

		// Tests
		assertEquals(1, responses.size());
		ProtocolCheckResponse response = responses.get("http");
		assertNotNull(response);
		assertEquals("No extension is available for the protocol http", response.getErrorMessage());
		assertFalse(response.isReachable());
	}

	@Test
	void testCheckProtocol_InvalidConfigurationExceptionThrown() throws InvalidConfigurationException {
		// Mocking extensions behavior
		httpExtension = mock(HttpExtension.class);
		doCallRealMethod().when(httpExtension).buildConfiguration(eq("http"), any(), any());
		doReturn(true).when(httpExtension).isSupportedConfigurationType("http");

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).configurations(getConfigurations()).build())
			.build();

		// Inject the TelemetryManager in the AgentContext and setup
		setup(List.of(httpExtension), Map.of("host-id", telemetryManager));

		// Call check protocol method
		Map<String, ProtocolCheckResponse> responses = protocolCheckService.checkProtocol("http", HOSTNAME, TIMEOUT);

		// Tests
		ProtocolCheckResponse response = responses.get("http");
		assertNotNull(response);
		assertFalse(response.isReachable());
	}

	@Test
	void testCheckProtocol_testingWinRmWithWmiConfig() throws InvalidConfigurationException {
		// Mocking extensions behavior
		winrmExtension = mock(WinRmExtension.class);
		doCallRealMethod().when(winrmExtension).buildConfiguration(eq("winrm"), any(), any());
		doReturn(true).when(winrmExtension).isSupportedConfigurationType("winrm");
		doReturn(Optional.of(Boolean.TRUE)).when(winrmExtension).checkProtocol(any(TelemetryManager.class));

		// Create a WmiConfiguration
		WmiConfiguration wmiConfiguration = WmiConfiguration.builder().hostname(HOSTNAME).build();

		// Create a TelemetryManager that contains WmiConfiguration
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOSTNAME)
					.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
					.build()
			)
			.build();

		// Inject the TelemetryManager in the AgentContext and setup
		setup(List.of(winrmExtension), Map.of("host-id", telemetryManager));

		// Call check protocol method
		Map<String, ProtocolCheckResponse> responses = protocolCheckService.checkProtocol("winrm", HOSTNAME, TIMEOUT);

		// Tests
		ProtocolCheckResponse response = responses.get("winrm");
		assertNotNull(response);
		assertNull(response.getErrorMessage());
		assertTrue(response.isReachable());
	}

	@Test
	void testCheckProtocol_allProtocols() throws Exception {
		// Mocking extensions behavior
		httpExtension = mock(HttpExtension.class);
		snmpExtension = mock(SnmpExtension.class);
		osCommandExtension = mock(OsCommandExtension.class);
		ipmiExtension = mock(IpmiExtension.class);
		wbemExtension = mock(WbemExtension.class);
		wmiExtension = mock(WmiExtension.class);
		winrmExtension = mock(WinRmExtension.class);

		// Mock configuration building
		doCallRealMethod().when(httpExtension).buildConfiguration(eq("http"), any(), any());
		doCallRealMethod().when(snmpExtension).buildConfiguration(eq("snmp"), any(), any());
		doCallRealMethod().when(osCommandExtension).buildConfiguration(eq("ssh"), any(), any());
		doCallRealMethod().when(ipmiExtension).buildConfiguration(eq("ipmi"), any(), any());
		doCallRealMethod().when(wbemExtension).buildConfiguration(eq("wbem"), any(), any());
		doCallRealMethod().when(wmiExtension).buildConfiguration(eq("wmi"), any(), any());
		doCallRealMethod().when(winrmExtension).buildConfiguration(eq("winrm"), any(), any());

		// Mock extension supported configuration method
		doReturn(true).when(httpExtension).isSupportedConfigurationType("http");
		doReturn(true).when(snmpExtension).isSupportedConfigurationType("snmp");
		doReturn(true).when(osCommandExtension).isSupportedConfigurationType("ssh");
		doReturn(true).when(ipmiExtension).isSupportedConfigurationType("ipmi");
		doReturn(true).when(wbemExtension).isSupportedConfigurationType("wbem");
		doReturn(true).when(wmiExtension).isSupportedConfigurationType("wmi");
		doReturn(true).when(winrmExtension).isSupportedConfigurationType("winrm");

		// Mock check protocol method
		doReturn(Optional.of(Boolean.TRUE)).when(httpExtension).checkProtocol(any(TelemetryManager.class));
		doReturn(Optional.of(Boolean.TRUE)).when(snmpExtension).checkProtocol(any(TelemetryManager.class));
		doReturn(Optional.of(Boolean.TRUE)).when(osCommandExtension).checkProtocol(any(TelemetryManager.class));
		doReturn(Optional.of(Boolean.TRUE)).when(ipmiExtension).checkProtocol(any(TelemetryManager.class));
		doReturn(Optional.of(Boolean.TRUE)).when(wbemExtension).checkProtocol(any(TelemetryManager.class));
		doReturn(Optional.of(Boolean.TRUE)).when(wmiExtension).checkProtocol(any(TelemetryManager.class));
		doReturn(Optional.of(Boolean.TRUE)).when(winrmExtension).checkProtocol(any(TelemetryManager.class));

		// Mock get identifier method
		doReturn("http").when(httpExtension).getIdentifier();
		doReturn("snmp").when(snmpExtension).getIdentifier();
		doReturn("ssh").when(osCommandExtension).getIdentifier();
		doReturn("ipmi").when(ipmiExtension).getIdentifier();
		doReturn("wbem").when(wbemExtension).getIdentifier();
		doReturn("wmi").when(wmiExtension).getIdentifier();
		doReturn("winrm").when(winrmExtension).getIdentifier();

		// Create a TelemetryManager that contain all the configurations except Snmpv3
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).configurations(getConfigurations()).build())
			.build();

		// Inject the TelemetryManager in the AgentContext and setup
		setup(
			List.of(
				httpExtension,
				snmpExtension,
				osCommandExtension,
				ipmiExtension,
				wbemExtension,
				wmiExtension,
				winrmExtension
			),
			Map.of("host-id", telemetryManager)
		);

		// Call check protocol method
		Map<String, ProtocolCheckResponse> responses = protocolCheckService.checkProtocol("", HOSTNAME, TIMEOUT);

		// Tests
		assertEquals(7, responses.size());
		responses
			.values()
			.forEach((ProtocolCheckResponse response) -> {
				assertNotNull(response);
				assertNull(response.getErrorMessage());
				assertTrue(response.isReachable());
			});
	}

	public static void main(String[] args) throws IOException {
		TestHelper.configureGlobalLogger();
		ExtensionManager extensionManager = new ExtensionManager();
		extensionManager.setProtocolExtensions(List.of(new JdbcExtension()));
		SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.hostname("ucs-manager")
			.username("admin")
			.password("nationale".toCharArray())
			.build();
		TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.configurations(Map.of(SshConfiguration.class, sshConfiguration))
					.hostname("ucs-manager")
					.build()
			)
			.build();
		AgentContext agentContext = new AgentContext(null, extensionManager);
		agentContext.setTelemetryManagers(Map.of("topLevelResources", Map.of("ucs-manager-id", telemetryManager)));
		AgentContextHolder contextHolder = new AgentContextHolder(agentContext);
		ProtocolCheckService service = new ProtocolCheckService(contextHolder);
		final Map<String, ProtocolCheckResponse> response = service.checkProtocol("jdbc", "ucs-manager", 5L);
		System.out.println(response);
	}
}
