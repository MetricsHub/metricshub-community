package com.sentrysoftware.hardware.cli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sentrysoftware.hardware.cli.component.cli.protocols.HTTPCredentials;
import com.sentrysoftware.hardware.cli.component.cli.protocols.IPMICredentials;
import com.sentrysoftware.hardware.cli.component.cli.protocols.SNMPCredentials;
import com.sentrysoftware.matrix.engine.protocol.HTTPProtocol;
import com.sentrysoftware.matrix.engine.protocol.IPMIOverLanProtocol;
import com.sentrysoftware.matrix.engine.protocol.SNMPProtocol;
import com.sentrysoftware.matrix.engine.protocol.SNMPProtocol.Privacy;
import com.sentrysoftware.matrix.engine.protocol.SNMPProtocol.SNMPVersion;

class EngineServiceTest {

	EngineService engineService = new EngineService();

	@Test
	void testGetConnectors() {

		final Set<String> allConnectors = Set.of("aa.hdfs", "bb.hdfs", "cc.hdfs", "dd.hdfs", "ee.hdfs");
		final Set<String> selected = Set.of("aa.hdfs", "bb.hdfs", "cc.hdfs");
		final Set<String> excluded = Set.of("aa.hdfs", "bb.hdfs");

		final Set<String> expectedInclusion = new HashSet<>(Arrays.asList("aa.connector", "bb.connector", "cc.connector"));
		Set<String> actual = EngineService.getConnectors(allConnectors, selected);
		assertEquals(expectedInclusion, actual);

		actual = EngineService.getConnectors(allConnectors, Collections.emptySet());
		assertEquals(Collections.emptySet(), actual);

		final Set<String> expectedExclusion = Set.of("aa.connector", "bb.connector");
		actual = EngineService.getConnectors(allConnectors, excluded);
		assertEquals(expectedExclusion, actual);

		actual = EngineService.getConnectors(allConnectors, Collections.emptySet());
		assertEquals(Collections.emptySet(), actual);

		actual = EngineService.getConnectors(allConnectors, null);
		assertEquals(Collections.emptySet(), actual);

	}

	@Test
	void getSNMPCredentialsTest() {
	
		SNMPCredentials snmpCred = new SNMPCredentials();
		snmpCred.setCommunity("comm1");
		snmpCred.setPassword("pwd1");
		snmpCred.setPort(110);
		snmpCred.setPrivacy(Privacy.AES);
		snmpCred.setPrivacyPassword("privPwd");
		snmpCred.setSnmpVersion(SNMPVersion.V1);
		snmpCred.setTimeout(10);
		snmpCred.setUsername("user");
		SNMPProtocol snmpProtocol = engineService.getSNMPProtocol(snmpCred);
		assertEquals("comm1", snmpProtocol.getCommunity());
		assertEquals("pwd1", snmpProtocol.getPassword());
		assertEquals(110, snmpProtocol.getPort());
		assertEquals(Privacy.AES, snmpProtocol.getPrivacy());
		assertEquals("privPwd", snmpProtocol.getPrivacyPassword());
		assertEquals(SNMPVersion.V1, snmpProtocol.getVersion());
		assertEquals(10, snmpProtocol.getTimeout());
		assertEquals("user", snmpProtocol.getUsername());
	}

	@Test
	void getHTTPCredentialsTest() {



		// httpCredentials is null
		assertThrows(IllegalArgumentException.class, () -> engineService.getHTTPProtocol(null));

		// httpCredentials is not null, password is null
		HTTPCredentials httpCredentials = new HTTPCredentials();
		HTTPProtocol httpProtocol = engineService.getHTTPProtocol(httpCredentials);
		assertNotNull(httpProtocol);
		assertTrue(httpProtocol.getHttps());
		assertEquals(0, httpProtocol.getPort());
		assertEquals(0L, httpProtocol.getTimeout());
		assertNull(httpProtocol.getUsername());
		assertNull(httpProtocol.getPassword());

		// httpCredentials is not null, password is not null
		String password = "password";
		httpCredentials.setPassword(password);
		httpProtocol = engineService.getHTTPProtocol(httpCredentials);
		assertNotNull(httpProtocol);
		assertTrue(httpProtocol.getHttps());
		assertEquals(0, httpProtocol.getPort());
		assertEquals(0L, httpProtocol.getTimeout());
		assertNull(httpProtocol.getUsername());
		assertTrue(Arrays.equals(password.toCharArray(), httpProtocol.getPassword()));
	}

	@Test
	void testGetIPMIProtocol() {
		IPMICredentials ipmiCredentials = new IPMICredentials();
		assertNotNull(engineService.getIPMIOverLanProtocol(ipmiCredentials));

		ipmiCredentials.setBmcKey("key");
		ipmiCredentials.setPassword("password");
		ipmiCredentials.setUsername("username");
		ipmiCredentials.setTimeout(120L);

		assertEquals(IPMIOverLanProtocol.builder()
				.bmcKey("key".getBytes())
				.password("password".toCharArray())
				.username("username")
				.skipAuth(false)
				.timeout(120L)
				.build(), engineService.getIPMIOverLanProtocol(ipmiCredentials));
	}
}
