package org.metricshub.engine.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
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
 * The simple mapping, a mapping without a source: exactly one monitor instance, whose values are constants and
 * references to text sources, in the simple, discovery and collect jobs.
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
	 * The texts the mocked command lines return. Any other command line fails: it returns an empty table.
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
				return text == null ? SourceTable.empty() : SourceTable.ofText(text);
			}
			return SourceTable.ofTable(SourceTable.csvToTable("row-1;10\nrow-2;20", MetricsHubConstants.TABLE_SEP));
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

		assertEquals(Set.of(CONNECTOR_ID + "_settings_settings"), monitors("settings").keySet());
		final Monitor settings = monitors("settings").values().iterator().next();
		assertEquals("Acme", settings.getAttribute("vendor"));
		assertEquals("Settings of Acme", settings.getAttribute("name"));
		assertEquals(1.0, metric(settings, "test.count"));
		assertEquals(42.0, metric(settings, "test.limit"));

		// A text source that failed gives an empty value, and no metric
		assertEquals("", settings.getAttribute("location"));
		assertNull(metric(settings, "test.failed"));

		// The instance exists even when its identifying attribute is a text that failed
		assertEquals(1, monitors("unidentified").size());

		// The next run updates the same instance
		texts.put("limit", "43");
		new PrepareCollectStrategy(telemetryManager, STRATEGY_TIME + 60_000, clientsExecutorMock, extensionManager).run();
		new SimpleStrategy(telemetryManager, STRATEGY_TIME + 60_000, clientsExecutorMock, extensionManager).run();

		assertEquals(1, monitors("settings").size());
		assertSame(settings, monitors("settings").values().iterator().next());
		assertEquals(43.0, metric(settings, "test.limit"));
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

		assertEquals(Set.of(CONNECTOR_ID + "_mono_mono"), monitors("mono").keySet());
		assertEquals(Set.of(CONNECTOR_ID + "_multi_multi"), monitors("multi").keySet());
		final Monitor mono = monitors("mono").values().iterator().next();
		final Monitor multi = monitors("multi").values().iterator().next();
		assertEquals("Acme", mono.getAttribute("vendor"));

		new CollectStrategy(telemetryManager, STRATEGY_TIME, clientsExecutorMock, extensionManager).run();

		assertEquals(42.0, metric(mono, "test.limit"));
		assertEquals(42.0, metric(multi, "test.limit"));
		assertEquals(1, monitors("mono").size());
		assertEquals(1, monitors("multi").size());
	}
}
