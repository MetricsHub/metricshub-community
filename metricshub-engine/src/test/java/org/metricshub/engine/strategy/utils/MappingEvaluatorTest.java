package org.metricshub.engine.strategy.utils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jawk.backend.SandboxedAVM;
import io.jawk.ext.GawkExtension;
import io.jawk.util.AwkSettings;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.awk.AwkExecutor;
import org.metricshub.engine.awk.MappingExpression;
import org.metricshub.engine.awk.MappingExtensionForJawk;
import org.metricshub.engine.awk.MappingExtensionForJawk.NoValue;
import org.metricshub.engine.awk.TableInputSource;
import org.metricshub.engine.awk.UtilityExtensionForJawk;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.strategy.utils.MappingEvaluator.Section;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MappingEvaluatorTest {

	private static final String SECRET = "s3cr3t-session";

	private static final JobInfo JOB_INFO = JobInfo.builder()
		.hostname("host")
		.connectorId("connector")
		.monitorType("disk")
		.jobName("discovery")
		.build();

	/**
	 * A case-insensitive section map, as the deserializer builds it.
	 */
	private static Map<String, String> section(final String... keysAndValues) {
		final Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (int i = 0; i < keysAndValues.length; i += 2) {
			map.put(keysAndValues[i], keysAndValues[i + 1]);
		}
		return map;
	}

	private static Mapping attributes(final String... keysAndValues) {
		return Mapping.builder().attributes(section(keysAndValues)).build();
	}

	private static Mapping metrics(final String... keysAndValues) {
		return Mapping.builder().metrics(section(keysAndValues)).build();
	}

	private static Connector connector(final Map<String, String> constants, final Map<String, String> variableValues) {
		final Connector connector = new Connector();
		connector.setConstants(constants);
		connector.setVariableValues(variableValues);
		return connector;
	}

	private static MappingEvaluator evaluator(final Mapping mapping) {
		return evaluator(mapping, 0L, null);
	}

	private static MappingEvaluator evaluator(
		final Mapping mapping,
		final long collectTime,
		final Map<String, String> textSources
	) {
		return new MappingEvaluator(
			TelemetryManager.builder().build(),
			new Connector(),
			mapping,
			JOB_INFO,
			collectTime,
			textSources
		);
	}

	private static Map<String, String> attributesOf(final MappingEvaluator evaluator, final String... row) {
		return evaluator.evaluate(Section.ATTRIBUTES, List.of(row), null);
	}

	/**
	 * Runs the action with the logs of the evaluator enabled (DEBUG and above) and returns what they printed.
	 * slf4j-simple writes to the current {@link System#err}.
	 */
	private static String logsOf(final Runnable action) throws Exception {
		final Logger logger = LoggerFactory.getLogger(MappingEvaluator.class);
		final Field level = logger.getClass().getDeclaredField("currentLogLevel");
		level.setAccessible(true);
		final int previousLevel = level.getInt(logger);
		final PrintStream previousErr = System.err;
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			level.setInt(logger, 0);
			System.setErr(new PrintStream(out, true, UTF_8));
			action.run();
		} finally {
			System.setErr(previousErr);
			level.setInt(logger, previousLevel);
		}
		return out.toString(UTF_8);
	}

	private static long warnings(final String logs) {
		return logs.lines().filter(line -> line.contains(" WARN ")).count();
	}

	@Test
	void legacyMappingKeepsTheTexts() {
		final Mapping mapping = attributes("id", "$1", "name", "= $2");
		final Mapping legacy = evaluator(mapping).getLegacyMapping();
		assertEquals(Map.of("id", "$1"), legacy.getAttributes());
		assertEquals("$1", legacy.getAttributes().get("ID"));

		final Mapping texts = Mapping.builder().source("${source::s}").attributes(section("id", "$1")).build();
		assertSame(texts, evaluator(texts).getLegacyMapping());
	}

	@Test
	void fieldAndConstant() {
		final MappingEvaluator evaluator = evaluator(attributes("a", "= $1", "b", "= \"x\"", "c", "= $5", "d", "= $5 + 0"));
		assertEquals(Map.of("a", "007", "b", "x", "c", ""), attributesOf(evaluator, "007", "y"));
	}

	@Test
	void noAvmForFieldsAndConstants() {
		final Mapping mapping = Mapping.builder()
			.attributes(section("id", "$1", "a", "= $1", "b", "= 2 * 3"))
			.metrics(section("m", "= $2"))
			.conditionalCollection(section("m", "= \"\""))
			.legacyTextParameters(section("t", "= \"x\""))
			.build();
		final MappingEvaluator evaluator = evaluator(mapping);
		final List<String> row = List.of("1", "2");
		final Monitor monitor = Monitor.builder().build();
		assertEquals(Map.of("a", "1", "b", "6"), evaluator.evaluate(Section.ATTRIBUTES, row, null));
		assertEquals(Map.of("m", "2"), evaluator.evaluate(Section.METRICS, row, monitor));
		assertEquals(Map.of("m", ""), evaluator.evaluate(Section.CONDITIONAL_COLLECTION, row, monitor));
		assertEquals(Map.of("t", "x"), evaluator.evaluate(Section.LEGACY_TEXT_PARAMETERS, row, monitor));
		assertFalse(evaluator.hasAvm());

		mapping.getMetrics().put("n", "= $2 * 2");
		final MappingEvaluator withEval = evaluator(mapping);
		assertEquals(Map.of("m", "2", "n", "4"), withEval.evaluate(Section.METRICS, row, monitor));
		assertTrue(withEval.hasAvm());
	}

	@Test
	void onlyReferencedNamesAreSet() {
		final MappingEvaluator evaluator = new MappingEvaluator(
			TelemetryManager.builder().build(),
			connector(Map.of("A", "1", "B", "2"), Map.of("c", "3")),
			attributes("a", "= $1 * A", "b", "= d"),
			JOB_INFO,
			0L,
			Map.of("d", "x")
		);
		assertEquals(Set.of("A", "d"), evaluator.getInjectedNames());
		assertEquals(Map.of("a", "5", "b", "x"), attributesOf(evaluator, "5"));
	}

	@Test
	void noValuePerSection() {
		final String failing = "= megaBit2Bit($1)";
		final Mapping mapping = Mapping.builder()
			.attributes(section("k", failing))
			.metrics(section("k", failing))
			.conditionalCollection(section("k", failing))
			.legacyTextParameters(section("k", failing))
			.build();
		final MappingEvaluator evaluator = evaluator(mapping);
		final List<String> row = List.of("N/A");
		final Monitor monitor = Monitor.builder().build();
		assertEquals(Map.of(), evaluator.evaluate(Section.ATTRIBUTES, row, null));
		assertEquals(Map.of(), evaluator.evaluate(Section.METRICS, row, monitor));
		assertEquals(Map.of("k", ""), evaluator.evaluate(Section.CONDITIONAL_COLLECTION, row, monitor));
		assertEquals(Map.of(), evaluator.evaluate(Section.LEGACY_TEXT_PARAMETERS, row, monitor));
	}

	@Test
	void strictNumberErrorDoesNotLogTheValue() throws Exception {
		final Map<String, String> texts = Map.of("token", SECRET);
		assertEquals(Map.of(), attributesOf(evaluator(attributes("a", "= token * 2"), 0L, texts), "1"));

		// The strict number error, as the evaluator gets it
		final AwkSettings settings = AwkExecutor.newAwkSettings(";");
		settings.setStrictNumbers(true);
		settings.putVariable("token", SECRET);
		final SandboxedAVM avm = new SandboxedAVM(
			settings,
			Map.of(
				MappingExtensionForJawk.class.getName(),
				new MappingExtensionForJawk(),
				UtilityExtensionForJawk.class.getName(),
				UtilityExtensionForJawk.INSTANCE,
				GawkExtension.class.getName(),
				new GawkExtension()
			)
		);
		final Exception strictError = assertThrows(
			Exception.class,
			() -> avm.eval(MappingExpression.of(false, "= token * 2").getExpression(), TableInputSource.ofRow(List.of(), ";"))
		);
		final RuntimeException other = new RuntimeException(SECRET, new IllegalStateException(SECRET));
		for (final Throwable failure : List.of(strictError, other)) {
			final String description = MappingEvaluator.describe(failure);
			assertFalse(description.contains(SECRET), description);
			assertFalse(description.contains(failure.getMessage()), description);
		}
		assertEquals("an argument is not a number", MappingEvaluator.describe(NoValue.NOT_A_NUMBER));
		assertEquals("RuntimeException (IllegalStateException)", MappingEvaluator.describe(other));

		// What the evaluator logs: a strict number error, another exception, a second state call
		final TelemetryManager telemetryManager = mock(TelemetryManager.class);
		when(telemetryManager.findMonitorsByType(any())).thenThrow(other);
		final MappingEvaluator evaluator = new MappingEvaluator(
			telemetryManager,
			new Connector(),
			Mapping.builder()
				.attributes(section("a", "= token * 2", "b", "= lookup(\"x\", \"id\", \"serial\", $3)"))
				.metrics(section("r", "= rate($1) + rate($2)"))
				.build(),
			JOB_INFO,
			240000L,
			texts
		);
		final List<String> row = List.of("3000", "4000", SECRET);
		final String logs = logsOf(() -> {
			assertEquals(Map.of(), evaluator.evaluate(Section.ATTRIBUTES, row, null));
			assertEquals(Map.of(), evaluator.evaluate(Section.METRICS, row, rateMonitor()));
		});
		assertEquals(2, warnings(logs), logs);
		assertFalse(logs.contains(SECRET), logs);
	}

	@Test
	void numbersAsIntegers() {
		final MappingEvaluator evaluator = evaluator(attributes("double", "= $1 * 2", "third", "= $2 / 3"));
		assertEquals(Map.of("double", "3", "third", "3.3333333333333335"), attributesOf(evaluator, "1.50", "10"));
	}

	@Test
	void strictNumbers() {
		final MappingEvaluator evaluator = evaluator(metrics("m", "= $1 / $2 * 100"));
		assertEquals(Map.of(), evaluator.evaluate(Section.METRICS, List.of(""), Monitor.builder().build()));
		assertEquals(Map.of(), evaluator.evaluate(Section.METRICS, List.of("N/A", "2"), Monitor.builder().build()));
		assertEquals(Map.of("m", "50"), evaluator.evaluate(Section.METRICS, List.of("1", "2"), Monitor.builder().build()));
	}

	@Test
	void connectorValues() {
		final Mapping mapping = attributes(
			"size",
			"= $1 * BLOCK_SIZE * scale",
			"name",
			"= model \" (\" $2 \")\"",
			"product",
			"= unset * 2",
			"label",
			"= unset \"\""
		);
		final MappingEvaluator evaluator = new MappingEvaluator(
			TelemetryManager.builder().build(),
			connector(Map.of("BLOCK_SIZE", "512"), Map.of("scale", "2")),
			mapping,
			JOB_INFO,
			0L,
			Map.of("model", "X1")
		);
		assertEquals(Map.of("size", "3072", "name", "X1 (b)", "label", ""), attributesOf(evaluator, "3", "b"));

		final MappingEvaluator notANumber = evaluator(attributes("double", "= model * 2"), 0L, Map.of("model", "N/A"));
		assertEquals(Map.of(), attributesOf(notANumber));
	}

	@Test
	void textsAreStrnum() {
		final MappingEvaluator evaluator = evaluator(
			attributes("above", "= limit > 5", "code", "= code"),
			0L,
			Map.of("limit", "10", "code", "007")
		);
		assertEquals(Map.of("above", "1", "code", "007"), attributesOf(evaluator));
	}

	@Test
	void rowBoundForEachValue() {
		final MappingEvaluator evaluator = evaluator(
			attributes("a", "= (sub(/a/, \"b\", $1)) \":\" $1", "b", "= $1", "c", "= $1 \"\"")
		);
		assertEquals(Map.of("a", "1:bbc", "b", "abc", "c", "abc"), attributesOf(evaluator, "abc"));
	}

	@Test
	void emptyAndInvalidExpressions() throws Exception {
		final MappingEvaluator evaluator = evaluator(attributes("e", "= # todo", "f", "= foo($1)", "g", "="));
		final String logs = logsOf(() -> {
			for (int i = 0; i < 3; i++) {
				assertEquals(Map.of(), attributesOf(evaluator, "1"));
			}
		});
		assertEquals(3, warnings(logs), logs);
	}

	@Test
	void stateFunctionsSeeTheMonitor() {
		final Mapping mapping = metrics("k", "= rate($1)");
		final Monitor monitor = Monitor.builder().build();
		assertEquals(Map.of(), evaluator(mapping, 120000L, null).evaluate(Section.METRICS, List.of("1000"), monitor));
		monitor.getMetric("__k.rate_from", NumberMetric.class).save();
		assertEquals(
			Map.of("k", "8.333333333333334"),
			evaluator(mapping, 240000L, null).evaluate(Section.METRICS, List.of("2000"), monitor)
		);
	}

	/**
	 * A monitor whose counter r was 1000 at 120000 ms, saved as the previous value: the first rate() call answers.
	 */
	private static Monitor rateMonitor() {
		final Monitor monitor = Monitor.builder().build();
		new MetricFactory("host", null).collectNumberMetric(monitor, "__r.rate_from", 1000.0, 120000L);
		monitor.getMetric("__r.rate_from", NumberMetric.class).save();
		return monitor;
	}

	@Test
	void secondStateCall() throws Exception {
		final MappingEvaluator evaluator = evaluator(metrics("r", "= rate($1) + rate($2)"), 240000L, null);
		final Monitor monitor = rateMonitor();
		final String logs = logsOf(() -> {
			for (int i = 0; i < 3; i++) {
				assertEquals(Map.of(), evaluator.evaluate(Section.METRICS, List.of("3000", "4000"), monitor));
			}
		});
		assertEquals(1, warnings(logs), logs);
		assertTrue(logs.contains("a second call of a function that keeps a state"), logs);
		assertEquals(3000.0, CollectHelper.getNumberMetricValue(monitor, "__r.rate_from", false));
	}

	@Test
	void missingColumnWarnsOnce() throws Exception {
		final MappingEvaluator evaluator = evaluator(attributes("c", "= $5"));
		final String logs = logsOf(() -> {
			for (int i = 0; i < 3; i++) {
				assertEquals(Map.of("c", ""), attributesOf(evaluator, SECRET, SECRET));
			}
		});
		assertEquals(1, warnings(logs), logs);
		assertFalse(logs.contains(SECRET), logs);
	}

	@Test
	void powerShare() throws Exception {
		final MappingEvaluator evaluator = evaluator(metrics("hw.vm.power_ratio", "= computePowerShareRatio($1)"));
		final Monitor monitor = Monitor.builder().build();
		final String logs = logsOf(
			() -> assertEquals(
				Map.of("__hw.vm.power_ratio.raw_power_share", "10"),
				evaluator.evaluate(Section.METRICS, List.of("10"), monitor)
			)
		);
		assertEquals("", logs);
		assertEquals(Map.of(), evaluator.evaluate(Section.METRICS, List.of("N/A"), monitor));
	}
}
