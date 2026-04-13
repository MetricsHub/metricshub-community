package org.metricshub.engine.common.helpers.state;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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
 * Enumeration representing different intrusion status.
 */
@AllArgsConstructor
public enum IntrusionStatus implements IState {
	/**
	 * Represents the CLOSED intrusion status.
	 */
	CLOSED(0),
	/**
	 * Represents the OPEN intrusion status.
	 */
	OPEN(1);

	/**
	 * The numeric value associated with the intrusion status.
	 */
	@Getter
	private int numericValue;

	/**
	 * Map each state value to a {@link IntrusionStatus}
	 */
	private static final Map<String, IntrusionStatus> INTRUSION_STATUS_MAP = Map.of(
		"0",
		CLOSED,
		"ok",
		CLOSED,
		"closed",
		CLOSED,
		"1",
		OPEN,
		"degraded",
		OPEN,
		"2",
		OPEN,
		"failed",
		OPEN,
		"open",
		OPEN
	);

	/**
	 * Interpret the specified state value:
	 *  <ul>
	 *  	<li>{0, ok, closed} as CLOSED</li>
	 *  	<li>{1, degraded, 2, failed, open} as OPEN</li>
	 *  </ul>
	 * @param state String to be interpreted
	 * @return {@link Optional} of {@link IntrusionStatus}
	 */
	public static Optional<IntrusionStatus> interpret(final String state) {
		return IState.interpret(state, INTRUSION_STATUS_MAP, IntrusionStatus.class);
	}
}
