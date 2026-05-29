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

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An abstract base class for deserializers handling non-null and non-blank values in collections.
 * Extends {@link AbstractCollectionDeserializer} and provides an implementation for extracting
 * non-null and non-blank values using a {@link Function}.
 */
public abstract class AbstractNonBlankNonNullInCollectionDeserializer extends AbstractCollectionDeserializer<String> {

	@Override
	protected Function<String, String> valueExtractor() {
		return nonNullNonBlankExtractor();
	}

	/**
	 * Return a function that extracts a non-null and non-blank value
	 *
	 * @return {@link Function} instance
	 */
	private Function<String, String> nonNullNonBlankExtractor() {
		return str -> {
			if (Objects.nonNull(str) && !str.isBlank()) {
				return str;
			}

			throw new IllegalArgumentException(getErrorMessage());
		};
	}

	protected abstract String getErrorMessage();

	@Override
	protected Predicate<String> getFilterPredicate() {
		return _ -> true;
	}
}
