package org.metricshub.engine.tool;

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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;

/**
 * One argument of a {@link ToolDefinition}, as declared by a connector or a protocol extension.
 *
 * @param type         the JSON schema type of the value: {@code string}, {@code integer}, {@code number} or
 *                     {@code boolean}. {@code string} when omitted.
 * @param description  what the value is for, as read by the LLM
 * @param required     whether an invocation must supply the value
 * @param defaultValue the value used when an invocation does not supply one
 */
public record ToolParameter(
	String type,
	String description,
	boolean required,
	@JsonProperty("default") String defaultValue
) implements Serializable {
	/**
	 * The type used when a parameter declares none.
	 */
	public static final String STRING = "string";

	/**
	 * The JSON schema types a parameter may declare.
	 */
	public static final List<String> TYPES = List.of(STRING, "integer", "number", "boolean");

	/**
	 * Normalizes the type: a missing or blank type is {@value #STRING}.
	 *
	 * @param type         the JSON schema type of the value
	 * @param description  what the value is for
	 * @param required     whether an invocation must supply the value
	 * @param defaultValue the value used when an invocation does not supply one
	 */
	public ToolParameter {
		if (type == null || type.isBlank()) {
			type = STRING;
		}
		type = type.trim().toLowerCase();
		if (!TYPES.contains(type)) {
			// Copied verbatim into the advertised JSON schema, where an unknown type would fail the whole tool list
			throw new IllegalArgumentException("Unknown tool parameter type '%s': expected one of %s".formatted(type, TYPES));
		}
	}
}
