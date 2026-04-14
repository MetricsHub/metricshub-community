package org.metricshub.extension.emulation.oscommand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.emulation.OsCommandEmulationConfig;
import org.metricshub.extension.oscommand.OsCommandConfiguration;

/**
 * Tests for {@link EmulationOsCommandService}.
 */
class EmulationOsCommandServiceTest {

	private static final String HOSTNAME = "test-host";

	private final EmulationOsCommandService service = new EmulationOsCommandService(new EmulationRoundRobinManager());

	private TelemetryManager buildTelemetryManager(final String emulationInputDir) {
		return TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOSTNAME)
					.hostId(HOSTNAME)
					.hostType(DeviceKind.LINUX)
					.configurations(
						Map.of(
							EmulationConfiguration.class,
							EmulationConfiguration
								.builder()
								.hostname(HOSTNAME)
								.oscommand(new OsCommandEmulationConfig(new OsCommandConfiguration(), emulationInputDir))
								.build()
						)
					)
					.build()
			)
			.build();
	}

	@Test
	void testRunOsCommandMatch(@TempDir Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - command: \"echo test\"
			    result: \"r1.txt\"
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(tempDir.resolve("r1.txt"), "ok", StandardCharsets.UTF_8);

		final OsCommandResult result = service.runOsCommand(
			"echo test",
			buildTelemetryManager(tempDir.toString()),
			null,
			true,
			true,
			Map.of()
		);

		assertEquals("ok", result.getResult());
		assertEquals("echo test", result.getNoPasswordCommand());
	}

	@Test
	void testRunOsCommandNoMatchReturnsEmpty(@TempDir Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - command: \"echo other\"
			    result: \"r1.txt\"
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(tempDir.resolve("r1.txt"), "ok", StandardCharsets.UTF_8);

		final OsCommandResult result = service.runOsCommand(
			"echo test",
			buildTelemetryManager(tempDir.toString()),
			null,
			true,
			true,
			Map.of()
		);

		assertEquals("", result.getResult());
	}

	@Test
	void testRunOsCommandRoundRobin(@TempDir Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - command: \"echo test\"
			    result: \"r1.txt\"
			  - command: \"echo test\"
			    result: \"r2.txt\"
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(tempDir.resolve("r1.txt"), "first", StandardCharsets.UTF_8);
		Files.writeString(tempDir.resolve("r2.txt"), "second", StandardCharsets.UTF_8);

		final TelemetryManager telemetryManager = buildTelemetryManager(tempDir.toString());

		final OsCommandResult result1 = service.runOsCommand("echo test", telemetryManager, null, true, true, Map.of());
		final OsCommandResult result2 = service.runOsCommand("echo test", telemetryManager, null, true, true, Map.of());
		final OsCommandResult result3 = service.runOsCommand("echo test", telemetryManager, null, true, true, Map.of());

		assertEquals("first", result1.getResult());
		assertEquals("second", result2.getResult());
		assertEquals("first", result3.getResult());
	}

	@Test
	void testRunOsCommandMissingIndex(@TempDir Path tempDir) throws Exception {
		final OsCommandResult result = service.runOsCommand(
			"echo test",
			buildTelemetryManager(tempDir.toString()),
			null,
			true,
			true,
			Map.of()
		);

		assertEquals("", result.getResult());
	}

	@Test
	void testRunOsCommandMalformedYaml(@TempDir Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), "this: is: [bad", StandardCharsets.UTF_8);

		final OsCommandResult result = service.runOsCommand(
			"echo test",
			buildTelemetryManager(tempDir.toString()),
			null,
			true,
			true,
			Map.of()
		);

		assertEquals("", result.getResult());
	}
}
