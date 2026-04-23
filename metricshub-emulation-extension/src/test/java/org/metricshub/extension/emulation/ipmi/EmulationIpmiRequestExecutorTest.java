package org.metricshub.extension.emulation.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.emulation.IpmiEmulationConfig;
import org.metricshub.extension.ipmi.IpmiConfiguration;

/**
 * Tests for {@link EmulationIpmiRequestExecutor}.
 */
class EmulationIpmiRequestExecutorTest {

	private static final String HOSTNAME = "test-host";

	private final EmulationIpmiRequestExecutor executor = new EmulationIpmiRequestExecutor(
		new EmulationRoundRobinManager(),
		new EmulationImageCacheManager()
	);

	private EmulationConfiguration buildEmulationConfiguration(final String emulationInputDir) {
		return EmulationConfiguration
			.builder()
			.hostname(HOSTNAME)
			.ipmi(new IpmiEmulationConfig(IpmiConfiguration.builder().hostname(HOSTNAME).build(), emulationInputDir))
			.build();
	}

	@Test
	void testExecuteIpmiDetectionMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request: IpmiDetection
			    response: r1.txt
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(tempDir.resolve("r1.txt"), "System power state is up.", StandardCharsets.UTF_8);

		final String result = executor.executeIpmiDetection(
			HOSTNAME,
			IpmiConfiguration.builder().hostname(HOSTNAME).build(),
			buildEmulationConfiguration(tempDir.toString())
		);

		assertEquals("System power state is up.", result);
	}

	@Test
	void testExecuteIpmiGetSensorsMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request: GetSensors
			    response: sensors.txt
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(tempDir.resolve("sensors.txt"), "FRU data here", StandardCharsets.UTF_8);

		final String result = executor.executeIpmiGetSensors(
			HOSTNAME,
			IpmiConfiguration.builder().hostname(HOSTNAME).build(),
			buildEmulationConfiguration(tempDir.toString())
		);

		assertEquals("FRU data here", result);
	}

	@Test
	void testRoundRobin(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request: GetSensors
			    response: r1.txt
			  - request: GetSensors
			    response: r2.txt
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(tempDir.resolve("r1.txt"), "first", StandardCharsets.UTF_8);
		Files.writeString(tempDir.resolve("r2.txt"), "second", StandardCharsets.UTF_8);

		final EmulationConfiguration emulCfg = buildEmulationConfiguration(tempDir.toString());
		final IpmiConfiguration ipmiCfg = IpmiConfiguration.builder().hostname(HOSTNAME).build();

		final String result1 = executor.executeIpmiGetSensors(HOSTNAME, ipmiCfg, emulCfg);
		final String result2 = executor.executeIpmiGetSensors(HOSTNAME, ipmiCfg, emulCfg);
		final String result3 = executor.executeIpmiGetSensors(HOSTNAME, ipmiCfg, emulCfg);

		assertEquals("first", result1);
		assertEquals("second", result2);
		assertEquals("first", result3);
	}

	@Test
	void testNoMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request: GetSensors
			    response: r1.txt
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(tempDir.resolve("r1.txt"), "data", StandardCharsets.UTF_8);

		final String result = executor.executeIpmiDetection(
			HOSTNAME,
			IpmiConfiguration.builder().hostname(HOSTNAME).build(),
			buildEmulationConfiguration(tempDir.toString())
		);

		assertNull(result);
	}

	@Test
	void testReturnsNullWhenEmulationConfigIsNull() {
		final String result = executor.executeIpmiDetection(
			HOSTNAME,
			IpmiConfiguration.builder().hostname(HOSTNAME).build(),
			null
		);

		assertNull(result);
	}

	@Test
	void testReturnsNullWhenIpmiConfigIsNull() {
		final EmulationConfiguration emulCfg = EmulationConfiguration.builder().hostname(HOSTNAME).build();

		final String result = executor.executeIpmiGetSensors(
			HOSTNAME,
			IpmiConfiguration.builder().hostname(HOSTNAME).build(),
			emulCfg
		);

		assertNull(result);
	}

	@Test
	void testReturnsNullWhenDirectoryIsNull() {
		final EmulationConfiguration emulCfg = EmulationConfiguration
			.builder()
			.hostname(HOSTNAME)
			.ipmi(new IpmiEmulationConfig(IpmiConfiguration.builder().hostname(HOSTNAME).build(), null))
			.build();

		final String result = executor.executeIpmiGetSensors(
			HOSTNAME,
			IpmiConfiguration.builder().hostname(HOSTNAME).build(),
			emulCfg
		);

		assertNull(result);
	}

	@Test
	void testReturnsNullWhenIndexIsMissing(@TempDir final Path tempDir) {
		final String result = executor.executeIpmiGetSensors(
			HOSTNAME,
			IpmiConfiguration.builder().hostname(HOSTNAME).build(),
			buildEmulationConfiguration(tempDir.toString())
		);

		assertNull(result);
	}

	@Test
	void testReturnsNullWhenIndexIsInvalid(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), "not: [valid", StandardCharsets.UTF_8);

		final String result = executor.executeIpmiGetSensors(
			HOSTNAME,
			IpmiConfiguration.builder().hostname(HOSTNAME).build(),
			buildEmulationConfiguration(tempDir.toString())
		);

		assertNull(result);
	}

	@Test
	void testReturnsNullWhenResponseIsBlank(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request: GetSensors
			    response: " "
			""",
			StandardCharsets.UTF_8
		);

		final String result = executor.executeIpmiGetSensors(
			HOSTNAME,
			IpmiConfiguration.builder().hostname(HOSTNAME).build(),
			buildEmulationConfiguration(tempDir.toString())
		);

		assertNull(result);
	}

	@Test
	void testReturnsNullWhenResponseFileIsMissing(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request: GetSensors
			    response: missing.txt
			""",
			StandardCharsets.UTF_8
		);

		final String result = executor.executeIpmiGetSensors(
			HOSTNAME,
			IpmiConfiguration.builder().hostname(HOSTNAME).build(),
			buildEmulationConfiguration(tempDir.toString())
		);

		assertNull(result);
	}

	@Test
	void testFindMatchingEntriesIgnoresNullEntries() {
		final IpmiEmulationEntry matchingEntry = new IpmiEmulationEntry("GetSensors", "r1.txt");
		final IpmiEmulationEntry nullRequestEntry = new IpmiEmulationEntry();

		final List<IpmiEmulationEntry> entries = new ArrayList<>();
		entries.add(null);
		entries.add(nullRequestEntry);
		entries.add(matchingEntry);

		final List<IpmiEmulationEntry> result = executor.findMatchingEntries(entries, "GetSensors");

		assertEquals(1, result.size());
		assertEquals("r1.txt", result.get(0).getResponse());
	}

	@Test
	void testFindMatchingEntriesReturnsEmptyForNullEntries() {
		assertTrue(executor.findMatchingEntries(null, "GetSensors").isEmpty());
	}

	@Test
	void testFindMatchingEntriesReturnsEmptyForEmptyList() {
		assertTrue(executor.findMatchingEntries(List.of(), "GetSensors").isEmpty());
	}
}
