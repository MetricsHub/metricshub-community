package org.metricshub.engine.common.helpers.state;

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

import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enumeration representing states related to predicted failures.
 */
@AllArgsConstructor
public enum PredictedFailure implements IState {
	/**
	 * OK state with numeric value 0.
	 */
	OK(0),
	/**
	 * FAILURE_PREDICTED state with numeric value 1.
	 */
	FAILURE_PREDICTED(1);

	@Getter
	private int numericValue;

	/**
	 * Map each state value to a {@link PredictedFailure}
	 */
	private static final Map<String, PredictedFailure> PREDICTED_FAILURE_MAP = Map.of(
		"0",
		OK,
		"ok",
		OK,
		"false",
		OK,
		"1",
		FAILURE_PREDICTED,
		"degraded",
		FAILURE_PREDICTED,
		"2",
		FAILURE_PREDICTED,
		"failed",
		FAILURE_PREDICTED,
		"true",
		FAILURE_PREDICTED
	);

	/**
	 * Interpret the specified state value:
	 *  <ul>
	 *  	<li>{0, ok, false} as OK</li>
	 *  	<li>{1, degraded, 2, failed, true} as FAILURE_PREDICTED</li>
	 *  </ul>
	 * @param state String to be interpreted
	 * @return {@link Optional} of {@link PredictedFailure}
	 */
	public static Optional<PredictedFailure> interpret(final String state) {
		return IState.interpret(state, PREDICTED_FAILURE_MAP, PredictedFailure.class);
	}
}
