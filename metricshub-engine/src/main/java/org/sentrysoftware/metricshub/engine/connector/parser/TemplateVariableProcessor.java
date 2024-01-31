package org.sentrysoftware.metricshub.engine.connector.parser;

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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Data
@EqualsAndHashCode(callSuper = true)
public class TemplateVariableProcessor extends AbstractNodeProcessor {

	@NonNull
	private Map<String, String> connectorVariables = new HashMap<>();

	@Builder
	public TemplateVariableProcessor(@NonNull Map<String, String> connectorVariables, AbstractNodeProcessor next) {
		super(next);
		this.connectorVariables = connectorVariables;
	}

	/**
	 * Processes a given Json node by calling {@link JsonNodeUpdater}
	 * @param node The input Json node.
	 * @return The processed Json node.
	 * @throws IOException thrown by {@link AbstractNodeProcessor}
	 */
	@Override
	public JsonNode processNode(JsonNode node) throws IOException {
		// Create the unary operator that replaces the template variable pattern by the agent config defined variable value
		final UnaryOperator<String> variableValueUpdater = value -> performReplacements(connectorVariables, value);

		// Create a predicate to check the matching with the template variable pattern
		final Predicate<String> isMatchingConnectorVariableRegex = str -> str != null && str.contains("${var::");

		// Call JsonNodeUpdater to replace the placeholder by the variable value
		JsonNodeUpdater
			.jsonNodeUpdaterBuilder()
			.withJsonNode(node)
			.withPredicate(isMatchingConnectorVariableRegex)
			.withUpdater(variableValueUpdater)
			.build()
			.update();

		return node;
	}

	/**
	 * Replace placeholders in the given value with corresponding values from the provided
	 * key-value pairs in the replacements {@link Map}.
	 *
	 * @param replacements Key-value pairs representing placeholders and their replacement values.
	 *                     <br>Example: { $constants.query1=MyQuery1, $constants.query2=MyQuery2 }
	 * @param value        The string to be replaced.
	 * @return A new {@link String} with the placeholders replaced.
	 */
	private String performReplacements(final Map<String, String> replacements, String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}

		// Loop over each placeholder and perform replacement
		for (final Map.Entry<String, String> entry : replacements.entrySet()) {
			final String key = entry.getKey();
			final Pattern pattern = Pattern.compile(String.format("\\$\\{var\\:\\:%s\\}", key));
			final Matcher matcher = pattern.matcher(value);
			while (matcher.find()) {
				value = value.replace(matcher.group(), entry.getValue());
			}
		}

		// return the new value
		return value;
	}
}
