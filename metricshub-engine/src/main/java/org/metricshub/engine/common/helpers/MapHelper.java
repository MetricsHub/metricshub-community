package org.metricshub.engine.common.helpers;

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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Helper class for operations related to maps.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MapHelper {

	/**
	 * Check if the given maps are deeply the same.
	 *
	 * @param first  The first map to compare.
	 * @param second The second map to compare.
	 * @return {@code true} if the maps are deeply equal, otherwise {@code false}.
	 */
	public static boolean areEqual(final Map<String, String> first, final Map<String, String> second) {
		if (Objects.equals(first, second)) {
			return true;
		}

		if (first == null || second == null) {
			return false;
		}

		if (first.size() != second.size()) {
			return false;
		}

		return first.entrySet().stream().allMatch(e -> e.getValue().equals(second.get(e.getKey())));
	}
}
