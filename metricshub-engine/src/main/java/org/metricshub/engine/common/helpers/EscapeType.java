package org.metricshub.engine.common.helpers;

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

import java.util.function.UnaryOperator;
import lombok.AllArgsConstructor;

/**
 * Enum representing various escape types, each associated with
 * 	a specific method in the MacrosUpdater class that handles the escaping
 * 	of special characters for that type. Each constant references a method
 * 	that takes a String input and returns an escaped String output
 */
@AllArgsConstructor
public enum EscapeType {
	JSON(MacrosUpdater::escapeJsonSpecialCharacters),
	XML(MacrosUpdater::escapeXmlSpecialCharacters),
	URL(MacrosUpdater::escapeUrlSpecialCharacters),
	REGEX(MacrosUpdater::escapeRegexSpecialCharacters),
	WINDOWS(MacrosUpdater::escapeWindowsCmdSpecialCharacters),
	CMD(MacrosUpdater::escapeWindowsCmdSpecialCharacters),
	POWERSHELL(MacrosUpdater::escapePowershellSpecialCharacters),
	LINUX(MacrosUpdater::escapeBashSpecialCharacters),
	BASH(MacrosUpdater::escapeBashSpecialCharacters),
	SQL(MacrosUpdater::escapeSqlSpecialCharacters);

	private final UnaryOperator<String> escapeFunction;

	/**
	 * Applies the associated escape method to the provided input string.
	 *
	 * @param input the string to be escaped
	 * @return the escaped string
	 */
	public String escape(String input) {
		return escapeFunction.apply(input);
	}

	/**
	 * Retrieves the corresponding EscapeType from a string representation.
	 *
	 * @param escapeType the string representation of the escape type
	 * @return the corresponding EscapeType
	 * @throws IllegalStateException if the provided string does not match any escape type
	 */
	public static EscapeType fromString(String escapeType) {
		for (EscapeType type : values()) {
			if (type.name().equalsIgnoreCase(escapeType)) {
				return type;
			}
		}
		return null;
	}
}
