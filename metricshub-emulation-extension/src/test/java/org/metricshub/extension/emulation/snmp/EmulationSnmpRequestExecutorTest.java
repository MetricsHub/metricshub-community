package org.metricshub.extension.emulation.snmp;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.extension.snmp.SnmpConfiguration;

/**
 * Tests for {@link EmulationSnmpRequestExecutor}.
 */
class EmulationSnmpRequestExecutorTest {

	private static final String HOSTNAME = "test-host";
	private static final SnmpConfiguration SNMP_CONFIG = SnmpConfiguration.builder().build();

	/**
	 * Standard walk file content with a few OIDs under 1.3.6.1.4.1.
	 */
	private static final String WALK_CONTENT =
		"""
		1.3.6.1.2.1.1.1.0\tOctetString\tLinux server 5.4.0
		1.3.6.1.2.1.1.3.0\tTimeTicks\t123456
		1.3.6.1.2.1.1.5.0\tOctetString\ttest-host
		1.3.6.1.2.1.2.2.1.1.1\tInteger\t1
		1.3.6.1.2.1.2.2.1.1.2\tInteger\t2
		1.3.6.1.2.1.2.2.1.2.1\tOctetString\teth0
		1.3.6.1.2.1.2.2.1.2.2\tOctetString\tlo
		1.3.6.1.2.1.2.2.1.5.1\tGauge32\t1000000000
		1.3.6.1.2.1.2.2.1.5.2\tGauge32\t10000000
		""";

	private void writeWalkFile(final Path snmpDir, final String filename, final String content) throws IOException {
		Files.createDirectories(snmpDir);
		Files.writeString(snmpDir.resolve(filename), content, StandardCharsets.UTF_8);
	}

	// ---- SNMP GET ----

	@Test
	void testExecuteSNMPGet(@TempDir Path tempDir) throws Exception {
		writeWalkFile(tempDir, "device.walk", WALK_CONTENT);
		final EmulationSnmpRequestExecutor executor = new EmulationSnmpRequestExecutor(tempDir.toString());

		final String result = executor.executeSNMPGet("1.3.6.1.2.1.1.1.0", SNMP_CONFIG, HOSTNAME, false, null);
		assertEquals("Linux server 5.4.0", result);
	}

	@Test
	void testExecuteSNMPGetOidNotFound(@TempDir Path tempDir) throws Exception {
		writeWalkFile(tempDir, "device.walk", WALK_CONTENT);
		final EmulationSnmpRequestExecutor executor = new EmulationSnmpRequestExecutor(tempDir.toString());

		// OID not in walk file → OfflineSnmpClient throws, executor returns null
		final String result = executor.executeSNMPGet("1.3.6.1.99.99.99", SNMP_CONFIG, HOSTNAME, false, null);
		assertNull(result);
	}

	// ---- SNMP GET NEXT ----

	@Test
	void testExecuteSNMPGetNext(@TempDir Path tempDir) throws Exception {
		writeWalkFile(tempDir, "device.walk", WALK_CONTENT);
		final EmulationSnmpRequestExecutor executor = new EmulationSnmpRequestExecutor(tempDir.toString());

		final String result = executor.executeSNMPGetNext("1.3.6.1.2.1.1.1.0", SNMP_CONFIG, HOSTNAME, false, null);
		assertNotNull(result);
		// GetNext after 1.3.6.1.2.1.1.1.0 should return 1.3.6.1.2.1.1.3.0
		assertTrue(result.contains("1.3.6.1.2.1.1.3.0"));
		assertTrue(result.contains("123456"));
	}

	// ---- SNMP TABLE ----

	@Test
	void testExecuteSNMPTable(@TempDir Path tempDir) throws Exception {
		writeWalkFile(tempDir, "device.walk", WALK_CONTENT);
		final EmulationSnmpRequestExecutor executor = new EmulationSnmpRequestExecutor(tempDir.toString());

		// Table under 1.3.6.1.2.1.2.2.1 (ifEntry) with columns 1 (ifIndex) and 2 (ifDescr)
		final List<List<String>> result = executor.executeSNMPTable(
			"1.3.6.1.2.1.2.2.1",
			new String[] { "1", "2" },
			SNMP_CONFIG,
			HOSTNAME,
			false,
			null
		);

		assertNotNull(result);
		assertEquals(2, result.size());
		// Row 1: ifIndex=1, ifDescr=eth0
		assertEquals("1", result.get(0).get(0));
		assertEquals("eth0", result.get(0).get(1));
		// Row 2: ifIndex=2, ifDescr=lo
		assertEquals("2", result.get(1).get(0));
		assertEquals("lo", result.get(1).get(1));
	}

	// ---- SNMP WALK ----

	@Test
	void testExecuteSNMPWalk(@TempDir Path tempDir) throws Exception {
		writeWalkFile(tempDir, "device.walk", WALK_CONTENT);
		final EmulationSnmpRequestExecutor executor = new EmulationSnmpRequestExecutor(tempDir.toString());

		final String result = executor.executeSNMPWalk("1.3.6.1.2.1.1", SNMP_CONFIG, HOSTNAME, false, null);
		assertNotNull(result);
		assertTrue(result.contains("1.3.6.1.2.1.1.1.0"));
		assertTrue(result.contains("Linux server 5.4.0"));
		assertTrue(result.contains("1.3.6.1.2.1.1.5.0"));
		assertTrue(result.contains("test-host"));
	}

	// ---- Missing SNMP directory ----

	@Test
	void testCreateSnmpClientMissingDirectory(@TempDir Path tempDir) {
		final EmulationSnmpRequestExecutor executor = new EmulationSnmpRequestExecutor(
			tempDir.resolve("missing").toString()
		);

		// Configured directory does not exist -> should throw
		assertThrows(
			Exception.class,
			() -> executor.executeSNMPGet("1.3.6.1.2.1.1.1.0", SNMP_CONFIG, HOSTNAME, false, null)
		);
	}

	@Test
	void testCreateSnmpClientNullDirectory() {
		final EmulationSnmpRequestExecutor executor = new EmulationSnmpRequestExecutor(null);

		assertThrows(
			Exception.class,
			() -> executor.executeSNMPGet("1.3.6.1.2.1.1.1.0", SNMP_CONFIG, HOSTNAME, false, null)
		);
	}

	// ---- Multiple walk files ----

	@Test
	void testMultipleWalkFiles(@TempDir Path tempDir) throws Exception {
		writeWalkFile(tempDir, "system.walk", "1.3.6.1.2.1.1.1.0\tOctetString\tLinux server 5.4.0\n");
		writeWalkFile(
			tempDir,
			"interfaces.walk",
			"1.3.6.1.2.1.2.2.1.1.1\tInteger\t1\n" + "1.3.6.1.2.1.2.2.1.2.1\tOctetString\teth0\n"
		);
		final EmulationSnmpRequestExecutor executor = new EmulationSnmpRequestExecutor(tempDir.toString());

		// Both walk files should be loaded
		assertEquals(
			"Linux server 5.4.0",
			executor.executeSNMPGet("1.3.6.1.2.1.1.1.0", SNMP_CONFIG, HOSTNAME, false, null)
		);
		assertEquals("1", executor.executeSNMPGet("1.3.6.1.2.1.2.2.1.1.1", SNMP_CONFIG, HOSTNAME, false, null));
	}

	// ---- Empty walk file ----

	@Test
	void testEmptyWalkFile(@TempDir Path tempDir) throws Exception {
		writeWalkFile(tempDir, "empty.walk", "");
		final EmulationSnmpRequestExecutor executor = new EmulationSnmpRequestExecutor(tempDir.toString());

		// No OIDs loaded, GET should fail/return null
		final String result = executor.executeSNMPGet("1.3.6.1.2.1.1.1.0", SNMP_CONFIG, HOSTNAME, false, null);
		assertNull(result);
	}
}
