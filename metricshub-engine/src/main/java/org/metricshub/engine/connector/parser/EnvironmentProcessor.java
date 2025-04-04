package org.metricshub.engine.connector.parser;

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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Processor for replacing placeholder values in a JsonNode using environment variables.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnvironmentProcessor extends AbstractNodeProcessor {

	/**
	 * Environment Variable Pattern
	 */
	private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{env::([\\w-]+)\\}");

	/**
	 * Constructs a EnvironmentProcessor without a next processor.
	 */
	public EnvironmentProcessor() {
		super(null);
	}

	/**
	 * Processes a JsonNode by replacing placeholder values with corresponding environment variables.
	 *
	 * @param node The JsonNode to be processed.
	 * @return The processed JsonNode with placeholders replaced by environment variables.
	 */
	@Override
	protected JsonNode processNode(JsonNode node) {
		final UnaryOperator<String> updater = this::performEnvReplacements;
		final Predicate<String> replacementPredicate = Objects::nonNull;

		JsonNodeUpdater
			.jsonNodeUpdaterBuilder()
			.withJsonNode(node)
			.withPredicate(replacementPredicate)
			.withUpdater(updater)
			.build()
			.update();

		return node;
	}

	/**
	 * Replace environment placeholders in the given value with corresponding values from system
	 * environment variables.
	 *
	 * @param value        The string to be replaced.
	 * @return A new {@link String} with the placeholders replaced.
	 */
	private String performEnvReplacements(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}

		return ENV_PATTERN
			.matcher(value)
			.replaceAll(match -> {
				final String variableValue = System.getenv(match.group(1));
				if (variableValue != null) {
					return Matcher.quoteReplacement(variableValue);
				}
				return Matcher.quoteReplacement(match.group());
			});
	}
}
