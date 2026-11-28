package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jawk.backend.AVM;
import io.jawk.backend.SandboxedAVM;
import io.jawk.ext.GawkExtension;
import io.jawk.jrt.AwkNumberFormatException;
import io.jawk.util.AwkSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.awk.MappingExpression.Kind;
import org.metricshub.engine.awk.MappingExtensionForJawk.NoValue;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.strategy.utils.MappingFunctions;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class MappingExtensionForJawkTest {

	private static final String HOSTNAME = "host";
	private static final String KEY = "k";
	private static final GawkExtension GAWK = new GawkExtension();

	/**
	 * Evaluate the value as the evaluator does: compiled by {@link MappingExpression}, on a strict AVM.
	 */
	private static Object eval(
		final MappingExtensionForJawk extension,
		final boolean metrics,
		final String value,
		final String... row
	) throws Exception {
		final MappingExpression expression = MappingExpression.of(metrics, value);
		assertNotEquals(Kind.INVALID, expression.getKind(), value);
		final AwkSettings settings = AwkExecutor.newAwkSettings(";");
		settings.setStrictNumbers(true);
		final AVM avm = new SandboxedAVM(
			settings,
			Map.of(
				MappingExtensionForJawk.class.getName(),
				extension,
				UtilityExtensionForJawk.class.getName(),
				UtilityExtensionForJawk.INSTANCE,
				GawkExtension.class.getName(),
				GAWK
			)
		);
		return avm.eval(expression.getExpression(), TableInputSource.ofRow(List.of(row), ";"));
	}

	private static String text(final MappingExtensionForJawk extension, final String value, final String... row)
		throws Exception {
		return MappingExpression.toText(eval(extension, true, value, row));
	}

	private static String text(final String value, final String... row) throws Exception {
		return text(new MappingExtensionForJawk(), value, row);
	}

	/**
	 * The failure of an evaluation that has no value: what was thrown, or the cause of the class it was wrapped in.
	 */
	private static Throwable failure(
		final MappingExtensionForJawk extension,
		final boolean metrics,
		final String value,
		final String... row
	) {
		final Exception thrown = assertThrows(Exception.class, () -> eval(extension, metrics, value, row), value);
		for (Throwable t = thrown; t != null; t = t.getCause()) {
			if (t instanceof NoValue || t instanceof AwkNumberFormatException) {
				return t;
			}
		}
		return thrown;
	}

	private static void assertNoValue(
		final NoValue expected,
		final MappingExtensionForJawk extension,
		final String value,
		final String... row
	) {
		assertSame(expected, failure(extension, true, value, row), value);
	}

	private static MappingExtensionForJawk extension(final Monitor monitor, final long collectTime) {
		final MappingExtensionForJawk extension = new MappingExtensionForJawk(
			TelemetryManager.builder().build(),
			HOSTNAME,
			collectTime
		);
		extension.prepare(monitor, KEY);
		return extension;
	}

	@Test
	void unitConversions() throws Exception {
		assertEquals("125000000", text("= megaBit2Byte(1000)"));
		assertEquals("0.5700000000000001", text("= percent2Ratio(57)"));
		assertEquals("3.3000000000000003", text("= milliVolt2Volt(3300)"));
		assertEquals("17179869184", text("= mebiByte2Byte(16384)"));
		assertEquals("1000000", text("= megaBit2Bit($1)", "1"));
		assertEquals("3200000000", text("= megaHertz2Hertz($1)", "3200"));
		assertEquals("1500000", text("= megaBit2Byte(\" 12 \")"));
		assertEquals("1500000", text("= megaBit2Byte($1)", " 12 "));
		assertEquals("125000000", text("= megaBit2Byte(\"1e3\")"));
	}

	@Test
	void numberArgumentGrammar() {
		final MappingExtensionForJawk extension = new MappingExtensionForJawk();
		for (final String argument : new String[] {
			"N/A",
			"",
			"NaN",
			"Infinity",
			"+inf",
			"-nan",
			"10d",
			"0x1p3",
			"12 W" }) {
			// As a literal and as a cell, under metrics and under attributes
			assertSame(NoValue.NOT_A_NUMBER, failure(extension, true, "= megaBit2Byte(\"" + argument + "\")"), argument);
			assertSame(NoValue.NOT_A_NUMBER, failure(extension, false, "= megaBit2Byte($1)", argument), argument);
		}
		assertSame(NoValue.NOT_A_NUMBER, failure(extension, false, "= megaBit2Byte(unassigned)"));
		assertSame(NoValue.NOT_A_NUMBER, failure(extension, false, "= megaBit2Byte($2)", "1"));
		// A division by zero has no value either, whoever refuses it
		failure(extension, false, "= megaBit2Byte($1 / 0)", "1");
	}

	@Test
	void booleanAndStates() throws Exception {
		assertEquals("0", text("= boolean(\" 1 \")"));
		assertEquals("1", text("= boolean(\"TRUE\")"));
		assertEquals("1", text("= boolean($1)", "1"));
		assertEquals("0", text("= boolean(\"\")"));
		assertEquals("1", text("= legacyLinkStatus(\"ok\")"));
		assertEquals("0", text("= legacyLinkStatus(\"2\")"));
		assertNoValue(NoValue.UNKNOWN_STATE, new MappingExtensionForJawk(), "= legacyLinkStatus(\"x\")");
		assertEquals("1", text("= legacyFullDuplex($1)", "full"));
		assertNotNull(text("= legacyIntrusionStatus($1)", "ok"));
		assertNotNull(text("= legacyPredictedFailure($1)", "ok"));
		assertNotNull(text("= legacyNeedsCleaning($1)", "0"));
	}

	@Test
	void lookup() throws Exception {
		final Monitor withId = Monitor.builder().attributes(Map.of("serial", "S1", "id", "E1")).build();
		final Monitor emptyId = Monitor.builder().attributes(Map.of("serial", "S2", "id", "")).build();
		final MappingExtensionForJawk extension = new MappingExtensionForJawk(
			TelemetryManager.builder().monitors(Map.of("enclosure", Map.of("1", withId, "2", emptyId))).build(),
			HOSTNAME,
			0L
		);
		final String lookup = "= lookup(\"enclosure\", \"id\", \"serial\", $1)";
		assertEquals("E1", MappingExpression.toText(eval(extension, false, lookup, "S1")));
		assertSame(NoValue.NO_MATCH, failure(extension, false, lookup, " S1 "));
		assertSame(NoValue.NO_MATCH, failure(extension, false, lookup, "S2"));
		assertSame(NoValue.NO_MATCH, failure(extension, false, "= lookup(\"fan\", \"id\", \"serial\", $1)", "S1"));
		assertSame(NoValue.EMPTY_ARGUMENT, failure(extension, false, lookup, ""));
	}

	@Test
	void legacyLedStatus() throws Exception {
		final Monitor monitor = Monitor.builder().attributes(Map.of("__on_status", "OK")).build();
		final MappingExtensionForJawk extension = extension(monitor, 0L);
		assertEquals("ok", text(extension, "= legacyLedStatus($1)", "on"));
		assertEquals("ok", text(extension, "= legacyLedStatus($1)", "ON"));
		assertNoValue(NoValue.UNKNOWN_STATE, extension, "= legacyLedStatus($1)", "broken");
		assertNoValue(NoValue.UNKNOWN_STATE, extension, "= legacyLedStatus($1)", "off");

		final MappingExtensionForJawk emptyStatus = extension(
			Monitor.builder().attributes(Map.of("__on_status", "")).build(),
			0L
		);
		assertNoValue(NoValue.UNKNOWN_STATE, emptyStatus, "= legacyLedStatus($1)", "on");
	}

	@Test
	void legacyPowerSupplyUtilization() throws Exception {
		final Monitor monitor = Monitor.builder().build();
		assertNoValue(NoValue.NO_LIMIT, extension(monitor, 0L), "= legacyPowerSupplyUtilization($1)", "50");
		new MetricFactory(HOSTNAME, null).collectNumberMetric(monitor, "hw.power_supply.limit", 200.0, 0L);
		assertEquals("0.25", text(extension(monitor, 0L), "= legacyPowerSupplyUtilization($1)", "50"));
		assertNoValue(NoValue.NOT_A_NUMBER, extension(monitor, 0L), "= legacyPowerSupplyUtilization($1)", "N/A");
	}

	/**
	 * A monitor whose counter was 1000 at 120000 ms, saved as the previous value
	 */
	private static Monitor rateMonitor() {
		final Monitor monitor = Monitor.builder().build();
		new MetricFactory(HOSTNAME, null).collectNumberMetric(monitor, "__k.rate_from", 1000.0, 120000L);
		monitor.getMetric("__k.rate_from", NumberMetric.class).save();
		return monitor;
	}

	@Test
	void rate() throws Exception {
		final Monitor monitor = Monitor.builder().build();
		assertNoValue(NoValue.NO_RESULT_YET, extension(monitor, 120000L), "= rate($1)", "1000");
		assertEquals(1000.0, CollectHelper.getNumberMetricValue(monitor, "__k.rate_from", false));
		monitor.getMetric("__k.rate_from", NumberMetric.class).save();
		assertEquals("8.333333333333334", text(extension(monitor, 240000L), "= rate($1)", "2000"));

		assertEquals("66.66666666666667", text(extension(rateMonitor(), 240000L), "= rate($1) * 8", "2000"));

		final Monitor twice = rateMonitor();
		final MappingExtensionForJawk extension = extension(twice, 240000L);
		final Throwable second = failure(extension, true, "= rate($1) + rate($2)", "3000", "4000");
		assertSame(NoValue.SECOND_STATE_CALL, second);
		assertEquals(3000.0, CollectHelper.getNumberMetricValue(twice, "__k.rate_from", false));

		// No state written for an argument that is not a number
		final Monitor notANumber = rateMonitor();
		assertNoValue(NoValue.NOT_A_NUMBER, extension(notANumber, 240000L), "= rate($1)", "N/A");
		assertEquals(1000.0, CollectHelper.getNumberMetricValue(notANumber, "__k.rate_from", false));
	}

	@Test
	void fakeCounterAndAccumulate() throws Exception {
		final Monitor monitor = Monitor.builder().build();
		final MetricFactory metricFactory = new MetricFactory(HOSTNAME, null);
		metricFactory.collectNumberMetric(monitor, "__k.fake_counter_from", 1000.0, 120000L);
		monitor.getMetric("__k.fake_counter_from", NumberMetric.class).save();
		assertEquals("240000", text(extension(monitor, 240000L), "= fakeCounter($1)", "2000"));

		final Monitor withPrevious = Monitor.builder().build();
		metricFactory.collectNumberMetric(withPrevious, "__k.fake_counter_from", 1000.0, 120000L);
		metricFactory.collectNumberMetric(withPrevious, KEY, 120000.0, 120000L);
		assertEquals("360000", text(extension(withPrevious, 240000L), "= fakeCounter($1)", "2000"));

		// Two runs at different times, without PrepareCollectStrategy: the total moves by one delta per run
		final Monitor total = Monitor.builder().build();
		assertEquals("5", text(extension(total, 1000L), "= accumulate($1)", "5"));
		metricFactory.collectNumberMetric(total, KEY, 5.0, 1000L);
		assertEquals("8", text(extension(total, 2000L), "= accumulate($1)", "3"));
		metricFactory.collectNumberMetric(total, KEY, 8.0, 2000L);
		assertEquals("12", text(extension(total, 3000L), "= accumulate($1)", "4"));
	}

	@Test
	void computePowerShareRatio() {
		final MappingExtensionForJawk extension = new MappingExtensionForJawk(
			TelemetryManager.builder().build(),
			HOSTNAME,
			0L
		);
		extension.prepare(Monitor.builder().build(), "hw.vm.power_ratio");
		final Throwable thrown = failure(extension, true, "= computePowerShareRatio($1)", "10");
		assertSame(NoValue.POWER_SHARE, thrown);
		assertEquals(10.0, extension.getPowerShare());

		extension.prepare(Monitor.builder().build(), "hw.vm.power_ratio");
		assertNull(extension.getPowerShare());
		assertSame(NoValue.NOT_A_NUMBER, failure(extension, true, "= computePowerShareRatio($1)", "N/A"));
		assertNull(extension.getPowerShare());
	}

	@Test
	void strictNumbersRefuseNotANumber() {
		assertTrue(failure(new MappingExtensionForJawk(), false, "= $1 * 2", "N/A") instanceof AwkNumberFormatException);
	}

	@Test
	void metricsFunctionWithoutMonitorIsNoValue() {
		final MappingExtensionForJawk extension = extension(null, 0L);
		assertNoValue(NoValue.NO_MONITOR, extension, "= legacyLedStatus($1)", "on");
		assertNoValue(NoValue.NO_MONITOR, extension, "= rate($1)", "1");
		assertNoValue(NoValue.NO_MONITOR, extension, "= accumulate($1)", "1");
	}

	@Test
	void findMonitor() {
		final Monitor monitor = Monitor.builder().attributes(Map.of("serial", "S1")).build();
		assertNull(MappingFunctions.findMonitor(null, "serial", "S1"));
		assertNull(MappingFunctions.findMonitor(Map.of("1", monitor), "serial", "S2"));
		assertSame(monitor, MappingFunctions.findMonitor(Map.of("1", monitor), "serial", "S1"));
	}
}
