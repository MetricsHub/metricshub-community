package org.metricshub.engine.connector.deserializer.custom;

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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collector;

/**
 * An abstract deserializer for collections of a specific type. Subclasses should provide implementations for
 * value extraction, empty collection creation, collector creation, and building a collection from another collection.
 * This deserializer handles the deserialization of JSON arrays and string values into a collection of the specified type.
 *
 * @param <T> The type of elements in the collection.
 * @see JsonDeserializer
 */
public abstract class AbstractCollectionDeserializer<T> extends JsonDeserializer<Collection<T>> {

	@Override
	public Collection<T> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		if (parser == null) {
			return emptyCollection();
		}

		try {
			if (parser.isExpectedStartArrayToken()) {
				final Collection<String> strCollection = parser.readValueAs(new TypeReference<Collection<String>>() {});

				return Optional
					.ofNullable(strCollection)
					.map(collection -> collection.stream().map(valueExtractor()).filter(Objects::nonNull).collect(collector()))
					.orElse(emptyCollection());
			}

			return Optional
				.ofNullable(parser.getValueAsString())
				.map(str ->
					Arrays
						.stream(str.split(","))
						.map(String::trim) // optional: trim spaces
						.map(valueExtractor()) // apply the custom extractor
						.collect(collector())
				) // collect into your custom container
				.orElse(emptyCollection());
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage());
		}
	}

	/**
	 * Value extractor function to execute when reading the string value
	 *
	 * @return {@link Function} which consumes a string value and returns T
	 */
	protected abstract Function<String, T> valueExtractor();

	/**
	 * Builds an empty collection
	 *
	 * @return {@link Collection} of T
	 */
	protected abstract Collection<T> emptyCollection();

	/**
	 * A mutable reduction function that accumulates values into a mutable
	 * result container
	 *
	 * @return {@link Collector} function
	 */
	protected abstract Collector<T, ?, Collection<T>> collector();
}
