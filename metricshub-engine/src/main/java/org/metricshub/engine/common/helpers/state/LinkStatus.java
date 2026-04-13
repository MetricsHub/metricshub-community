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
 * Enumeration representing the link status states.
 *
 * The states are:
 * <ul>
 *     <li>{@link #PLUGGED} with numeric value 1</li>
 *     <li>{@link #UNPLUGGED} with numeric value 0</li>
 * </ul>
 */
@AllArgsConstructor
public enum LinkStatus implements IState {
	/**
	 * Represents the plugged state with a numeric value of 1.
	 */
	PLUGGED(1),
	/**
	 * Represents the unplugged state with a numeric value of 0.
	 */
	UNPLUGGED(0);

	/**
	 * The numeric value associated with each state.
	 */
	@Getter
	private int numericValue;

	/**
	 * Map each state value to a {@link LinkStatus}
	 */
	private static final Map<String, LinkStatus> LINK_STATUS_MAP = Map.of(
		"0",
		PLUGGED,
		"ok",
		PLUGGED,
		"plugged",
		PLUGGED,
		"1",
		UNPLUGGED,
		"degraded",
		UNPLUGGED,
		"2",
		UNPLUGGED,
		"failed",
		UNPLUGGED,
		"unplugged",
		UNPLUGGED,
		"warn",
		UNPLUGGED,
		"alarm",
		UNPLUGGED
	);

	/**
	 * Interpret the specified state value:
	 *  <ul>
	 *  	<li>{0, ok, plugged} as Plugged</li>
	 *  	<li>{1, degraded, failed, 2, unplugged, warn, alarm} as Unplugged</li>
	 *  </ul>
	 * @param state String to be interpreted
	 * @return {@link Optional} of {@link LinkStatus}
	 */
	public static Optional<LinkStatus> interpret(final String state) {
		return IState.interpret(state, LINK_STATUS_MAP, LinkStatus.class);
	}
}
