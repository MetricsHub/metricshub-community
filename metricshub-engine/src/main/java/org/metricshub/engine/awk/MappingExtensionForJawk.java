package org.metricshub.engine.awk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import io.jawk.ext.AbstractExtension;
import io.jawk.ext.ExtensionFunction;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.jrt.JRT;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.metricshub.engine.common.helpers.state.DuplexMode;
import org.metricshub.engine.common.helpers.state.IState;
import org.metricshub.engine.common.helpers.state.IntrusionStatus;
import org.metricshub.engine.common.helpers.state.LinkStatus;
import org.metricshub.engine.common.helpers.state.NeedsCleaning;
import org.metricshub.engine.common.helpers.state.PredictedFailure;
import org.metricshub.engine.strategy.utils.MappingFunctions;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The functions of the mapping expressions (a mapping value that starts with <code>=</code>), with the context of the
 * evaluation that calls them: the host, the collect time, the monitor and the key computed. A function that cannot
 * answer throws {@link NoValue}: the whole value is left out.
 * <p>
 * One instance per evaluator, which calls {@link #prepare(Monitor, String)} before each value. The instances without
 * context only compile expressions.
 */
public class MappingExtensionForJawk extends AbstractExtension {

	/**
	 * The functions only the metrics section declares: they read or write the monitor.
	 */
	public static final Set<String> METRICS_FUNCTIONS = Set.of(
		"legacyLedStatus",
		"legacyPowerSupplyUtilization",
		"rate",
		"fakeCounter",
		"accumulate",
		"computePowerShareRatio"
	);

	/**
	 * The functions that must be the whole value of a metric, with any arithmetic inside their argument.
	 */
	public static final Set<String> WHOLE_VALUE_FUNCTIONS = Set.of("fakeCounter", "accumulate", "computePowerShareRatio");

	/**
	 * The functions that keep a state: at most one call per evaluation.
	 */
	public static final Set<String> STATE_FUNCTIONS = Set.of(
		"rate",
		"fakeCounter",
		"accumulate",
		"computePowerShareRatio"
	);

	private final TelemetryManager telemetryManager;
	private final String hostname;
	private final long collectTime;

	private Monitor monitor;
	private String key;
	private int stateCalls;
	private Double powerShare;

	/**
	 * A prototype that compiles expressions of the metrics section: no context, never called.
	 */
	public MappingExtensionForJawk() {
		this(null, null, 0L);
	}

	/**
	 * The extension of one evaluator: the host and the collect time of the table it evaluates.
	 *
	 * @param telemetryManager The host
	 * @param hostname         The host name, for the logs
	 * @param collectTime      The collect time, in milliseconds
	 */
	public MappingExtensionForJawk(
		final TelemetryManager telemetryManager,
		final String hostname,
		final long collectTime
	) {
		this.telemetryManager = telemetryManager;
		this.hostname = hostname;
		this.collectTime = collectTime;
	}

	/**
	 * A prototype that compiles expressions: the given functions are not declared, so a call of one does not compile.
	 *
	 * @param functionNames The functions to leave out
	 * @return a new prototype
	 */
	public static MappingExtensionForJawk without(final Set<String> functionNames) {
		return new Without(functionNames);
	}

	/**
	 * Sets the context of the next evaluation, and resets the count of state calls and the power share.
	 *
	 * @param monitor The monitor, <code>null</code> while attributes are evaluated
	 * @param key     The key computed
	 */
	public void prepare(final Monitor monitor, final String key) {
		this.monitor = monitor;
		this.key = key;
		this.stateCalls = 0;
		this.powerShare = null;
	}

	/**
	 * The argument of the <code>computePowerShareRatio</code> call of the last evaluation: the evaluator stores it as
	 * the hidden metric <code>__&lt;key&gt;.raw_power_share</code>.
	 *
	 * @return the power share, or <code>null</code>
	 */
	public Double getPowerShare() {
		return powerShare;
	}

	@Override
	public String getExtensionName() {
		return "MetricsHub mapping";
	}

	/**
	 * Converts megabits to bits.
	 *
	 * @param value A number
	 * @return the value in bits
	 */
	@JawkFunction("megaBit2Bit")
	public Double megaBit2Bit(final Object value) {
		return num(value) * MappingFunctions.MEGABIT_2_BIT_FACTOR;
	}

	/**
	 * Converts megabits to bytes.
	 *
	 * @param value A number
	 * @return the value in bytes
	 */
	@JawkFunction("megaBit2Byte")
	public Double megaBit2Byte(final Object value) {
		return num(value) * MappingFunctions.MEGABIT_2_BYTE_FACTOR;
	}

	/**
	 * Converts a percentage to a ratio.
	 *
	 * @param value A number
	 * @return the ratio
	 */
	@JawkFunction("percent2Ratio")
	public Double percent2Ratio(final Object value) {
		return num(value) * MappingFunctions.PERCENT_2_RATIO_FACTOR;
	}

	/**
	 * Converts megahertz to hertz.
	 *
	 * @param value A number
	 * @return the value in hertz
	 */
	@JawkFunction("megaHertz2Hertz")
	public Double megaHertz2Hertz(final Object value) {
		return num(value) * MappingFunctions.MEGAHERTZ_2_HERTZ_FACTOR;
	}

	/**
	 * Converts mebibytes to bytes.
	 *
	 * @param value A number
	 * @return the value in bytes
	 */
	@JawkFunction("mebiByte2Byte")
	public Double mebiByte2Byte(final Object value) {
		return num(value) * MappingFunctions.MEBIBYTE_2_BYTE_FACTOR;
	}

	/**
	 * Converts millivolts to volts.
	 *
	 * @param value A number
	 * @return the value in volts
	 */
	@JawkFunction("milliVolt2Volt")
	public Double milliVolt2Volt(final Object value) {
		return num(value) * MappingFunctions.MILLIVOLT_2_VOLT_FACTOR;
	}

	/**
	 * The <code>boolean()</code> function.
	 *
	 * @param value A text
	 * @return 1 for <code>1</code> or <code>true</code> in any case, else 0
	 */
	@JawkFunction("boolean")
	public Long toBoolean(final Object value) {
		return MappingFunctions.isTrue(toAwkString(value)) ? 1L : 0L;
	}

	/**
	 * Converts a legacy intrusion status.
	 *
	 * @param value A state
	 * @return its numeric value
	 */
	@JawkFunction("legacyIntrusionStatus")
	public Long legacyIntrusionStatus(final Object value) {
		return state(IntrusionStatus.interpret(toAwkString(value)));
	}

	/**
	 * Converts a legacy predicted failure.
	 *
	 * @param value A state
	 * @return its numeric value
	 */
	@JawkFunction("legacyPredictedFailure")
	public Long legacyPredictedFailure(final Object value) {
		return state(PredictedFailure.interpret(toAwkString(value)));
	}

	/**
	 * Converts a legacy needs cleaning state.
	 *
	 * @param value A state
	 * @return its numeric value
	 */
	@JawkFunction("legacyNeedsCleaning")
	public Long legacyNeedsCleaning(final Object value) {
		return state(NeedsCleaning.interpret(toAwkString(value)));
	}

	/**
	 * Converts a legacy link status.
	 *
	 * @param value A state
	 * @return its numeric value
	 */
	@JawkFunction("legacyLinkStatus")
	public Long legacyLinkStatus(final Object value) {
		return state(LinkStatus.interpret(toAwkString(value)));
	}

	/**
	 * Converts a legacy duplex mode.
	 *
	 * @param value A state
	 * @return its numeric value
	 */
	@JawkFunction("legacyFullDuplex")
	public Long legacyFullDuplex(final Object value) {
		return state(DuplexMode.interpret(toAwkString(value)));
	}

	/**
	 * The attribute of the first monitor of a type whose attribute matches a value.
	 *
	 * @param type     The monitor type
	 * @param returned The attribute to return
	 * @param matched  The attribute to match
	 * @param value    The value it must have
	 * @return the attribute
	 */
	@JawkFunction("lookup")
	public String lookup(final Object type, final Object returned, final Object matched, final Object value) {
		final String typeText = toAwkString(type);
		final String returnedText = toAwkString(returned);
		final String matchedText = toAwkString(matched);
		final String valueText = toAwkString(value);
		if (typeText.isEmpty() || returnedText.isEmpty() || matchedText.isEmpty() || valueText.isEmpty()) {
			throw NoValue.EMPTY_ARGUMENT;
		}
		final Monitor found = MappingFunctions.findMonitor(
			telemetryManager.findMonitorsByType(typeText),
			matchedText,
			valueText
		);
		final String result = found == null ? null : found.getAttributes().get(returnedText);
		if (result == null || result.isEmpty()) {
			throw NoValue.NO_MATCH;
		}
		return result;
	}

	/**
	 * The status of a LED state, from the attributes of the monitor.
	 *
	 * @param value The LED state
	 * @return the lowercased status
	 */
	@JawkFunction("legacyLedStatus")
	public String legacyLedStatus(final Object value) {
		final String status = MappingFunctions.ledStatus(requireMonitor().getAttributes(), toAwkString(value));
		if (status == null || status.isEmpty()) {
			throw NoValue.UNKNOWN_STATE;
		}
		return status;
	}

	/**
	 * The power divided by the power limit of the monitor.
	 *
	 * @param value The power
	 * @return the ratio
	 */
	@JawkFunction("legacyPowerSupplyUtilization")
	public Double legacyPowerSupplyUtilization(final Object value) {
		final Monitor current = requireMonitor();
		final Double result = MappingFunctions.powerSupplyUtilization(current, key, num(value), hostname);
		if (result == null) {
			throw NoValue.NO_LIMIT;
		}
		return result;
	}

	/**
	 * The rate of a counter.
	 *
	 * @param value The counter
	 * @return the increase per second since the previous collect
	 */
	@JawkFunction("rate")
	public Double rate(final Object value) {
		final Monitor current = requireMonitor();
		countStateCall();
		final Double result = MappingFunctions.rate(
			current,
			key,
			num(value),
			collectTime,
			hostname,
			telemetryManager.getConnectorStore()
		);
		if (result == null) {
			throw NoValue.NO_RESULT_YET;
		}
		return result;
	}

	/**
	 * A counter made from a rate.
	 *
	 * @param value The rate
	 * @return the counter
	 */
	@JawkFunction("fakeCounter")
	public Double fakeCounter(final Object value) {
		final Monitor current = requireMonitor();
		countStateCall();
		final Double result = MappingFunctions.fakeCounter(
			current,
			key,
			num(value),
			collectTime,
			hostname,
			telemetryManager.getConnectorStore()
		);
		if (result == null) {
			throw NoValue.NO_RESULT_YET;
		}
		return result;
	}

	/**
	 * A counter made from deltas.
	 *
	 * @param value The delta
	 * @return the delta plus the previous value
	 */
	@JawkFunction("accumulate")
	public Double accumulate(final Object value) {
		final Monitor current = requireMonitor();
		countStateCall();
		return MappingFunctions.accumulate(current, key, num(value), collectTime);
	}

	/**
	 * Keeps the power share for the hidden metric <code>__&lt;key&gt;.raw_power_share</code>: the key itself has no
	 * value.
	 *
	 * @param value The power share
	 * @return never
	 */
	@JawkFunction("computePowerShareRatio")
	public Double computePowerShareRatio(final Object value) {
		countStateCall();
		powerShare = num(value);
		throw NoValue.POWER_SHARE;
	}

	/**
	 * A number argument, with the strict grammar of the arithmetic of the expression.
	 *
	 * @param value The argument
	 * @return its finite value
	 */
	private static double num(final Object value) {
		if (!JRT.isNumber(value)) {
			throw NoValue.NOT_A_NUMBER;
		}
		final double number = JRT.toDouble(value);
		if (!Double.isFinite(number)) {
			throw NoValue.NOT_A_NUMBER;
		}
		return number;
	}

	private static Long state(final Optional<? extends IState> state) {
		return state.map(s -> (long) s.getNumericValue()).orElseThrow(() -> NoValue.UNKNOWN_STATE);
	}

	private Monitor requireMonitor() {
		if (monitor == null) {
			throw NoValue.NO_MONITOR;
		}
		return monitor;
	}

	private void countStateCall() {
		if (++stateCalls > 1) {
			throw NoValue.SECOND_STATE_CALL;
		}
	}

	/**
	 * Thrown by a function that cannot answer: the evaluator leaves the whole value out. Stackless, immutable,
	 * shared: its message is a constant reason, never data.
	 */
	public static final class NoValue extends RuntimeException {

		private static final long serialVersionUID = 1L;

		/**
		 * An argument is not a number
		 */
		public static final NoValue NOT_A_NUMBER = new NoValue("an argument is not a number");

		/**
		 * The state is not known
		 */
		public static final NoValue UNKNOWN_STATE = new NoValue("the state is not known");

		/**
		 * No monitor matches
		 */
		public static final NoValue NO_MATCH = new NoValue("no monitor matches");

		/**
		 * An argument is empty
		 */
		public static final NoValue EMPTY_ARGUMENT = new NoValue("an argument is empty");

		/**
		 * The monitor has no power limit
		 */
		public static final NoValue NO_LIMIT = new NoValue("the monitor has no hw.power_supply.limit");

		/**
		 * No result yet
		 */
		public static final NoValue NO_RESULT_YET = new NoValue(
			"no result: first collect, counter reset or no elapsed time"
		);

		/**
		 * The function needs the monitor
		 */
		public static final NoValue NO_MONITOR = new NoValue("the function needs the monitor");

		/**
		 * A second call of a function that keeps a state
		 */
		public static final NoValue SECOND_STATE_CALL = new NoValue("a second call of a function that keeps a state");

		/**
		 * computePowerShareRatio: the key has no value, the hidden metric is in {@link #getPowerShare()}
		 */
		public static final NoValue POWER_SHARE = new NoValue("the power share is a hidden metric");

		private NoValue(final String reason) {
			super(reason, null, false, false);
		}
	}

	/**
	 * A prototype that does not declare some of the functions. The functions stay declared by
	 * {@link MappingExtensionForJawk}, so the expressions it compiles run on an AVM holding a
	 * {@link MappingExtensionForJawk}.
	 */
	private static final class Without extends MappingExtensionForJawk {

		private final Map<String, ExtensionFunction> functions;

		private Without(final Set<String> functionNames) {
			final Map<String, ExtensionFunction> all = new LinkedHashMap<>(super.getExtensionFunctions());
			all.keySet().removeAll(functionNames);
			functions = Collections.unmodifiableMap(all);
		}

		@Override
		public Map<String, ExtensionFunction> getExtensionFunctions() {
			return functions;
		}
	}
}
