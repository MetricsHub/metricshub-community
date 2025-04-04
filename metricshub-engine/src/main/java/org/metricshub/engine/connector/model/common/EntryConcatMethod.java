package org.metricshub.engine.connector.model.common;

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

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * Represents different methods for concatenating entries in a connector.
 */
@Getter
@AllArgsConstructor
public enum EntryConcatMethod implements IEntryConcatMethod {
	/**
	 * Concatenation using a list.
	 */
	@JsonAlias("list")
	LIST("list"),
	/**
	 * Concatenation using a JSON array.
	 */
	@JsonAlias(value = { "jsonArray", "json_array" })
	JSON_ARRAY("jsonArray"),
	/**
	 * Concatenation using an extended JSON array.
	 */
	@JsonAlias(value = { "JSONArrayExtended", "json_array_extended" })
	JSON_ARRAY_EXTENDED("jsonArrayExtended");

	/**
	 * List of all possible values for {@link EntryConcatMethod}.
	 */
	public static final List<EntryConcatMethod> ENUM_VALUES = List.of(values());

	private String name;

	/**
	 * Get {@link EntryConcatMethod} by name, the name defined in the connector code.
	 *
	 * @param name The name of the concatenation method.
	 * @return {@link EntryConcatMethod} instance.
	 * @throws IllegalArgumentException if an invalid name is provided.
	 */
	public static EntryConcatMethod getByName(@NonNull final String name) {
		return ENUM_VALUES
			.stream()
			.filter(n -> n.name().equalsIgnoreCase(name) || n.getName().equalsIgnoreCase(name))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Invalid EntryConcatMethod name: " + name));
	}

	@Override
	public IEntryConcatMethod copy() {
		return this;
	}

	@Override
	public String getDescription() {
		return name;
	}
}
