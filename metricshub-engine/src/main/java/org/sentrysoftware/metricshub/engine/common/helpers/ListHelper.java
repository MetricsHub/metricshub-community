package org.sentrysoftware.metricshub.engine.common.helpers;

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

import java.util.List;

/**
 * Helper class for common operations on lists.
 */
public class ListHelper {

	private ListHelper() {}

	/**
	 * Returns the element at the specified position in this list.
	 *
	 * @param <T>           The type of elements in the list.
	 * @param list          The list from which we want to get the element at the specified position.
	 * @param index         Index of the element to return.
	 * @param defaultValue  The default value to return if the element cannot be extracted.
	 * @return The element at the specified position in the list, or the default value if the index is out of bounds.
	 */
	public static <T> T getValueAtIndex(List<T> list, int index, T defaultValue) {
		if (list == null || list.size() <= index) {
			return defaultValue;
		}
		return list.get(index);
	}
}
