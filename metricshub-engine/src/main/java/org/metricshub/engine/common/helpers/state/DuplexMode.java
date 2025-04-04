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
 * Enumeration representing different duplex modes.
 */
@AllArgsConstructor
public enum DuplexMode implements IState {
	/**
	 * Represents the HALF duplex mode.
	 */
	HALF(0),
	/**
	 * Represents the FULL duplex mode.
	 */
	FULL(1);

	/**
	 * The numeric value associated with the duplex mode.
	 */
	@Getter
	private int numericValue;

	/**
	 * Map each state value to a {@link DuplexMode}
	 */
	private static final Map<String, DuplexMode> DUPLEX_MODE_MAP = Map.of(
		"0",
		HALF,
		"no",
		HALF,
		"half",
		HALF,
		"degraded",
		HALF,
		"1",
		FULL,
		"yes",
		FULL,
		"full",
		FULL,
		"ok",
		FULL
	);

	/**
	 * Interpret the specified state value:
	 *  <ul>
	 *  	<li>{0, no, half, degraded} as HALF</li>
	 *  	<li>{1, yes, full, ok} as FULL</li>
	 *  </ul>
	 * @param state String to be interpreted
	 * @return {@link Optional} of {@link DuplexMode}
	 */
	public static Optional<DuplexMode> interpret(final String state) {
		return IState.interpret(state, DUPLEX_MODE_MAP, DuplexMode.class);
	}
}
