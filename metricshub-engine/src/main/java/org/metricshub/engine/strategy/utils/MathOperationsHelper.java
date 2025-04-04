package org.metricshub.engine.strategy.utils;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@code MathOperationsHelper} class provides utility methods for performing basic mathematical operations,
 * including subtraction, division, multiplication, rate calculation, and finding the minimum of two operands. The
 * class includes methods to handle potential edge cases, such as division by zero or suspicious negative results.
 * Logging statements are used to provide additional information in case of unexpected scenarios.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class MathOperationsHelper {

	/**
	 * Performs a subtraction arithmetic operation.
	 *
	 * @param metricName The name of the metric.
	 * @param minuend    Minuend of the subtraction.
	 * @param subtrahend Subtrahend of the subtraction.
	 * @param hostname   Current hostname used for logging only.
	 * @return The result of the subtraction operation, or null if any operand is null.
	 */
	public static Double subtract(
		final String metricName,
		final Double minuend,
		final Double subtrahend,
		final String hostname
	) {
		if (minuend == null || subtrahend == null) {
			return null;
		}

		final double result = minuend - subtrahend;

		if (result < 0) {
			log.warn(
				"Hostname {} - Suspicious negative value ({} - {}) = {} for metric {}.",
				hostname,
				minuend,
				subtrahend,
				result,
				metricName
			);
			return null;
		}

		return result;
	}

	/**
	 * Performs a division arithmetic operation.
	 *
	 * @param metricName The metric for the division operation.
	 * @param dividend   The dividend to use.
	 * @param divisor    The divisor to use.
	 * @param hostname   Current hostname used for logging only.
	 * @return The result of the division operation, or null if any operand is null or division by zero occurs.
	 */
	public static Double divide(
		final String metricName,
		final Double dividend,
		final Double divisor,
		final String hostname
	) {
		if (dividend == null || divisor == null) {
			return null;
		}

		if (divisor == 0) {
			log.debug(
				"Hostname {} - Couldn't compute ({} / {}) for metric {}. Division by zero is not allowed.",
				hostname,
				dividend,
				divisor,
				metricName
			);
			return null;
		}

		final double result = dividend / divisor;

		if (result < 0) {
			log.warn(
				"Hostname {} - Suspicious negative value ({} / {}) = {} for metric {}.",
				hostname,
				dividend,
				divisor,
				result,
				metricName
			);
			return null;
		}

		return result;
	}

	/**
	 * Performs a multiplication arithmetic operation.
	 *
	 * @param metricName   The name of the metric for the multiplication operation.
	 * @param multiplier   The multiplier to use.
	 * @param multiplicand The multiplicand to use.
	 * @param hostname     Current hostname used for logging only.
	 * @return The result of the multiplication operation, or null if any operand is null.
	 */
	public static Double multiply(
		final String metricName,
		final Double multiplier,
		final Double multiplicand,
		final String hostname
	) {
		if (multiplier == null || multiplicand == null) {
			return null;
		}

		double result = multiplier * multiplicand;

		if (result < 0) {
			log.warn(
				"Hostname {} - Suspicious negative value ({} * {}) = {} for metric {}.",
				hostname,
				multiplier,
				multiplicand,
				result,
				metricName
			);
			return null;
		}

		return result;
	}

	/**
	 * Computes a rate using the formula (value - previousValue) / (collectTime - previousCollectTime).
	 *
	 * @param metricName          The metric name for rate calculation.
	 * @param value               The value from the current collect.
	 * @param previousValue       The value from the previous collect.
	 * @param collectTime         The time of the current collect.
	 * @param previousCollectTime The time of the previous collect.
	 * @param hostname            Current hostname used for logging only.
	 * @return The computed rate, or null if any operand is null or division by zero occurs.
	 */
	public static Double rate(
		String metricName,
		Double value,
		Double previousValue,
		Double collectTime,
		Double previousCollectTime,
		String hostname
	) {
		return divide(
			metricName,
			subtract(metricName, value, previousValue, hostname),
			subtract(metricName, collectTime, previousCollectTime, hostname),
			hostname
		);
	}

	/**
	 * Computes the minimum between two {@link Double} operands. If one of the operands is null, returns the other operand.
	 *
	 * @param operandOne The first operand.
	 * @param operandTwo The second operand.
	 * @return The minimum value between the operands, or the non-null operand if the other is null.
	 */
	public static Double min(final Double operandOne, final Double operandTwo) {
		if (operandOne == null) {
			return operandTwo;
		} else if (operandTwo == null) {
			return operandOne;
		} else {
			return Math.min(operandOne, operandTwo);
		}
	}
}
