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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A function an LLM may call, as declared by a connector ({@code tools:} section) or by a protocol extension
 * ({@link org.metricshub.engine.extension.IProtocolExtension#getTools()}). The agent turns it into an MCP tool,
 * adding the host selection; the engine only describes what the tool takes and runs it.
 *
 * @param name        the tool name, unique within its connector or extension
 * @param description what the tool does, as read by the LLM
 * @param parameters  the arguments the tool accepts, by name, in declaration order
 * @param destructive whether the tool changes the state of the monitored host (a reboot, a command), so that it
 *                    is advertised as such and only when allowed
 */
public record ToolDefinition(
	String name,
	String description,
	Map<String, ToolParameter> parameters,
	boolean destructive
) {
	/**
	 * Validates the name and freezes the parameters, keeping their declaration order.
	 *
	 * @param name        the tool name
	 * @param description what the tool does
	 * @param parameters  the arguments the tool accepts, {@code null} for none
	 * @param destructive whether the tool changes the state of the monitored host
	 */
	public ToolDefinition {
		Objects.requireNonNull(name, "A tool needs a name");
		parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
	}

	/**
	 * Starts a definition, for the extensions that declare their tools in Java.
	 *
	 * @param name        the tool name
	 * @param description what the tool does
	 * @return the builder
	 */
	public static Builder builder(final String name, final String description) {
		return new Builder(name, description);
	}

	/**
	 * Builds a {@link ToolDefinition} parameter by parameter.
	 */
	public static final class Builder {

		private final String name;
		private final String description;
		private final Map<String, ToolParameter> parameters = new LinkedHashMap<>();
		private boolean destructive;

		private Builder(final String name, final String description) {
			this.name = name;
			this.description = description;
		}

		/**
		 * Declares an optional string parameter.
		 *
		 * @param parameterName the parameter name
		 * @param description   what the value is for
		 * @return this builder
		 */
		public Builder parameter(final String parameterName, final String description) {
			return parameter(parameterName, ToolParameter.STRING, description, false, null);
		}

		/**
		 * Declares a required string parameter.
		 *
		 * @param parameterName the parameter name
		 * @param description   what the value is for
		 * @return this builder
		 */
		public Builder requiredParameter(final String parameterName, final String description) {
			return parameter(parameterName, ToolParameter.STRING, description, true, null);
		}

		/**
		 * Declares a parameter.
		 *
		 * @param parameterName the parameter name
		 * @param type          the JSON schema type of the value
		 * @param description   what the value is for
		 * @param required      whether an invocation must supply the value
		 * @param defaultValue  the value used when an invocation does not supply one
		 * @return this builder
		 */
		public Builder parameter(
			final String parameterName,
			final String type,
			final String description,
			final boolean required,
			final String defaultValue
		) {
			parameters.put(parameterName, new ToolParameter(type, description, required, defaultValue));
			return this;
		}

		/**
		 * Marks the tool as changing the state of the host.
		 *
		 * @return this builder
		 */
		public Builder destructive() {
			this.destructive = true;
			return this;
		}

		/**
		 * @return the definition
		 */
		public ToolDefinition build() {
			return new ToolDefinition(name, description, parameters, destructive);
		}
	}

	/**
	 * Completes and checks the arguments of an invocation: an argument the tool does not declare is dropped, a
	 * supplied one is validated against its parameter, a parameter that is not supplied takes its default value, and
	 * a required parameter without one is reported.
	 *
	 * @param arguments the arguments supplied by the invocation, by parameter name, {@code null} for none
	 * @return the arguments to run the tool with, defaults applied
	 * @throws IllegalArgumentException if an argument is not valid (see {@link ToolParameter#validate}), or a
	 *                                  required parameter is neither supplied nor defaulted
	 */
	public Map<String, String> resolveArguments(final Map<String, String> arguments) {
		final Map<String, String> resolved = new LinkedHashMap<>();
		final List<String> missing = new ArrayList<>();
		parameters.forEach((parameterName, parameter) -> {
			final String value = arguments == null ? null : arguments.get(parameterName);
			if (value != null) {
				parameter.validate(parameterName, value);
				resolved.put(parameterName, value);
			} else if (parameter.defaultValue() != null) {
				resolved.put(parameterName, parameter.defaultValue());
			} else if (parameter.required()) {
				missing.add(parameterName);
			}
		});
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("Tool %s requires the argument(s): %s".formatted(name, missing));
		}
		return resolved;
	}
}
