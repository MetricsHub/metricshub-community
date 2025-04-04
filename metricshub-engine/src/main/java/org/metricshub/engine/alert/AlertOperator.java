package org.metricshub.engine.alert;

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

import java.util.Objects;
import java.util.function.BiPredicate;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents different comparison operators used in alert conditions, such as
 * equality, greater than, greater than or equal to, less than, less than or equal to,
 * and not equal to.
 */
@AllArgsConstructor
public enum AlertOperator {
	// CHECKSTYLE:OFF
	/**
	 * Equality operator.
	 */
	EQ(
		(paramValue, threshold) -> nonNull(paramValue, threshold) && paramValue.doubleValue() == threshold.doubleValue(),
		"=="
	),
	/**
	 * Greater than operator.
	 */
	GT(
		(paramValue, threshold) -> nonNull(paramValue, threshold) && paramValue.doubleValue() > threshold.doubleValue(),
		">"
	),
	/**
	 * Greater than or equal to operator.
	 */
	GTE(
		(paramValue, threshold) -> nonNull(paramValue, threshold) && paramValue.doubleValue() >= threshold.doubleValue(),
		">="
	),
	/**
	 * Less than operator.
	 */
	LT(
		(paramValue, threshold) -> nonNull(paramValue, threshold) && paramValue.doubleValue() < threshold.doubleValue(),
		"<"
	),
	/**
	 * Less than or equal to operator.
	 */
	LTE(
		(paramValue, threshold) -> nonNull(paramValue, threshold) && paramValue.doubleValue() <= threshold.doubleValue(),
		"<="
	),
	/**
	 * Not equal to operator.
	 */
	NE(
		(paramValue, threshold) -> nonNull(paramValue, threshold) && paramValue.doubleValue() != threshold.doubleValue(),
		"!="
	);

	// CHECKSTYLE:ON

	@Getter
	private BiPredicate<Double, Double> function;

	@Getter
	private String expression;

	private static boolean nonNull(Double paramValue, Double threshold) {
		return Objects.nonNull(paramValue) && Objects.nonNull(threshold);
	}
}
