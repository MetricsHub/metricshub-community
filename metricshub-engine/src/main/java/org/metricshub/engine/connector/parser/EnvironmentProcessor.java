package org.metricshub.engine.connector.parser;

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

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.metricshub.engine.common.helpers.LocalOsHandler;

/**
 * Processor for replacing placeholder values in a JsonNode using environment variables.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnvironmentProcessor extends AbstractNodeProcessor {

	/**
	 * Environment Variable Pattern: {@code ${env::NAME}} or {@code ${env::NAME:-default}}.
	 * Group 1 is the variable name, group 2 the optional default value.
	 */
	private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{env::([\\w-]+)(?::-([^}]*))?\\}");

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

		JsonNodeUpdater.jsonNodeUpdaterBuilder()
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
	 * <p>
	 * {@code ${env::NAME:-default}} resolves to {@code default} when {@code NAME} is unset or empty
	 * (shell semantics). {@code ${env::NAME}} is left untouched when {@code NAME} is unset.
	 * On Linux, an unset or empty {@code HOSTNAME} is first resolved from the kernel hostname,
	 * since services do not normally inherit this environment variable.
	 *
	 * @param value        The string to be replaced.
	 * @return A new {@link String} with the placeholders replaced.
	 */
	public String performEnvReplacements(String value) {
		return performEnvReplacements(value, System::getenv);
	}

	/**
	 * Replace environment placeholders in the given value using the given environment lookup.
	 *
	 * @param value       The string to be replaced.
	 * @param envResolver Resolves an environment variable name to its value, or {@code null} if unset.
	 * @return A new {@link String} with the placeholders replaced.
	 */
	static String performEnvReplacements(final String value, final UnaryOperator<String> envResolver) {
		if (value == null || value.isEmpty()) {
			return value;
		}

		return ENV_PATTERN.matcher(value).replaceAll(match -> {
			final String variableValue = resolveEnvironmentVariable(match.group(1), envResolver);
			final String defaultValue = match.group(2);
			if (defaultValue != null) {
				return Matcher.quoteReplacement(
					variableValue == null || variableValue.isEmpty() ? defaultValue : variableValue
				);
			}
			if (variableValue != null) {
				return Matcher.quoteReplacement(variableValue);
			}
			return Matcher.quoteReplacement(match.group());
		});
	}

	/**
	 * Look up an environment variable, using the Linux hostname when HOSTNAME is unavailable.
	 *
	 * @param name        The environment variable name.
	 * @param envResolver The environment lookup.
	 * @return The variable value, or the kernel hostname for an unset or empty Linux HOSTNAME.
	 */
	private static String resolveEnvironmentVariable(final String name, final UnaryOperator<String> envResolver) {
		final String value = envResolver.apply(name);
		if (value != null && !value.isEmpty()) {
			return value;
		}
		if ("HOSTNAME".equals(name) && LocalOsHandler.getOS().filter(LocalOsHandler.LINUX::equals).isPresent()) {
			try {
				final String hostname = Files.readString(Path.of("/proc/sys/kernel/hostname")).trim();
				return hostname.isEmpty() ? value : hostname;
			} catch (IOException e) {
				// Keep the expression's fallback behavior if the kernel hostname cannot be read.
			}
		}
		return value;
	}
}
