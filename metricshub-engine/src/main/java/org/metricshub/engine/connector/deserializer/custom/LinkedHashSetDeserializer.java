package org.metricshub.engine.connector.deserializer.custom;

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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Custom deserializer for a {@link LinkedHashSet} of strings.
 * <p>
 * Accepts either:
 * <ul>
 *   <li>a YAML/JSON array (e.g. {@code ["value1", "value2"]})</li>
 *   <li>a comma-separated string (e.g. {@code "value1,value2"})</li>
 * </ul>
 * Blank strings are filtered out. Null values are rejected.
 */
public class LinkedHashSetDeserializer extends AbstractCollectionDeserializer<String> {

	@Override
	protected Function<String, String> valueExtractor() {
		return str -> {
			if (Objects.nonNull(str)) {
				return str;
			}

			throw new IllegalArgumentException("Null value is not accepted in sets.");
		};
	}

	@Override
	protected Collection<String> emptyCollection() {
		return new LinkedHashSet<>();
	}

	@Override
	protected Collector<String, ?, Collection<String>> collector() {
		return Collectors.toCollection(LinkedHashSet::new);
	}

	@Override
	protected Predicate<? super String> getFilterPredicate() {
		return str -> !str.isBlank();
	}
}
