package org.metricshub.engine.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.engine.constants.Constants.HOST_ID;
import static org.metricshub.engine.constants.Constants.HOST_NAME;
import static org.metricshub.engine.constants.Constants.TEST_CONNECTOR_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.tool.ConnectorTool;
import org.metricshub.engine.connector.model.tool.ToolResult;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.ICompositeSourceScriptExtension;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.strategy.simple.SimpleStrategy;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceResult;
import org.metricshub.engine.strategy.tool.ToolStrategy;
import org.metricshub.engine.telemetry.MonitorFactory;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The text sources of a run: what {@link AbstractStrategy#processSourcesAndComputes} returns, and what the awk sources
 * and awk computes of the run see.
 */
class TextSourcesTest {

	private static final JobInfo JOB = JobInfo.builder()
		.hostname(HOST_NAME)
		.connectorId(TEST_CONNECTOR_ID)
		.jobName("simple")
		.monitorType("enclosure")
		.build();

	/**
	 * Whether the source <code>/flaky</code> fails.
	 */
	private final AtomicBoolean flakyFails = new AtomicBoolean();

	private static String key(final String name) {
		return "${source::monitors.enclosure.simple.sources." + name + "}";
	}

	private static HttpSource http(final String name, final String url) {
		return HttpSource.builder().url(url).key(key(name)).build();
	}

	/**
	 * An HTTP source answers its URL without its "/" followed by two line feeds, <code>/fail</code> fails,
	 * <code>/null</code> gives a text holding null; an SNMP table source answers one row.
	 */
	private SourceResult answer(final Source source) {
		if (!(source instanceof HttpSource http)) {
			return SourceResult.ofTable(List.of(List.of("t", "1")));
		}
		return switch (http.getUrl()) {
			case "/fail" -> SourceResult.failed("HTTP GET /fail: status 500");
			case "/null" -> SourceResult.ofText(null);
			case "/flaky" ->
				flakyFails.get() ? SourceResult.failed("HTTP GET /flaky: status 500") : SourceResult.ofText("first");
			default -> SourceResult.ofText(http.getUrl().substring(1) + "\n\n");
		};
	}

	private TelemetryManager newTelemetryManager() {
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId(HOST_ID)
					.hostname(HOST_NAME)
					.hostType(DeviceKind.LINUX)
					.sequential(true)
					.configurations(Map.of(TestConfiguration.class, TestConfiguration.builder().build()))
					.build()
			)
			.build();
	}

	private ExtensionManager newExtensionManager() {
		final IProtocolExtension extension = mock(IProtocolExtension.class);
		doReturn(true).when(extension).isValidConfiguration(any());
		doReturn(Set.of(HttpSource.class, SnmpTableSource.class)).when(extension).getSupportedSources();
		doAnswer(invocation -> answer(invocation.getArgument(0))).when(extension).processSource(any(), anyString(), any());

		// The awk source prints the text sources it sees
		final ICompositeSourceScriptExtension awkSource = mock(ICompositeSourceScriptExtension.class);
		doReturn(true).when(awkSource).isValidSource(any(JawkSource.class));
		doAnswer(invocation -> SourceResult.ofText(invocation.<SourceProcessor>getArgument(3).getTextSources().toString()))
			.when(awkSource)
			.processSource(any(), anyString(), any(), any());

		return ExtensionManager.builder()
			.withProtocolExtensions(List.of(extension))
			.withCompositeSourceScriptExtensions(List.of(awkSource))
			.build();
	}

	private SimpleStrategy newStrategy(final TelemetryManager telemetryManager) {
		return SimpleStrategy.builder()
			.strategyTime(1234L)
			.telemetryManager(telemetryManager)
			.clientsExecutor(new ClientsExecutor(telemetryManager))
			.extensionManager(newExtensionManager())
			.build();
	}

	private static Awk awk(final String script) {
		return Awk.builder().script(script).build();
	}

	private static String stored(final TelemetryManager telemetryManager, final String name) {
		return telemetryManager.getHostProperties()
			.getConnectorNamespace(TEST_CONNECTOR_ID)
			.getSourceResult(key(name))
			.getText();
	}

	@Test
	void testTextSourcesByNameInRunOrder() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final HttpSource failed = http("broken", "/fail");
		failed.setContinueOnFailure(true);
		final HttpSource failedCompute = http("badCompute", "/ok");
		failedCompute.setContinueOnFailure(true);
		failedCompute.setComputes(List.of(awk("BEGIN { exit 2 }")));

		final Map<String, String> textSources = newStrategy(telemetryManager).processSourcesAndComputes(
			List.of(
				http("model", "/X1"),
				SnmpTableSource.builder().oid("1.3").selectColumns("1,2").key(key("disks")).build(),
				http("source(1)", "/one"),
				http("log", "/two"),
				failed,
				failedCompute,
				http("empty", "/null"),
				JawkSource.builder().script("{ print }").key(key("seen")).build(),
				http("last", "/Z")
			),
			null,
			JOB,
			new ArrayList<>()
		);

		// No final line feed; a table, an unnameable source, a failed source and a failed compute are absent
		assertEquals(List.of("model", "empty", "seen", "last"), List.copyOf(textSources.keySet()));
		assertEquals("X1", textSources.get("model"));
		assertEquals("", textSources.get("empty"));
		assertEquals("Z", textSources.get("last"));
		// The awk source saw the texts that ran before it
		assertEquals("{model=X1, empty=}", textSources.get("seen"));
		assertThrows(UnsupportedOperationException.class, () -> textSources.put("x", "y"));
	}

	@Test
	void testComputeOfASourceSeesTheEarlierTextsOnly() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final HttpSource b = http("b", "/B");
		b.setComputes(List.of(awk("BEGIN { print model \"|\" b \"|\" length(b) }")));
		final HttpSource c = http("c", "/C");
		c.setComputes(List.of(awk("BEGIN { print b }")));

		final Map<String, String> textSources = newStrategy(telemetryManager).processSourcesAndComputes(
			List.of(http("model", "/X1"), b, c),
			null,
			JOB,
			new ArrayList<>()
		);

		// The compute of b reads the earlier text model and sees b unassigned; b is recorded after its compute
		assertEquals("X1||0\n", stored(telemetryManager, "b"));
		assertEquals("X1||0\n", stored(telemetryManager, "c"));
		assertEquals(Map.of("model", "X1", "b", "X1||0", "c", "X1||0"), textSources);
	}

	@Test
	void testEachCallHasItsOwnMap() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final SimpleStrategy strategy = newStrategy(telemetryManager);

		// A beforeAll source is a text source of the beforeAll call only
		final HttpSource session = HttpSource.builder().url("/token").key("${source::beforeAll.session}").build();
		assertEquals(
			Map.of("session", "token"),
			strategy.processSourcesAndComputes(List.of(session), null, JOB, new ArrayList<>())
		);

		final HttpSource job = http("job", "/J");
		job.setComputes(List.of(awk("BEGIN { print \"[\" session \"]\" }")));
		assertEquals(Map.of("job", "[]"), strategy.processSourcesAndComputes(List.of(job), null, JOB, new ArrayList<>()));
		// No source: an empty map
		assertEquals(Map.of(), strategy.processSourcesAndComputes(List.of(), null, JOB, new ArrayList<>()));
	}

	@Test
	void testAFailedTextOfASecondRunIsUnassigned() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final HttpSource flaky = http("flaky", "/flaky");
		flaky.setContinueOnFailure(true);
		final HttpSource reader = http("reader", "/R");
		reader.setComputes(List.of(awk("BEGIN { print \"[\" flaky \"]\" }")));
		final List<Source> sources = List.of(flaky, reader);

		assertEquals(
			Map.of("flaky", "first", "reader", "[first]"),
			newStrategy(telemetryManager).processSourcesAndComputes(sources, null, JOB, new ArrayList<>())
		);

		// The second run never sees the text of the first one
		flakyFails.set(true);
		assertEquals(
			Map.of("reader", "[]"),
			newStrategy(telemetryManager).processSourcesAndComputes(sources, null, JOB, new ArrayList<>())
		);
	}

	@Test
	void testToolCallHasItsOwnMap() throws Exception {
		final TelemetryManager telemetryManager = newTelemetryManager();
		final Source model = HttpSource.builder().url("/X1").key("${source::tools.t.sources.model}").build();
		final Source out = HttpSource.builder()
			.url("/O")
			.key("${source::tools.t.sources.out}")
			.computes(List.of(awk("BEGIN { print \"tool \" model }")))
			.build();
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put("model", model);
		sources.put("out", out);
		final Connector connector = Connector.builder()
			.connectorIdentity(ConnectorIdentity.builder().compiledFilename(TEST_CONNECTOR_ID).build())
			.tools(
				Map.of(
					"t",
					ConnectorTool.builder()
						.description("A tool")
						.sources(sources)
						.executionOrder(new LinkedHashSet<>(List.of("model", "out")))
						.result(new ToolResult("${source::tools.t.sources.out}", null))
						.build()
				)
			)
			.build();
		// The surrounding jobs record their duration on the endpoint host monitor
		MonitorFactory.builder()
			.telemetryManager(telemetryManager)
			.discoveryTime(System.currentTimeMillis())
			.keys(new HashSet<>(Set.of(MetricsHubConstants.HOST_NAME)))
			.build()
			.createEndpointHostMonitor();
		final ToolStrategy strategy = spy(
			ToolStrategy.builder()
				.telemetryManager(telemetryManager)
				.strategyTime(System.currentTimeMillis())
				.clientsExecutor(new ClientsExecutor(telemetryManager))
				.extensionManager(newExtensionManager())
				.connector(connector)
				.toolName("t")
				.arguments(Map.of())
				.build()
		);
		final AtomicReference<Object> textSources = new AtomicReference<>();
		doAnswer(invocation -> {
			final Object answered = invocation.callRealMethod();
			textSources.set(answered);
			return answered;
		}).when(strategy).processSourcesAndComputes(any(), any(), any(), any());

		strategy.run();

		assertEquals("tool X1\n", strategy.getResult().getText());
		assertEquals(Map.of("model", "X1", "out", "tool X1"), textSources.get());
	}
}
