package org.metricshub.extension.emulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.HttpConfiguration;

/**
 * Tests for {@link EmulationExtension}.
 */
class EmulationExtensionTest {

	private static final String HOSTNAME = "test-host";

	private final EmulationExtension emulationExtension = new EmulationExtension();

	private TelemetryManager buildTelemetryManager(final Map<Class<? extends IConfiguration>, IConfiguration> configs) {
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(Map.of("connector", Connector.builder().build()));
		return TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOSTNAME)
					.hostId(HOSTNAME)
					.hostType(DeviceKind.LINUX)
					.configurations(configs)
					.build()
			)
			.connectorStore(connectorStore)
			.hostProperties(HostProperties.builder().isLocalhost(true).build())
			.build();
	}

	// ---- isValidConfiguration ----

	@Test
	void testIsValidConfigurationWithEmulationConfig() {
		assertTrue(emulationExtension.isValidConfiguration(EmulationConfiguration.builder().build()));
	}

	@Test
	void testIsValidConfigurationWithOtherConfig() {
		assertFalse(emulationExtension.isValidConfiguration(HttpConfiguration.builder().build()));
	}

	@Test
	void testIsValidConfigurationWithAnonymousConfig() {
		assertFalse(
			emulationExtension.isValidConfiguration(
				new IConfiguration() {
					@Override
					public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {}

					@Override
					public String getHostname() {
						return null;
					}

					@Override
					public void setHostname(String hostname) {}

					@Override
					public IConfiguration copy() {
						return null;
					}

					@Override
					public void setTimeout(Long timeout) {}

					@Override
					public String getProperty(String property) {
						return null;
					}

					@Override
					public boolean isCorrespondingProtocol(String protocol) {
						return false;
					}
				}
			)
		);
	}

	// ---- getSupportedSources ----

	@Test
	void testGetSupportedSources() {
		assertFalse(emulationExtension.getSupportedSources().isEmpty());
		assertTrue(emulationExtension.getSupportedSources().contains(HttpSource.class));
		assertTrue(emulationExtension.getSupportedSources().contains(CommandLineSource.class));
		assertTrue(emulationExtension.getSupportedSources().contains(SnmpGetSource.class));
		assertTrue(emulationExtension.getSupportedSources().contains(SnmpTableSource.class));
	}

	// ---- getSupportedCriteria ----

	@Test
	void testGetSupportedCriteria() {
		assertFalse(emulationExtension.getSupportedCriteria().isEmpty());
		assertTrue(emulationExtension.getSupportedCriteria().contains(HttpCriterion.class));
		assertTrue(emulationExtension.getSupportedCriteria().contains(CommandLineCriterion.class));
		assertTrue(emulationExtension.getSupportedCriteria().contains(SnmpGetCriterion.class));
		assertTrue(emulationExtension.getSupportedCriteria().contains(SnmpGetNextCriterion.class));
	}

	// ---- getConfigurationToSourceMapping ----

	@Test
	void testGetConfigurationToSourceMapping() {
		final var mapping = emulationExtension.getConfigurationToSourceMapping();
		assertFalse(mapping.isEmpty());
		assertTrue(mapping.containsKey(EmulationConfiguration.class));
		assertTrue(mapping.get(EmulationConfiguration.class).contains(HttpSource.class));
		assertTrue(mapping.get(EmulationConfiguration.class).contains(CommandLineSource.class));
	}

	// ---- checkProtocol ----

	@Test
	void testCheckProtocolWithEmulationConfigured() {
		final TelemetryManager tm = buildTelemetryManager(
			Map.of(EmulationConfiguration.class, EmulationConfiguration.builder().hostname(HOSTNAME).build())
		);

		final Optional<Boolean> result = emulationExtension.checkProtocol(tm);
		assertTrue(result.isPresent());
		assertTrue(result.get());
	}

	@Test
	void testCheckProtocolWithoutEmulationConfiguration() {
		final TelemetryManager tm = buildTelemetryManager(
			Map.of(HttpConfiguration.class, HttpConfiguration.builder().build())
		);

		final Optional<Boolean> result = emulationExtension.checkProtocol(tm);
		assertTrue(result.isEmpty());
	}

	@Test
	void testCheckProtocolEmptyConfigurations() {
		final TelemetryManager tm = buildTelemetryManager(new HashMap<>());

		final Optional<Boolean> result = emulationExtension.checkProtocol(tm);
		assertTrue(result.isEmpty());
	}

	// ---- processSource ----

	@Test
	void testProcessSourceWithSnmpGetSourceNoEmulationDir() {
		final TelemetryManager tm = buildTelemetryManager(
			Map.of(EmulationConfiguration.class, EmulationConfiguration.builder().build())
		);

		final SnmpGetSource snmpSource = SnmpGetSource.builder().oid("1.3.6.1").build();
		// No emulation input directory → returns empty table
		final SourceTable result = emulationExtension.processSource(snmpSource, "connector", tm);

		assertNotNull(result);
		assertTrue(result.getTable().isEmpty());
	}

	@Test
	void testProcessSourceWithSnmpGetSource(@TempDir Path tempDir) throws IOException {
		final Path snmpDir = tempDir.resolve("snmp");
		Files.createDirectories(snmpDir);
		Files.writeString(
			snmpDir.resolve("device.walk"),
			"1.3.6.1.2.1.1.1.0\tOctetString\tLinux server 5.4.0\n",
			StandardCharsets.UTF_8
		);

		final TelemetryManager tm = buildTelemetryManager(
			Map.of(EmulationConfiguration.class, EmulationConfiguration.builder().hostname(HOSTNAME).build())
		);
		tm.setEmulationInputDirectory(tempDir.toString());

		final SnmpGetSource snmpSource = SnmpGetSource.builder().oid("1.3.6.1.2.1.1.1.0").build();
		final SourceTable result = emulationExtension.processSource(snmpSource, "connector", tm);

		assertNotNull(result);
		assertFalse(result.getTable().isEmpty());
	}

	@Test
	void testProcessSourceWithHttpSourceNoEmulationDir() {
		final TelemetryManager tm = buildTelemetryManager(
			Map.of(EmulationConfiguration.class, EmulationConfiguration.builder().build())
		);

		final HttpSource httpSource = HttpSource
			.builder()
			.url("/api/test")
			.method(HttpMethod.GET)
			.resultContent(ResultContent.BODY)
			.build();
		// No emulation input directory → returns empty table (no match in executor)
		final SourceTable result = emulationExtension.processSource(httpSource, "connector", tm);

		assertNotNull(result);
	}

	@Test
	void testProcessSourceWithCommandLineSource(@TempDir Path tempDir) throws IOException {
		final Path commandDir = tempDir.resolve("command");
		Files.createDirectories(commandDir);
		Files.writeString(
			commandDir.resolve("image.yaml"),
			"""
			image:
			  - command: "echo test"
			    result: "r1.txt"
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(commandDir.resolve("r1.txt"), "ok", StandardCharsets.UTF_8);

		final TelemetryManager tm = buildTelemetryManager(
			Map.of(EmulationConfiguration.class, EmulationConfiguration.builder().hostname(HOSTNAME).build())
		);
		tm.setEmulationInputDirectory(tempDir.toString());

		final CommandLineSource source = CommandLineSource.builder().commandLine("echo test").build();
		final SourceTable result = emulationExtension.processSource(source, "connector", tm);

		assertNotNull(result);
		assertFalse(result.getTable().isEmpty());
		assertEquals("ok", result.getTable().get(0).get(0));
	}

	// ---- processCriterion ----

	@Test
	void testProcessCriterionWithNonHttpCriterion() {
		final TelemetryManager tm = buildTelemetryManager(
			Map.of(EmulationConfiguration.class, EmulationConfiguration.builder().build())
		);

		// null criterion triggers the empty path
		final CriterionTestResult result = emulationExtension.processCriterion(null, "connector", tm, false);
		assertEquals(CriterionTestResult.empty(), result);
	}

	@Test
	void testProcessCriterionSnmpGetCriterion(@TempDir Path tempDir) throws IOException {
		final Path snmpDir = tempDir.resolve("snmp");
		Files.createDirectories(snmpDir);
		Files.writeString(
			snmpDir.resolve("device.walk"),
			"1.3.6.1.2.1.1.1.0\tOctetString\tLinux server 5.4.0\n",
			StandardCharsets.UTF_8
		);

		final TelemetryManager tm = buildTelemetryManager(
			Map.of(EmulationConfiguration.class, EmulationConfiguration.builder().hostname(HOSTNAME).build())
		);
		tm.setEmulationInputDirectory(tempDir.toString());

		final SnmpGetCriterion snmpGetCriterion = SnmpGetCriterion.builder().oid("1.3.6.1.2.1.1.1.0").build();
		final CriterionTestResult result = emulationExtension.processCriterion(snmpGetCriterion, "connector", tm, false);

		assertNotNull(result);
		assertTrue(result.isSuccess());
	}

	@Test
	void testProcessCriterionSnmpGetNextCriterion(@TempDir Path tempDir) throws IOException {
		final Path snmpDir = tempDir.resolve("snmp");
		Files.createDirectories(snmpDir);
		Files.writeString(
			snmpDir.resolve("device.walk"),
			"1.3.6.1.2.1.1.1.0\tOctetString\tLinux server 5.4.0\n" + "1.3.6.1.2.1.1.3.0\tTimeTicks\t123456\n",
			StandardCharsets.UTF_8
		);

		final TelemetryManager tm = buildTelemetryManager(
			Map.of(EmulationConfiguration.class, EmulationConfiguration.builder().hostname(HOSTNAME).build())
		);
		tm.setEmulationInputDirectory(tempDir.toString());

		final SnmpGetNextCriterion snmpGetNextCriterion = SnmpGetNextCriterion.builder().oid("1.3.6.1.2.1.1").build();
		final CriterionTestResult result = emulationExtension.processCriterion(
			snmpGetNextCriterion,
			"connector",
			tm,
			false
		);

		assertNotNull(result);
		assertTrue(result.isSuccess());
	}

	@Test
	void testProcessCriterionCommandLineCriterion(@TempDir Path tempDir) throws IOException {
		final Path commandDir = tempDir.resolve("command");
		Files.createDirectories(commandDir);
		Files.writeString(
			commandDir.resolve("image.yaml"),
			"""
			image:
			  - command: "echo test"
			    result: "r1.txt"
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(commandDir.resolve("r1.txt"), "status=OK", StandardCharsets.UTF_8);

		final TelemetryManager tm = buildTelemetryManager(
			Map.of(EmulationConfiguration.class, EmulationConfiguration.builder().hostname(HOSTNAME).build())
		);
		tm.setEmulationInputDirectory(tempDir.toString());

		final CommandLineCriterion criterion = CommandLineCriterion
			.builder()
			.commandLine("echo test")
			.expectedResult("status=OK")
			.build();

		final CriterionTestResult result = emulationExtension.processCriterion(criterion, "connector", tm, false);

		assertNotNull(result);
		assertTrue(result.isSuccess());
	}

	// ---- isSupportedConfigurationType ----

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(emulationExtension.isSupportedConfigurationType("emulation"));
		assertTrue(emulationExtension.isSupportedConfigurationType("EMULATION"));
		assertTrue(emulationExtension.isSupportedConfigurationType("Emulation"));
		assertFalse(emulationExtension.isSupportedConfigurationType("http"));
		assertFalse(emulationExtension.isSupportedConfigurationType("snmp"));
		assertFalse(emulationExtension.isSupportedConfigurationType(null));
	}

	// ---- buildConfiguration ----

	@Test
	void testBuildConfigurationSuccess() throws InvalidConfigurationException {
		final ObjectNode jsonNode = JsonNodeFactory.instance.objectNode();
		final IConfiguration config = emulationExtension.buildConfiguration("emulation", jsonNode, value -> value);

		assertNotNull(config);
		assertTrue(config instanceof EmulationConfiguration);
	}

	@Test
	void testBuildConfigurationNullDecrypt() throws InvalidConfigurationException {
		final ObjectNode jsonNode = JsonNodeFactory.instance.objectNode();
		final IConfiguration config = emulationExtension.buildConfiguration("emulation", jsonNode, null);

		assertNotNull(config);
		assertTrue(config instanceof EmulationConfiguration);
	}

	// ---- getIdentifier ----

	@Test
	void testGetIdentifier() {
		assertEquals("emulation", emulationExtension.getIdentifier());
	}

	// ---- executeQuery ----

	@Test
	void testExecuteQueryThrowsUnsupported() {
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		assertThrows(
			UnsupportedOperationException.class,
			() -> emulationExtension.executeQuery(EmulationConfiguration.builder().build(), queryNode)
		);
	}
}
