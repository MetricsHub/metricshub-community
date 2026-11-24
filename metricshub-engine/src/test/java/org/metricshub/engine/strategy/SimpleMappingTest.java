package org.metricshub.engine.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.CONNECTOR;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.collect.PrepareCollectStrategy;
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.strategy.simple.SimpleStrategy;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The simple mapping (<code>type: simple</code>): exactly one monitor instance of its type for the connector, without
 * key attribute, whose values are constants and references to text sources, in the simple, discovery and collect jobs.
 */
@ExtendWith(MockitoExtension.class)
class SimpleMappingTest {

	private static final String CONNECTOR_ID = "TestConnectorWithSimpleMapping";
	private static final long STRATEGY_TIME = new Date().getTime();

	@Mock
	private IProtocolExtension protocolExtensionMock;

	@Mock
	private ClientsExecutor clientsExecutorMock;

	/**
	 * The texts the mocked command lines return. Any other command line fails.
	 */
	private final Map<String, String> texts = new HashMap<>(Map.of("vendor", "Acme\n", "limit", "42"));

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
				return text == null ? SourceTable.failed("No such command") : SourceTable.ofText(text);
			}
			return SourceTable.ofTable(SourceTable.csvToTable("row-1;10\nrow-2;20", ";"));
		}).when(protocolExtensionMock).processSource(any(), anyString(), any(TelemetryManager.class));
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(protocolExtensionMock)).build();

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
			.connectorStore(
				new ConnectorStore(Paths.get("src", "test", "resources", "test-files", "strategy", "simpleMapping"))
			)
			.build();
	}

	private Map<String, Monitor> monitors(final String type) {
		return telemetryManager.getMonitors().get(type);
	}

	private static Double metric(final Monitor monitor, final String name) {
		final NumberMetric metric = monitor.getMetric(name, NumberMetric.class);
		return metric == null ? null : metric.getValue();
	}

	@Test
	void simpleJobCreatesExactlyOneInstance() {
		new SimpleStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		// The singleton is identified by the connector and the monitor type: no key attribute
		assertEquals(Set.of(CONNECTOR_ID + "_settings"), monitors("settings").keySet());
		final Monitor settings = monitors("settings").values().iterator().next();
		assertEquals(Set.of(), settings.getIdentifyingAttributeKeys());
		assertEquals("Acme", settings.getAttribute(MONITOR_ATTRIBUTE_ID));
		assertEquals("Acme", settings.getAttribute("vendor"));
		assertEquals("Settings of Acme", settings.getAttribute("name"));
		assertEquals(1.0, metric(settings, "test.count"));
		assertEquals(42.0, metric(settings, "test.limit"));

		// A text source that failed gives an empty value, and no metric
		assertEquals("", settings.getAttribute("location"));
		assertNull(metric(settings, "test.failed"));

		// The next run updates the same instance, although its id attribute changed: it is a plain attribute
		texts.put("limit", "43");
		texts.put("vendor", "Globex");
		new PrepareCollectStrategy(telemetryManager, STRATEGY_TIME + 60_000, clientsExecutorMock, extensionManager).run();
		new SimpleStrategy(telemetryManager, STRATEGY_TIME + 60_000, clientsExecutorMock, extensionManager).run();

		assertEquals(Set.of(CONNECTOR_ID + "_settings"), monitors("settings").keySet());
		assertSame(settings, monitors("settings").values().iterator().next());
		assertEquals("Globex", settings.getAttribute(MONITOR_ATTRIBUTE_ID));
		assertEquals(43.0, metric(settings, "test.limit"));
	}

	@Test
	void simpleMappingWithoutAttributeCreatesOneInstance() {
		new SimpleStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		assertEquals(Set.of(CONNECTOR_ID + "_bare"), monitors("bare").keySet());
		final Monitor bare = monitors("bare").values().iterator().next();
		assertNull(bare.getAttribute(MONITOR_ATTRIBUTE_ID));
		assertEquals(1.0, metric(bare, "test.count"));

		new PrepareCollectStrategy(telemetryManager, STRATEGY_TIME + 60_000, clientsExecutorMock, extensionManager).run();
		new SimpleStrategy(telemetryManager, STRATEGY_TIME + 60_000, clientsExecutorMock, extensionManager).run();

		assertSame(bare, monitors("bare").values().iterator().next());
		assertEquals(1, monitors("bare").size());
	}

	@Test
	void textThatAnsweredThenFailsGivesAnEmptyValueAndStillOneInstance() {
		new SimpleStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		final Set<String> unanswered = telemetryManager.getHostProperties()
			.getConnectorNamespace(CONNECTOR_ID)
			.getUnansweredSourceKeys();
		assertEquals(Set.of("${source::monitors.settings.simple.sources.failed}"), unanswered);
		final Monitor settings = monitors("settings").values().iterator().next();
		assertEquals("Acme", settings.getAttribute("vendor"));
		assertEquals(42.0, metric(settings, "test.limit"));

		// The texts that answered do not answer any more: each source is retried, then given up
		texts.clear();
		final long nextTime = STRATEGY_TIME + 60_000;
		new PrepareCollectStrategy(telemetryManager, nextTime, clientsExecutorMock, extensionManager).run();
		new SimpleStrategy(telemetryManager, nextTime, clientsExecutorMock, extensionManager).run();

		assertTrue(
			unanswered.containsAll(
				Set.of(
					"${source::monitors.settings.simple.sources.vendor}",
					"${source::monitors.settings.simple.sources.limit}"
				)
			),
			() -> "unanswered: " + unanswered
		);

		// Still the one instance: its attributes are empty, its metric is not collected at this run
		assertEquals(Set.of(CONNECTOR_ID + "_settings"), monitors("settings").keySet());
		assertEquals("", settings.getAttribute(MONITOR_ATTRIBUTE_ID));
		assertSame(settings, monitors("settings").values().iterator().next());
		assertEquals("", settings.getAttribute("vendor"));
		assertEquals("Settings of ", settings.getAttribute("name"));
		assertEquals(nextTime, settings.getMetric("test.count", NumberMetric.class).getCollectTime());
		assertEquals(STRATEGY_TIME, settings.getMetric("test.limit", NumberMetric.class).getCollectTime());
	}

	@Test
	void mappingWithASourceCreatesOneInstancePerRow() {
		new SimpleStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		assertEquals(Set.of(CONNECTOR_ID + "_rows_row-1", CONNECTOR_ID + "_rows_row-2"), monitors("rows").keySet());
		final Monitor row2 = monitors("rows").get(CONNECTOR_ID + "_rows_row-2");
		assertEquals("Acme", row2.getAttribute("vendor"));
		assertEquals(20.0, metric(row2, "test.limit"));
	}

	@Test
	void discoveryAndCollectJobsReadASimpleMapping() {
		new DiscoveryStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		assertEquals(Set.of(CONNECTOR_ID + "_mono"), monitors("mono").keySet());
		assertEquals(Set.of(CONNECTOR_ID + "_multi"), monitors("multi").keySet());
		final Monitor mono = monitors("mono").values().iterator().next();
		final Monitor multi = monitors("multi").values().iterator().next();
		assertEquals("Acme", mono.getAttribute("vendor"));
		assertEquals("multi", multi.getAttribute("hw.id"));

		new CollectStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		// The multiInstance collect finds the singleton without a key: the id its mapping writes is not read
		assertEquals(42.0, metric(mono, "test.limit"));
		assertEquals(42.0, metric(multi, "test.limit"));
		assertEquals(1, monitors("mono").size());
		assertEquals(1, monitors("multi").size());
		assertNull(multi.getAttribute(MONITOR_ATTRIBUTE_ID));
	}

	@Test
	void collectOfASimpleMappingReachesOnlyTheSingleton() {
		new DiscoveryStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		// Instances of an older table mapping of the same connector, kept by a removalDelay
		final Map<String, Monitor> stale = new HashMap<>();
		for (final String type : List.of("mono", "multi")) {
			final Monitor monitor = Monitor.builder().type(type).id(CONNECTOR_ID + "_" + type + "_old").build();
			monitor.getAttributes().put(MONITOR_ATTRIBUTE_ID, "old");
			monitor.getAttributes().put(MONITOR_ATTRIBUTE_CONNECTOR_ID, CONNECTOR_ID);
			monitors(type).put(monitor.getId(), monitor);
			stale.put(type, monitor);
		}

		new CollectStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		for (final String type : List.of("mono", "multi")) {
			assertEquals(42.0, metric(monitors(type).get(CONNECTOR_ID + "_" + type), "test.limit"));
			assertNull(metric(stale.get(type), "test.limit"));
		}
	}

	@Test
	void tableMappingWithEmptyKeysKeepsItsId() {
		new SimpleStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		assertEquals(Set.of(CONNECTOR_ID + "_emptyKeys_"), monitors("emptyKeys").keySet());
	}
}
