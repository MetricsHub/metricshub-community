package org.metricshub.engine.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.CONNECTOR;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.configuration.AdditionalConnector;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.parser.ConnectorParser;
import org.metricshub.engine.connector.parser.ConnectorStoreComposer;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.collect.PrepareCollectStrategy;
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.strategy.simple.SimpleStrategy;
import org.metricshub.engine.strategy.source.SourceResult;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mapping values that start with "=" (#1684), run by the strategies on a connector composed as the agent does: AWK
 * expressions on the row, the constants, the connector variables and the text sources of the job as AWK variables.
 */
@ExtendWith(MockitoExtension.class)
class MappingExpressionsTest {

	private static final String CONNECTOR_ID = "TestConnectorWithExpressions";
	private static final long STRATEGY_TIME = new Date().getTime();

	@Mock
	private IProtocolExtension protocolExtensionMock;

	@Mock
	private ClientsExecutor clientsExecutorMock;

	/**
	 * The texts the mocked command lines return. Any other command line fails.
	 */
	private final Map<String, String> texts = new HashMap<>(
		Map.of("model", "X1\n", "vendor", "Acme\n", "reading s1", "11\n", "reading s2", "22\n")
	);

	/**
	 * The tables the mocked SNMP tables return, by OID, as CSV.
	 */
	private final Map<String, String> tables = new HashMap<>(
		Map.of(
			"1.3.6.1.1",
			"d1;10;ok\nd2;20;failed",
			"1.3.6.1.2",
			"d1;1000;2000",
			"1.3.6.1.3",
			"c1;3200",
			"1.3.6.1.5",
			"s1\ns2",
			"1.3.6.1.6",
			"x",
			"1.3.6.1.7",
			"1\nN/A",
			"1.3.6.1.8",
			"1;5\nN/A;7"
		)
	);

	private TelemetryManager telemetryManager;
	private ExtensionManager extensionManager;

	@BeforeEach
	void setUp() {
		final TestConfiguration configuration = TestConfiguration.builder().build();
		lenient().doReturn(true).when(protocolExtensionMock).isValidConfiguration(configuration);
		lenient().doReturn(Set.of(CommandLineSource.class, SnmpTableSource.class))
			.when(protocolExtensionMock)
			.getSupportedSources();
		lenient().doAnswer(invocation -> {
			if (invocation.getArgument(0) instanceof CommandLineSource commandLine) {
				final String text = texts.get(commandLine.getCommandLine());
				return text == null ? SourceResult.failed("No such command") : SourceResult.ofText(text);
			}
			final SnmpTableSource snmpTable = invocation.getArgument(0);
			return SourceResult.ofTable(SourceResult.csvToTable(tables.get(snmpTable.getOid()), ";"));
		}).when(protocolExtensionMock).processSource(any(), anyString(), any(TelemetryManager.class));
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(protocolExtensionMock)).build();

		// The store as the agent builds it: the connector declares a variable, so it is composed for the resource,
		// which configures scale: 3
		final ConnectorStoreComposer composer = ConnectorStoreComposer.builder()
			.withRawConnectorStore(
				new RawConnectorStore(Paths.get("src", "test", "resources", "test-files", "strategy", "mappingExpressions"))
			)
			.withUpdateChain(ConnectorParser.createUpdateChain())
			.withDeserializer(new ConnectorDeserializer(JsonHelper.buildYamlMapper()))
			.withAdditionalConnectors(
				Map.of(CONNECTOR_ID, AdditionalConnector.builder().uses(CONNECTOR_ID).variables(Map.of("scale", "3")).build())
			)
			.build();
		final ConnectorStore connectorStore = composer.generateStaticConnectorStore();
		connectorStore.addMany(composer.resolveConnectorStoreVariables(connectorStore).getCustomConnectorsMap());
		assertNotNull(connectorStore.getStore().get(CONNECTOR_ID));

		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		final Monitor connectorMonitor = Monitor.builder().type(CONNECTOR.getKey()).build();
		connectorMonitor.getAttributes().put(MONITOR_ATTRIBUTE_ID, CONNECTOR_ID);
		telemetryManager = TelemetryManager.builder()
			.monitors(
				new HashMap<>(
					Map.of(
						HOST.getKey(),
						Map.of("monitor1", hostMonitor),
						CONNECTOR.getKey(),
						new HashMap<>(
							Map.of(
								String.format(AbstractStrategy.CONNECTOR_ID_FORMAT, CONNECTOR.getKey(), CONNECTOR_ID),
								connectorMonitor
							)
						)
					)
				)
			)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId("host-01")
					.hostname("ec-02")
					.hostType(DeviceKind.LINUX)
					.sequential(true)
					.configurations(Map.of(TestConfiguration.class, configuration))
					.build()
			)
			.connectorStore(connectorStore)
			.build();
	}

	private Map<String, Monitor> monitors(final String type) {
		return telemetryManager.getMonitors().get(type);
	}

	/**
	 * The monitor of the given type whose id attribute is the given one.
	 */
	private Monitor monitor(final String type, final String id) {
		return monitors(type).values()
			.stream()
			.filter(monitor -> id.equals(monitor.getAttribute(MONITOR_ATTRIBUTE_ID)))
			.findFirst()
			.orElse(null);
	}

	private Set<String> ids(final String type) {
		return monitors(type).values()
			.stream()
			.map(monitor -> monitor.getAttribute(MONITOR_ATTRIBUTE_ID))
			.collect(Collectors.toSet());
	}

	private static Double metric(final Monitor monitor, final String name) {
		final NumberMetric metric = monitor.getMetric(name, NumberMetric.class);
		return metric == null ? null : metric.getValue();
	}

	private void discovery(final long time) {
		new DiscoveryStrategy(telemetryManager, time, clientsExecutorMock, extensionManager).run();
	}

	private void collect(final long time) {
		new PrepareCollectStrategy(telemetryManager, time, clientsExecutorMock, extensionManager).run();
		new CollectStrategy(telemetryManager, time, clientsExecutorMock, extensionManager).run();
	}

	@Test
	void discoveryEvaluatesExpressionsBesideLegacyValues() {
		discovery(STRATEGY_TIME);

		assertEquals(Set.of("d1", "d2"), ids("disk"));
		final Monitor d1 = monitor("disk", "d1");
		assertEquals("d1 (X1)", d1.getAttribute("name"));
		assertEquals("d1", d1.getAttribute("legacyId"));
		assertEquals("=x", d1.getAttribute("eq"));
		// blocks x BLOCK_SIZE x scale, computed by the awk compute with the configured scale
		assertEquals(10.0 * 512 * 3, metric(d1, "disk.size"));

		// The status is not ok: the metric is deactivated
		final Monitor d2 = monitor("disk", "d2");
		assertTrue(d2.isMetricDeactivated("disk.size"));
		assertNull(metric(d2, "disk.size"));
	}

	@Test
	void collectEvaluatesStateFunctions() {
		discovery(STRATEGY_TIME);
		final Monitor d1 = monitor("disk", "d1");

		// The first collect has no previous value: no rate, no counter, never 0
		collect(STRATEGY_TIME);
		assertNull(metric(d1, "disk.io"));
		assertNull(metric(d1, "disk.energy"));
		assertEquals(1000.0, metric(d1, "__disk.io.rate_from"));

		// 120 s later: 1000 bytes more, 2 W over 120 s
		tables.put("1.3.6.1.2", "d1;2000;2000");
		collect(STRATEGY_TIME + 120_000);
		assertEquals(1000.0 / 120 * 8, metric(d1, "disk.io"), 1e-9);
		assertEquals(2000.0, metric(d1, "__disk.io.rate_from"));
		assertEquals(240.0, metric(d1, "disk.energy"));

		// The counter adds up
		collect(STRATEGY_TIME + 240_000);
		assertEquals(480.0, metric(d1, "disk.energy"));
	}

	@Test
	void simpleJobReadsTextSources() {
		new SimpleStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		final Monitor c1 = monitor("cpu", "c1");
		assertEquals("Acme c1", c1.getAttribute("info"));
		assertEquals(3_200_000_000.0, metric(c1, "cpu.speed"));
	}

	@Test
	void monoInstanceCollectReadsTheTextOfEachMonitor() {
		discovery(STRATEGY_TIME);
		assertEquals(Set.of("s1", "s2"), ids("sensor"));

		collect(STRATEGY_TIME);

		assertEquals(11.0, metric(monitor("sensor", "s1"), "sensor.reading"));
		assertEquals(22.0, metric(monitor("sensor", "s2"), "sensor.reading"));
	}

	@Test
	void keyAttributeWithoutValueSkipsTheRow() {
		discovery(STRATEGY_TIME);

		// megaBit2Bit("N/A") has no value: only the first row creates a monitor
		assertEquals(Set.of("1000000"), ids("fan"));

		collect(STRATEGY_TIME);

		final Monitor fan = monitor("fan", "1000000");
		assertEquals(5.0, metric(fan, "fan.speed"));
		assertEquals(1, monitors("fan").size());
	}
}
