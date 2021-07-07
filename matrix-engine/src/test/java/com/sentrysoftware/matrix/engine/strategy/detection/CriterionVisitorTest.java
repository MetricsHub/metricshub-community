package com.sentrysoftware.matrix.engine.strategy.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sentrysoftware.matrix.connector.model.detection.criteria.http.HTTP;
import com.sentrysoftware.matrix.connector.model.detection.criteria.ipmi.IPMI;
import com.sentrysoftware.matrix.connector.model.detection.criteria.kmversion.KMVersion;
import com.sentrysoftware.matrix.connector.model.detection.criteria.os.OS;
import com.sentrysoftware.matrix.connector.model.detection.criteria.oscommand.OSCommand;
import com.sentrysoftware.matrix.connector.model.detection.criteria.process.Process;
import com.sentrysoftware.matrix.connector.model.detection.criteria.service.Service;
import com.sentrysoftware.matrix.connector.model.detection.criteria.snmp.SNMPGet;
import com.sentrysoftware.matrix.connector.model.detection.criteria.snmp.SNMPGetNext;
import com.sentrysoftware.matrix.connector.model.detection.criteria.telnet.TelnetInteractive;
import com.sentrysoftware.matrix.connector.model.detection.criteria.ucs.UCS;
import com.sentrysoftware.matrix.connector.model.detection.criteria.wbem.WBEM;
import com.sentrysoftware.matrix.connector.model.detection.criteria.wmi.WMI;
import com.sentrysoftware.matrix.engine.EngineConfiguration;
import com.sentrysoftware.matrix.engine.protocol.HTTPProtocol;
import com.sentrysoftware.matrix.engine.protocol.IPMIOverLanProtocol;
import com.sentrysoftware.matrix.engine.protocol.IProtocolConfiguration;
import com.sentrysoftware.matrix.engine.protocol.OSCommandConfig;
import com.sentrysoftware.matrix.engine.protocol.SNMPProtocol;
import com.sentrysoftware.matrix.engine.protocol.SNMPProtocol.SNMPVersion;
import com.sentrysoftware.matrix.engine.protocol.SSHProtocol;
import com.sentrysoftware.matrix.engine.protocol.WMIProtocol;
import com.sentrysoftware.matrix.engine.strategy.StrategyConfig;
import com.sentrysoftware.matrix.engine.strategy.detection.CriterionVisitor.NamespaceResult;
import com.sentrysoftware.matrix.engine.strategy.detection.CriterionVisitor.PossibleNamespacesResult;
import com.sentrysoftware.matrix.engine.strategy.matsya.MatsyaClientsExecutor;
import com.sentrysoftware.matrix.engine.strategy.utils.OsCommandHelper;
import com.sentrysoftware.matrix.engine.target.HardwareTarget;
import com.sentrysoftware.matrix.engine.target.TargetType;
import com.sentrysoftware.matrix.model.monitoring.HostMonitoring;
import com.sentrysoftware.matrix.model.monitoring.IHostMonitoring;
import com.sentrysoftware.matsya.wmi.exceptions.WmiComException;

@ExtendWith(MockitoExtension.class)
class CriterionVisitorTest {

	private static final String MANAGEMENT_CARD_HOST = "management-card-host";
	private static final String HOST_LINUX = "host-linux";
	private static final String HOST_WIN = "host-win";
	private static final String AUTOMATIC = "Automatic";
	private static final String ROOT_HPQ_NAMESPACE = "root\\hpq";
	private static final String NAMESPACE_WMI_QUERY = "SELECT Name FROM __NAMESPACE";
	private static final String WMI_WQL = "SELECT Version FROM IBMPSG_UniversalManageabilityServices";
	private static final String UCS_EXPECTED = "UCS";
	private static final String UCS_SYSTEM_CISCO_RESULT = "UCS System Cisco";
	private static final String RESULT_4 = "1.3.6.1.4.1.674.10893.1.20.1 ASN_OCT";
	private static final String RESULT_3 = "1.3.6.1.4.1.674.10893.1.20.1 ASN_OCT 2.4.6";
	private static final String RESULT_2 = "1.3.6.1.4.1.674.10893.1.20.1 ASN_INTEGER 1";
	private static final String RESULT_1 = "1.3.6.1.4.1.674.99999.1.20.1 ASN_INTEGER 1";
	private static final String ECS1_01 = "ecs1-01";
	private static final String VERSION = "2.4.6";
	private static final String EMPTY = "";
	private static final String OID = "1.3.6.1.4.1.674.10893.1.20";

	private static final String PUREM_SAN = "purem-san";
	private static final String FOO = "FOO";
	private static final String BAR = "BAR";
	private static final String PC14 = "pc14";
	
	private static final String USERNAME = "username";
	private static final char[] PASSWORD = "password".toCharArray();
	private static final Long TIME_OUT = 120L;

	@Mock
	private StrategyConfig strategyConfig;

	@Mock
	private MatsyaClientsExecutor matsyaClientsExecutor;

	@InjectMocks
	private CriterionVisitor criterionVisitor;

	@InjectMocks
	@Spy
	private CriterionVisitor criterionVisitorSpy;

	private static EngineConfiguration engineConfiguration;

	private void initHTTP() {

		if (engineConfiguration != null
			&& engineConfiguration.getProtocolConfigurations().get(HTTPProtocol.class) != null) {

			return;
		}

		HTTPProtocol protocol = HTTPProtocol
			.builder()
			.port(443)
			.timeout(120L)
			.build();

		engineConfiguration = EngineConfiguration
			.builder()
			.target(HardwareTarget.builder().hostname(PUREM_SAN).id(PUREM_SAN).type(TargetType.LINUX).build())
			.protocolConfigurations(Map.of(HTTPProtocol.class, protocol))
			.build();
	}

	@Test
	void testVisitHTTPFailure() {

		// null HTTP
		assertEquals(CriterionTestResult.empty(), criterionVisitor.visit((HTTP) null));

		// HTTP is not null, protocol is null
		engineConfiguration = EngineConfiguration.builder().build();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		HTTP http = new HTTP();
		assertEquals(CriterionTestResult.empty(), criterionVisitor.visit(http));
		verify(strategyConfig).getEngineConfiguration();

		// HTTP is not null, protocol is not null, expectedResult is null, result is null
		initHTTP();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(null).when(matsyaClientsExecutor).executeHttp(any(), any(), any(), eq(false));
		CriterionTestResult criterionTestResult = criterionVisitor.visit(http);
		verify(strategyConfig, times(2)).getEngineConfiguration();
		verify(matsyaClientsExecutor).executeHttp(any(), any(), any(), eq(false));
		assertNotNull(criterionTestResult);
		assertNull(criterionTestResult.getResult());
		assertFalse(criterionTestResult.isSuccess());

		// HTTP is not null, protocol is not null, expectedResult is null, result is empty
		doReturn(EMPTY).when(matsyaClientsExecutor).executeHttp(any(), any(), any(), eq(false));
		criterionTestResult = criterionVisitor.visit(http);
		verify(strategyConfig, times(3)).getEngineConfiguration();
		verify(matsyaClientsExecutor, times(2)).executeHttp(any(), any(), any(), eq(false));
		assertNotNull(criterionTestResult);
		assertEquals(EMPTY, criterionTestResult.getResult());
		assertFalse(criterionTestResult.isSuccess());

		// HTTP is not null, protocol is not null, expectedResult is not null, result is null
		doReturn(null).when(matsyaClientsExecutor).executeHttp(any(), any(), any(), eq(false));
		http.setExpectedResult(FOO);
		criterionTestResult = criterionVisitor.visit(http);
		verify(strategyConfig, times(4)).getEngineConfiguration();
		verify(matsyaClientsExecutor, times(3)).executeHttp(any(), any(), any(), eq(false));
		assertNotNull(criterionTestResult);
		assertNull(criterionTestResult.getResult());
		assertFalse(criterionTestResult.isSuccess());

		// HTTP is not null, protocol is not null, expectedResult is not null, result is not null and does not match
		doReturn(BAR).when(matsyaClientsExecutor).executeHttp(any(), any(), any(), eq(false));
		criterionTestResult = criterionVisitor.visit(http);
		verify(strategyConfig, times(5)).getEngineConfiguration();
		verify(matsyaClientsExecutor, times(4)).executeHttp(any(), any(), any(), eq(false));
		assertNotNull(criterionTestResult);
		assertEquals(BAR, criterionTestResult.getResult());
		assertFalse(criterionTestResult.isSuccess());
	}

	@Test
	void testVisitHTTPSuccess() {

		initHTTP();
		HTTP http = new HTTP();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

		// HTTP is not null, protocol is not null, expectedResult is null, result is neither null nor empty
		doReturn(FOO).when(matsyaClientsExecutor).executeHttp(any(), any(), any(), eq(false));
		CriterionTestResult criterionTestResult = criterionVisitor.visit(http);
		verify(strategyConfig).getEngineConfiguration();
		verify(matsyaClientsExecutor).executeHttp(any(), any(), any(), eq(false));
		assertNotNull(criterionTestResult);
		assertEquals(FOO, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());

		// HTTP is not null, protocol is not null, expectedResult is not null, result is not null and matches
		http.setExpectedResult(FOO);
		criterionTestResult = criterionVisitor.visit(http);
		verify(strategyConfig, times(2)).getEngineConfiguration();
		verify(matsyaClientsExecutor, times(2)).executeHttp(any(), any(), any(), eq(false));
		assertNotNull(criterionTestResult);
		assertEquals(FOO, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
	}

	@Test
	void testVisitIPMIWindowsFailure() throws Exception {
		// No WMI protocol
		Map<Class<? extends IProtocolConfiguration>, IProtocolConfiguration> protocolConfigurations = new HashMap<>();
		final EngineConfiguration engineConfiguration = EngineConfiguration
				.builder()
				.target(HardwareTarget.builder()
						.hostname(HOST_WIN)
						.id(HOST_WIN)
						.type(TargetType.MS_WINDOWS)
						.build())
				.protocolConfigurations(protocolConfigurations)
				.build();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

		IPMI ipmi = IPMI.builder().forceSerialization(true).build();
		CriterionTestResult criterionTestResult = criterionVisitor.visit(ipmi);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals("No WMI credentials provided.", criterionTestResult.getMessage());

		// matsyaClientsExecutor gives null result to WMI request
		WMIProtocol wmiProtocol = WMIProtocol.builder()
				.namespace(HOST_WIN)
				.username(USERNAME)
				.password(PASSWORD)
				.timeout(TIME_OUT)
				.build();
		protocolConfigurations.put(wmiProtocol.getClass(), wmiProtocol);

		doReturn(null).when(matsyaClientsExecutor).executeWmi(HOST_WIN,
				USERNAME,
				PASSWORD,
				TIME_OUT,
				"SELECT Description FROM ComputerSystem",
				HOST_WIN);

		criterionTestResult = criterionVisitor.visit(ipmi);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals("The Microsoft IPMI WMI provider did not report the presence of any BMC controller.", criterionTestResult.getMessage());

		// Exception thrown by matsyaClientsExecutor when executing the WMI request
		doThrow(new WmiComException("Unit test error")).when(matsyaClientsExecutor).executeWmi(HOST_WIN,
				USERNAME,
				PASSWORD,
				TIME_OUT,
				"SELECT Description FROM ComputerSystem",
				HOST_WIN);

		criterionTestResult = criterionVisitor.visit(ipmi);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals("Ipmi Test Failed - WMI request was unsuccessful due to an exception. Message: Unit test error.", criterionTestResult.getMessage());

	}

	@Test
	void testVisitIPMIWindowsSuccess() throws Exception {
		Map<Class<? extends IProtocolConfiguration>, IProtocolConfiguration> protocolConfigurations = new HashMap<>();
		WMIProtocol wmiProtocol = WMIProtocol.builder()
				.namespace(HOST_WIN)
				.username(USERNAME)
				.password(PASSWORD)
				.timeout(TIME_OUT)
				.build();
		protocolConfigurations.put(wmiProtocol.getClass(), wmiProtocol);
		final EngineConfiguration engineConfiguration = EngineConfiguration
				.builder()
				.target(HardwareTarget.builder()
						.hostname(HOST_WIN)
						.id(HOST_WIN)
						.type(TargetType.MS_WINDOWS)
						.build())
				.protocolConfigurations(protocolConfigurations)
				.build();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

		String resultWmi = "System description";
		String resultFinal = "System description;";

		doReturn(Collections.singletonList(Collections.singletonList(resultWmi))).when(matsyaClientsExecutor).executeWmi(HOST_WIN,
				USERNAME,
				PASSWORD,
				TIME_OUT,
				"SELECT Description FROM ComputerSystem",
				HOST_WIN);

		CriterionTestResult criterionTestResult = criterionVisitor.visit(IPMI.builder().forceSerialization(true).build());

		assertNotNull(criterionTestResult);
		assertEquals(resultFinal, criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
	}


	@Test
	@EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
	void testRunOsCommandWindows() throws InterruptedException, IOException {
		HostMonitoring hostMonitoring = new HostMonitoring();
		hostMonitoring.setLocalhost(true);
		doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
		String version = criterionVisitor.runOsCommand("ver", "localhost", null, 120,
				OsCommandHelper.CommandTypeEnum.CMD);
		assertTrue(version.startsWith("Microsoft Windows"));
	}

	@Test
	@EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
	void testRunOsCommandLinux() throws InterruptedException, IOException {
		HostMonitoring hostMonitoring = new HostMonitoring();
		hostMonitoring.setLocalhost(true);
		doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
		String version = criterionVisitor.runOsCommand("uname -a", "localhost", null, 120,
				OsCommandHelper.CommandTypeEnum.SHELL);
		assertTrue(version.startsWith("Linux"));
	}

	@Test
	void testVisitIPMILinux() throws IOException, InterruptedException {
		// osConfig null
		{
			final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
					.target(HardwareTarget.builder().hostname(HOST_LINUX).id(HOST_LINUX).type(TargetType.LINUX).build())

					.build();

			HostMonitoring hostMonitoring = new HostMonitoring();
			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
			doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
			assertEquals(CriterionTestResult.builder().result("").success(false)
					.message("Couldn't OS Command Configuration on " + HOST_LINUX + ". Retrun empty result.").build(),
					criterionVisitor.visit(new IPMI()));
		}
		SSHProtocol ssh = SSHProtocol.sshProtocolBuilder().username("root").password("nationale".toCharArray()).build();
		{
			// wrong IPMIToolCommand
			final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
					.target(HardwareTarget.builder().hostname(HOST_LINUX).id(HOST_LINUX).type(TargetType.LINUX).build())
					.protocolConfigurations(Map.of(HTTPProtocol.class, OSCommandConfig.builder().build(),
											OSCommandConfig.class, OSCommandConfig.builder().build(), 
											SSHProtocol.class, ssh))
					.build();

			HostMonitoring hostMonitoring = new HostMonitoring();
			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
			doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
			doReturn("blabla").when(criterionVisitorSpy).buildIpmiCommand(eq(TargetType.LINUX), any(), any(), any(),
					eq(120));
			assertFalse(criterionVisitorSpy.visit(new IPMI()).isSuccess());

		}
		{
			// wrong result when running IPMIToolCommand
			final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
					.target(HardwareTarget.builder().hostname(HOST_LINUX).id(HOST_LINUX).type(TargetType.LINUX).build())
					.protocolConfigurations(Map.of(HTTPProtocol.class, OSCommandConfig.builder().build(),
													OSCommandConfig.class, OSCommandConfig.builder().build(), 
													SSHProtocol.class, ssh))
					.build();

			HostMonitoring hostMonitoring = new HostMonitoring();
			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
			doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
			doReturn("PATH=blabla").when(criterionVisitorSpy).buildIpmiCommand(eq(TargetType.LINUX), any(), any(),
					any(), eq(120));
			doReturn("wrong result").when(criterionVisitorSpy).runOsCommand("PATH=blabla", HOST_LINUX, ssh, 120,
					OsCommandHelper.CommandTypeEnum.SHELL);
			assertFalse(criterionVisitorSpy.visit(new IPMI()).isSuccess());

		}

		final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
				.target(HardwareTarget.builder().hostname(HOST_LINUX).id(HOST_LINUX).type(TargetType.LINUX).build())
				.protocolConfigurations(Map.of(HTTPProtocol.class, OSCommandConfig.builder().build(),
												OSCommandConfig.class, OSCommandConfig.builder().build(), 
												SSHProtocol.class, ssh))
				.build();

		HostMonitoring hostMonitoring = new HostMonitoring();
		doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		// set ssh
		String ipmiResultExample = "Device ID                 : 3\r\n" + "Device Revision           : 3\r\n"
				+ "Firmware Revision         : 4.10\r\n" + "IPMI Version              : 2.0\r\n"
				+ "Manufacturer ID           : 10368\r\n" + "Manufacturer Name         : Fujitsu Siemens\r\n"
				+ "Product ID                : 790 (0x0316)\r\n" + "Product Name              : Unknown (0x316)";
		doReturn(ipmiResultExample).when(matsyaClientsExecutor).runRemoteSshCommand(any(), any(), any(), any(), any(),
				eq(120));
		assertEquals(CriterionTestResult.builder().result(ipmiResultExample).success(true)
				.message("Successfully connected to the IPMI BMC chip with the in-band driver interface.").build(),
				criterionVisitor.visit(new IPMI()));

		// run localhost command
		final EngineConfiguration engineConfigurationLocal = EngineConfiguration.builder()
				.target(HardwareTarget.builder().hostname("localhost").id("localhost").type(TargetType.SUN_SOLARIS)
						.build())
				.protocolConfigurations(Map.of(HTTPProtocol.class, OSCommandConfig.builder().build(),
												OSCommandConfig.class, OSCommandConfig.builder().build(), 
												SSHProtocol.class, ssh))
				.build();
		hostMonitoring.setLocalhost(true);
		doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
		doReturn(engineConfigurationLocal).when(strategyConfig).getEngineConfiguration();
		// here the try is important because it only will mock the static reference for
		// the following context.
		// Otherwise even for other contexts/methods it will always return the same
		// result (it is static..)
		try (MockedStatic<OsCommandHelper> oscmd = mockStatic(OsCommandHelper.class)) {
			oscmd.when(() -> OsCommandHelper.runLocalCommand(any(), any())).thenReturn(ipmiResultExample);
			assertEquals(CriterionTestResult.builder().result(ipmiResultExample).success(true)
					.message("Successfully connected to the IPMI BMC chip with the in-band driver interface.").build(),
					criterionVisitor.visit(new IPMI()));
		}

	}

	@Test
	void testRunOsCommand() throws InterruptedException, IOException {
		HostMonitoring hostMonitoring = new HostMonitoring();
		hostMonitoring.setLocalhost(false);
		doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
		SSHProtocol ssh = SSHProtocol.sshProtocolBuilder().username("root").password("nationale".toCharArray()).build();

		String cmdResult = criterionVisitor.runOsCommand(null, "localhost", ssh, 120, null);
		assertNull(cmdResult);

		cmdResult = criterionVisitor.runOsCommand("cmd", "localhost", null, 120, null);
		assertNull(cmdResult);

		cmdResult = criterionVisitor.runOsCommand("cmd", null, ssh, 120, null);
		assertNull(cmdResult);

		doReturn("something").when(matsyaClientsExecutor).runRemoteSshCommand(any(), any(), any(), any(), any(),
				eq(120));
		cmdResult = criterionVisitor.runOsCommand("cmd", "localhost", ssh, 120, OsCommandHelper.CommandTypeEnum.CMD);
		assertEquals("something", cmdResult);

	}

	@Test
	void testBuildIpmiCommand() {
		SSHProtocol ssh = SSHProtocol.sshProtocolBuilder().username("root").password("nationale".toCharArray()).build();
		OSCommandConfig osCommandConfig = OSCommandConfig.builder().build();
		{
			// test Solaris
			HostMonitoring hostMonitoring = new HostMonitoring();
			hostMonitoring.setLocalhost(true);
			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
			try (MockedStatic<OsCommandHelper> oscmd = mockStatic(OsCommandHelper.class)) {
				oscmd.when(() -> OsCommandHelper.runLocalCommand(any(), any())).thenReturn("5.10");
				String cmdResult = criterionVisitor.buildIpmiCommand(TargetType.SUN_SOLARIS, "toto", ssh,
						osCommandConfig, 120);
				assertNotNull(cmdResult);
				assertTrue(cmdResult.startsWith("PATH")); // Successful command
			}

			try (MockedStatic<OsCommandHelper> oscmd = mockStatic(OsCommandHelper.class)) {
				oscmd.when(() -> OsCommandHelper.runLocalCommand(any(), any())).thenReturn("blabla");
				String cmdResult = criterionVisitor.buildIpmiCommand(TargetType.SUN_SOLARIS, "toto", ssh,
						osCommandConfig, 120);
				assertNotNull(cmdResult);
				assertTrue(cmdResult.startsWith("Couldn't")); // Not Successful command the response starts with
																// Couldn't identify
			}

			try (MockedStatic<OsCommandHelper> oscmd = mockStatic(OsCommandHelper.class)) {
				osCommandConfig.setUseSudo(true);
				oscmd.when(() -> OsCommandHelper.runLocalCommand(any(), any())).thenReturn("5.10");
				String cmdResult = criterionVisitor.buildIpmiCommand(TargetType.SUN_SOLARIS, "toto", ssh,
						osCommandConfig, 120);
				assertNotNull(cmdResult);
				assertTrue(cmdResult.contains("sudo")); // Successful sudo command
			}
		}

		{
			// test Linux
			osCommandConfig.setUseSudo(false);
			String cmdResult = criterionVisitor.buildIpmiCommand(TargetType.LINUX, "toto", ssh, osCommandConfig, 120);
			assertEquals("PATH=$PATH:/usr/local/bin:/usr/sfw/bin;export PATH;ipmitool -I open bmc info", cmdResult);
		}

	}

	@Test
	void testGetIpmiCommandForSolaris() throws Exception {
		String ipmitoolCommand = "ipmitoolCommand ";
		{ // Solaris Version 10 => bmc
			String cmdResult = criterionVisitor.getIpmiCommandForSolaris(ipmitoolCommand, "toto", "5.10");
			assertEquals("ipmitoolCommand bmc", cmdResult);
		}
		{ // Solaris version 9 => lipmi
			String cmdResult = criterionVisitor.getIpmiCommandForSolaris(ipmitoolCommand, "toto", "5.9");
			assertEquals("ipmitoolCommand lipmi", cmdResult);
		}
		

		{// wrong String OS version
			Exception exception = assertThrows(Exception.class, () -> {
				criterionVisitor.getIpmiCommandForSolaris(ipmitoolCommand, "toto", "blabla");
			});

			String expectedMessage = "Unkown Solaris version";
			String actualMessage = exception.getMessage();

			assertTrue(actualMessage.contains(expectedMessage));
		}
		{// old OS version
			Exception exception = assertThrows(Exception.class, () -> {
				criterionVisitor.getIpmiCommandForSolaris(ipmitoolCommand, "toto", "4.1.1B");
			});

			String expectedMessage = "Solaris version (4.1.1B) is too old";
			String actualMessage = exception.getMessage();

			assertTrue(actualMessage.contains(expectedMessage));
		}
	}

	@Test
	void testVisitIPMIOutOfBand() {
		final EngineConfiguration engineConfiguration = EngineConfiguration
				.builder()
				.target(HardwareTarget.builder()
						.hostname(MANAGEMENT_CARD_HOST)
						.id(MANAGEMENT_CARD_HOST)
						.type(TargetType.MGMT_CARD_BLADE_ESXI)
						.build())
				.protocolConfigurations(Map.of(HTTPProtocol.class, IPMIOverLanProtocol.builder().build()))
				.build();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		assertEquals(CriterionTestResult.empty(), criterionVisitor.visit(new IPMI()));
	}

	@Test
	void testVisitKMVersion() {
		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit(new KMVersion()));
	}

	@Test
	void testVisitOS() {
		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit(new OS()));
	}

	@Test
	void testVisitOSCommand() {
		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit(new OSCommand()));
	}

	@Test
	void testVisitProcess() {
		;
		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit(new Process()));
	}

	@Test
	void testVisitService() {
		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit(new Service()));
	}

	private void initSNMP() {

		if (engineConfiguration != null
			&& engineConfiguration.getProtocolConfigurations().get(SNMPProtocol.class) != null) {

			return;
		}

		SNMPProtocol protocol = SNMPProtocol
			.builder()
			.community("public")
			.version(SNMPVersion.V1)
			.port(161)
			.timeout(120L)
			.build();

		engineConfiguration = EngineConfiguration
			.builder()
			.target(HardwareTarget.builder().hostname(ECS1_01).id(ECS1_01).type(TargetType.LINUX).build())
			.protocolConfigurations(Map.of(SNMPProtocol.class, protocol))
			.build();
	}

	@Test
	void testVisitSNMPGetException() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doThrow(new TimeoutException("SNMPGet timeout")).when(matsyaClientsExecutor).executeSNMPGet(any(),
				any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGet.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"SNMP Test Failed - SNMP Get of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01 was unsuccessful due to an exception. Message: SNMPGet timeout.")
				.build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetNullResult() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(null).when(matsyaClientsExecutor).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGet.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"SNMP Test Failed - SNMP Get of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01 was unsuccessful due to a null result.")
				.build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetEmptyResult() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(EMPTY).when(matsyaClientsExecutor).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGet.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"SNMP Test Failed - SNMP Get of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01 was unsuccessful due to an empty result.")
				.result(EMPTY).build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetSuccessWithNoExpectedResult() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(UCS_SYSTEM_CISCO_RESULT).when(matsyaClientsExecutor).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGet.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"Successful SNMP Get of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01. Returned Result: UCS System Cisco.")
				.result(UCS_SYSTEM_CISCO_RESULT)
				.success(true).build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetExpectedResultNotMatches() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(UCS_SYSTEM_CISCO_RESULT).when(matsyaClientsExecutor).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGet.builder().oid(OID).expectedResult(VERSION).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"SNMP Test Failed - SNMP Get of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01 was successful but the value of the returned OID did not match with the expected result. Expected value: 2.4.6 - returned value UCS System Cisco.")
				.result(UCS_SYSTEM_CISCO_RESULT)
				.build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetExpectedResultMatches() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(UCS_SYSTEM_CISCO_RESULT).when(matsyaClientsExecutor).executeSNMPGet(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGet.builder().oid(OID).expectedResult(UCS_EXPECTED).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"Successful SNMP Get of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01. Returned Result: UCS System Cisco.")
				.result(UCS_SYSTEM_CISCO_RESULT)
				.success(true)
				.build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetReturnsEmptyResult() {

		initSNMP();

		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit((SNMPGet) null));
		assertEquals(CriterionTestResult.empty(),
				new CriterionVisitor().visit(SNMPGet.builder().oid(null).build()));
		doReturn(new EngineConfiguration()).when(strategyConfig).getEngineConfiguration();
		assertEquals(CriterionTestResult.empty(),
				criterionVisitor.visit(SNMPGet.builder().oid(OID).build()));
	}

	@Test
	void testVisitTelnetInteractive() {
		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit(new TelnetInteractive()));
	}

	@Test
	void testVisitUCS() {
		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit(new UCS()));
	}

	@Test
	void testVisitWBEM() {
		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit(new WBEM()));
	}

	@Test
	void testVisitWMI() {
		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit(new WMI()));
	}

	@Test
	void testVisitSNMPGetNextException() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doThrow(new TimeoutException("SNMPGetNext timeout")).when(matsyaClientsExecutor).executeSNMPGetNext(any(),
				any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGetNext.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"SNMP Test Failed - SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01 was unsuccessful due to an exception. Message: SNMPGetNext timeout.")
				.build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetNextNullResult() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(null).when(matsyaClientsExecutor).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGetNext.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"SNMP Test Failed - SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01 was unsuccessful due to a null result.")
				.build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetNextEmptyResult() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(EMPTY).when(matsyaClientsExecutor).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGetNext.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"SNMP Test Failed - SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01 was unsuccessful due to an empty result.")
				.result(EMPTY).build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetNextNotSameSubTreeOID() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(RESULT_1).when(matsyaClientsExecutor).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGetNext.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"SNMP Test Failed - SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01 was successful but the returned OID is not under the same tree. Returned OID: 1.3.6.1.4.1.674.99999.1.20.1.")
				.result(RESULT_1).build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetNextSuccessWithNoExpectedResult() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(RESULT_2).when(matsyaClientsExecutor).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGetNext.builder().oid(OID).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"Successful SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01. Returned Result: 1.3.6.1.4.1.674.10893.1.20.1 ASN_INTEGER 1.")
				.result(RESULT_2)
				.success(true).build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetNextExpectedResultNotMatches() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(RESULT_2).when(matsyaClientsExecutor).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGetNext.builder().oid(OID).expectedResult(VERSION).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"SNMP Test Failed - SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01 was successful but the value of the returned OID did not match with the expected result. Expected value: 2.4.6 - returned value 1.")
				.result(RESULT_2)
				.build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetNextExpectedResultMatches() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(RESULT_3).when(matsyaClientsExecutor).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGetNext.builder().oid(OID).expectedResult(VERSION).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"Successful SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01. Returned Result: 1.3.6.1.4.1.674.10893.1.20.1 ASN_OCT 2.4.6.")
				.result(RESULT_3)
				.success(true)
				.build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetNextExpectedResultCannotExtract() throws Exception {

		initSNMP();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(RESULT_4).when(matsyaClientsExecutor).executeSNMPGetNext(any(), any(), any(), eq(false));
		final CriterionTestResult actual = criterionVisitor.visit(SNMPGetNext.builder().oid(OID).expectedResult(VERSION).build());
		final CriterionTestResult expected = CriterionTestResult.builder().message(
				"SNMP Test Failed - SNMP GetNext of 1.3.6.1.4.1.674.10893.1.20 on ecs1-01 was successful but the value cannot be extracted. Returned Result: 1.3.6.1.4.1.674.10893.1.20.1 ASN_OCT.")
				.result(RESULT_4)
				.build();
		assertEquals(expected, actual);
	}

	@Test
	void testVisitSNMPGetNextReturnsEmptyResult() {

		initSNMP();

		assertEquals(CriterionTestResult.empty(), new CriterionVisitor().visit((SNMPGetNext) null));
		assertEquals(CriterionTestResult.empty(),
				new CriterionVisitor().visit(SNMPGetNext.builder().oid(null).build()));
		doReturn(new EngineConfiguration()).when(strategyConfig).getEngineConfiguration();
		assertEquals(CriterionTestResult.empty(),
				criterionVisitor.visit(SNMPGetNext.builder().oid(OID).build()));
	}

	@Test
	void testExtractPossibleNamespaces() {
		final Set<String> result = CriterionVisitor.extractPossibleNamespaces(Arrays.asList(
				Collections.emptyList(),
				Collections.singletonList("hpq"),
				Collections.singletonList("interop"),
				Collections.singletonList("SECURITY")));

		assertEquals(Set.of(ROOT_HPQ_NAMESPACE), result);

	}

	@Test
	void testDetectPossibleWmiNamespacesAlreadyDetected() {
		final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).build();
		final WMIProtocol protocol = WMIProtocol.builder()
				.username(PC14 + "\\" + "Administrator")
				.password("password".toCharArray())
				.build();

		final IHostMonitoring hostMonitoring = new HostMonitoring();
		hostMonitoring.getPossibleWmiNamespaces().add(ROOT_HPQ_NAMESPACE);
		doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();

		assertEquals(PossibleNamespacesResult.builder().possibleNamespaces(Set.of(ROOT_HPQ_NAMESPACE)).success(true).build(),
				criterionVisitor.detectPossibleWmiNamespaces(wmi, protocol));

	}

	@Test 
	void testDetectPossibleWmiNamespaces() throws Exception {
		final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).build();
		final WMIProtocol protocol = WMIProtocol.builder()
				.username(PC14 + "\\" + "Administrator")
				.password("password".toCharArray())
				.build();
		final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
				.target(HardwareTarget.builder().hostname(PC14).id(PC14).type(TargetType.MS_WINDOWS).build())
				.protocolConfigurations(Map.of(WMIProtocol.class, protocol))
				.build();

		doReturn(new HostMonitoring()).when(strategyConfig).getHostMonitoring();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

		final List<List<String>> wqlResult = Arrays.asList(List.of("hpq"), List.of("SECURITY"), List.of("Cli"));

		doReturn(wqlResult).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator", "password".toCharArray(),
				protocol.getTimeout(), NAMESPACE_WMI_QUERY, "root");

		final PossibleNamespacesResult actual = criterionVisitor.detectPossibleWmiNamespaces(wmi, protocol);
		final PossibleNamespacesResult expected = PossibleNamespacesResult.builder()
				.possibleNamespaces(Set.of(ROOT_HPQ_NAMESPACE))
				.success(true)
				.build();

		assertEquals(expected, actual);
	}

	@Test 
	void testDetectPossibleWmiNamespacesException() throws Exception {
		final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).build();
		final WMIProtocol protocol = WMIProtocol.builder()
				.username(PC14 + "\\" + "Administrator")
				.password("password".toCharArray())
				.build();
		final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
				.target(HardwareTarget.builder().hostname(PC14).id(PC14).type(TargetType.MS_WINDOWS).build())
				.protocolConfigurations(Map.of(WMIProtocol.class, protocol))
				.build();

		doReturn(new HostMonitoring()).when(strategyConfig).getHostMonitoring();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

		doThrow(new TimeoutException()).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator", "password".toCharArray(),
				protocol.getTimeout(), NAMESPACE_WMI_QUERY, "root");

		final PossibleNamespacesResult actual = criterionVisitor.detectPossibleWmiNamespaces(wmi, protocol);

		assertFalse(actual.isSuccess());
	}

	@Test 
	void testDetectPossibleWmiNamespacesEmpty() throws Exception {
		final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).build();
		final WMIProtocol protocol = WMIProtocol.builder()
				.username(PC14 + "\\" + "Administrator")
				.password("password".toCharArray())
				.build();
		final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
				.target(HardwareTarget.builder().hostname(PC14).id(PC14).type(TargetType.MS_WINDOWS).build())
				.protocolConfigurations(Map.of(WMIProtocol.class, protocol))
				.build();

		doReturn(new HostMonitoring()).when(strategyConfig).getHostMonitoring();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

		doReturn(Collections.emptyList()).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator", "password".toCharArray(),
				protocol.getTimeout(), NAMESPACE_WMI_QUERY, "root");

		final PossibleNamespacesResult actual = criterionVisitor.detectPossibleWmiNamespaces(wmi, protocol);

		assertFalse(actual.isSuccess());
	}

	@Test
	void testFindNamespaceAutomaticAlreadyDetected() {
		final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).wbemNamespace(AUTOMATIC).build();
		final WMIProtocol protocol = WMIProtocol.builder()
				.username(PC14 + "\\" + "Administrator")
				.password("password".toCharArray())
				.build();

		final IHostMonitoring hostMonitoring = new HostMonitoring();
		hostMonitoring.setAutomaticWmiNamespace(ROOT_HPQ_NAMESPACE);

		doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();

		final NamespaceResult result = criterionVisitor.findNamespace(wmi, protocol);
		assertTrue(result.isSuccess());
		assertEquals(ROOT_HPQ_NAMESPACE, result.getNamespace());
	}

	@Test
	void testFindNamespaceNotAutomatic() {
		{
			final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).wbemNamespace(ROOT_HPQ_NAMESPACE).build();
			final WMIProtocol protocol = WMIProtocol.builder()
					.username(PC14 + "\\" + "Administrator")
					.password("password".toCharArray())
					.build();

			final NamespaceResult result = criterionVisitor.findNamespace(wmi, protocol);
			assertTrue(result.isSuccess());
			assertEquals(ROOT_HPQ_NAMESPACE, result.getNamespace());
		}

		{
			final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).build();
			final WMIProtocol protocol = WMIProtocol.builder()
					.username(PC14 + "\\" + "Administrator")
					.password("password".toCharArray())
					.build();

			final NamespaceResult result = criterionVisitor.findNamespace(wmi, protocol);
			assertTrue(result.isSuccess());
			assertEquals("root/cimv2", result.getNamespace());
		}
	}

	@Test
	void testFindNamespaceAutomatic() throws Exception {

		{
			final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).wbemNamespace(AUTOMATIC).expectedResult("^ibm.*$").build();
			final WMIProtocol protocol = WMIProtocol.builder()
					.username(PC14 + "\\" + "Administrator")
					.password("password".toCharArray())
					.build();
			final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
					.target(HardwareTarget.builder().hostname(PC14).id(PC14).type(TargetType.MS_WINDOWS).build())
					.protocolConfigurations(Map.of(WMIProtocol.class, protocol))
					.build();
			final IHostMonitoring hostMonitoring = new HostMonitoring();
			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
			doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

			doReturn(Arrays.asList(List.of("ibmsd"),
					List.of("cimv2"),
					List.of("ibm"),
					List.of("ibm2"))).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator", "password".toCharArray(),
					protocol.getTimeout(), NAMESPACE_WMI_QUERY, "root");

			doReturn(List.of(List.of("ibm system version 1.0.00"))).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator", "password".toCharArray(),
					protocol.getTimeout(), WMI_WQL, "root\\ibmsd");
			doReturn(Collections.emptyList()).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator", "password".toCharArray(),
					protocol.getTimeout(), WMI_WQL, "root\\ibm2");
			doReturn(List.of(List.of("ibm system version 1.0.00"))).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator", "password".toCharArray(),
					protocol.getTimeout(), WMI_WQL, "root\\cimv2");
			doThrow(new TimeoutException("Test timeout exception")).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator", "password".toCharArray(),
					protocol.getTimeout(), WMI_WQL, "root\\ibm");
			final NamespaceResult result = criterionVisitor.findNamespace(wmi, protocol);

			assertTrue(result.isSuccess());
			assertEquals("root\\ibmsd", result.getNamespace());
			assertEquals("root\\ibmsd", hostMonitoring.getAutomaticWmiNamespace());
		}

		{
			final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).wbemNamespace(AUTOMATIC).expectedResult(null).build();
			final WMIProtocol protocol = WMIProtocol.builder()
					.username(PC14 + "\\" + "Administrator")
					.password("password".toCharArray())
					.build();
			final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
					.target(HardwareTarget.builder().hostname(PC14).id(PC14).type(TargetType.MS_WINDOWS).build())
					.protocolConfigurations(Map.of(WMIProtocol.class, protocol))
					.build();
			final IHostMonitoring hostMonitoring = new HostMonitoring();
			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
			doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

			doReturn(List.of(List.of("ibmsd"))).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator", "password".toCharArray(),
					protocol.getTimeout(), NAMESPACE_WMI_QUERY, "root");

			final NamespaceResult result = criterionVisitor.findNamespace(wmi, protocol);

			assertTrue(result.isSuccess());
			assertEquals("root\\ibmsd", result.getNamespace());
			assertEquals("root\\ibmsd", hostMonitoring.getAutomaticWmiNamespace());
		}
	}

	@Test
	void testVisitWmiBadCriterion() {
		assertEquals(CriterionTestResult.empty(), criterionVisitor.visit(WMI.builder()
				.wbemNamespace(AUTOMATIC)
				.expectedResult(null)
				.build()));
		assertEquals(CriterionTestResult.empty(), criterionVisitor.visit((WMI) null));
	}

	@Test
	void testVisitWmiNoProtocol() {
		final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).wbemNamespace(AUTOMATIC).expectedResult(null).build();
		final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
				.target(HardwareTarget.builder().hostname(PC14).id(PC14).type(TargetType.MS_WINDOWS).build())
				.protocolConfigurations(Map.of(SNMPProtocol.class, new SNMPProtocol()))
				.build();

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

		assertEquals(CriterionTestResult.empty(), criterionVisitor.visit(wmi));
	}

	@Test
	void testVisitWmiCannotDetectNamespace() throws Exception {
		{
			final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).wbemNamespace(AUTOMATIC).expectedResult("^ibm.*$").build();
			final WMIProtocol protocol = WMIProtocol.builder()
					.username(PC14 + "\\" + "Administrator")
					.password("password".toCharArray())
					.build();
			final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
					.target(HardwareTarget.builder().hostname(PC14).id(PC14).type(TargetType.MS_WINDOWS).build())
					.protocolConfigurations(Map.of(WMIProtocol.class, protocol))
					.build();

			doReturn(new HostMonitoring()).when(strategyConfig).getHostMonitoring();
			doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
			doReturn(Collections.emptyList()).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator",
					"password".toCharArray(),
					protocol.getTimeout(),
					NAMESPACE_WMI_QUERY,
					"root");

			assertFalse(criterionVisitor.visit(wmi).isSuccess());
		}

		{
			final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).wbemNamespace(AUTOMATIC).expectedResult(null).build();
			final WMIProtocol protocol = WMIProtocol.builder()
					.username(PC14 + "\\" + "Administrator")
					.password("password".toCharArray())
					.build();
			final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
					.target(HardwareTarget.builder().hostname(PC14).id(PC14).type(TargetType.MS_WINDOWS).build())
					.protocolConfigurations(Map.of(WMIProtocol.class, protocol))
					.build();
			final IHostMonitoring hostMonitoring = new HostMonitoring();
			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
			doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

			doReturn(List.of(List.of("ibmsd"))).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator",
					"password".toCharArray(),
					protocol.getTimeout(),
					NAMESPACE_WMI_QUERY,
					"root");

			// Expected doesn't matches
			doReturn(Collections.emptyList()).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator",
					"password".toCharArray(),
					protocol.getTimeout(),
					WMI_WQL,
					"root\\ibmsd");

			assertFalse(criterionVisitor.visit(wmi).isSuccess());
		}
	}

	@Test
	void testVisitWmi() throws Exception {
		WMI wmi = WMI.builder().wbemQuery(WMI_WQL).wbemNamespace(AUTOMATIC).expectedResult("^ibm.*$").build();
		final WMIProtocol protocol = WMIProtocol.builder()
				.username(PC14 + "\\" + "Administrator")
				.password("password".toCharArray())
				.build();
		final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
				.target(HardwareTarget.builder().hostname(PC14).id(PC14).type(TargetType.MS_WINDOWS).build())
				.protocolConfigurations(Map.of(WMIProtocol.class, protocol))
				.build();
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

		doReturn(Arrays.asList(List.of("ibmsd"),
				List.of("cimv2"),
				List.of("ibm"),
				List.of("ibm2"))).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator",
						"password".toCharArray(),
						protocol.getTimeout(),
						NAMESPACE_WMI_QUERY,
						"root");

		doReturn(List.of(List.of("ibm system version 1.0.00"),
				List.of("controller version 8.8.00"))).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator", "password".toCharArray(),
				protocol.getTimeout(), WMI_WQL, "root\\ibmsd");

		assertTrue(criterionVisitor.visit(wmi).isSuccess());

		wmi = WMI.builder().wbemQuery(WMI_WQL).wbemNamespace(AUTOMATIC).expectedResult(null).build();
		assertTrue(criterionVisitor.visit(wmi).isSuccess());
	}

	@Test
	void testVisitWmiResultNotMatched() throws Exception {
		final WMI wmi = WMI.builder().wbemQuery(WMI_WQL).wbemNamespace(AUTOMATIC).expectedResult("^ibm.*$").build();
		final WMIProtocol protocol = WMIProtocol.builder()
				.username(PC14 + "\\" + "Administrator")
				.password("password".toCharArray())
				.build();
		final EngineConfiguration engineConfiguration = EngineConfiguration.builder()
				.target(HardwareTarget.builder().hostname(PC14).id(PC14).type(TargetType.MS_WINDOWS).build())
				.protocolConfigurations(Map.of(WMIProtocol.class, protocol))
				.build();
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		hostMonitoring.setAutomaticWmiNamespace("root\\ibmsd");
		doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

		doReturn(List.of(List.of("hp system version 1.0.00"),
				List.of("controller version 8.8.00"))).when(matsyaClientsExecutor)
		.executeWmi(PC14, PC14 + "\\" + "Administrator",
				"password".toCharArray(),
				protocol.getTimeout(),
				WMI_WQL,
				"root\\ibmsd");
		assertFalse(criterionVisitor.visit(wmi).isSuccess());

		doReturn(Collections.emptyList()).when(matsyaClientsExecutor)
		.executeWmi(PC14, PC14 + "\\" + "Administrator",
				"password".toCharArray(),
				protocol.getTimeout(),
				WMI_WQL,
				"root\\ibmsd");
		assertFalse(criterionVisitor.visit(wmi).isSuccess());

		// Exception
		doThrow(new RuntimeException("Test exception")).when(matsyaClientsExecutor).executeWmi(PC14, PC14 + "\\" + "Administrator",
				"password".toCharArray(),
				protocol.getTimeout(),
				WMI_WQL,
				"root\\ibmsd");

		assertFalse(criterionVisitor.visit(wmi).isSuccess());
	}
}
