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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReferenceResolverProcessor extends AbstractNodeProcessor {

	private static final String SOURCE_REF_FORMAT = "${source::%s}";

	private static final Pattern REGEX_SOURCE_REF_MONITORS = Pattern.compile(
		"\\$\\{source::(?!((?i)monitors\\.([\\w()\\.-]+)\\.(discovery|collect|simple)\\.sources\\.([\\w()\\.-]+)\\}|(?i)(beforeAll|afterAll)\\.([\\w()\\.-]+)\\}))([\\w()\\.-]+)\\}"
	);

	@Builder
	public ReferenceResolverProcessor(final AbstractNodeProcessor next) {
		super(next);
	}

	private static final Set<String> JOBS = Set.of("discovery", "collect", "simple");

	/**
	 * Updates the source references in the given value based on the context provided.<br>
	 * This method handles the source references in the monitors, beforeAll, and afterAll sections.
	 *
	 * @param valueToUpdate The string value that contains the source references to be updated.
	 * @param context The context used for updating the source references, providing additional
	 *                information for the update process.
	 * @return The updated string after applying both update operations on the source references.
	 */
	private String updateSourceReferences(String valueToUpdate, final String context) {
		if (valueToUpdate == null || valueToUpdate.isEmpty() || context == null || context.isEmpty()) {
			return valueToUpdate;
		}

		final var parts = context.split("\\.");

		return REGEX_SOURCE_REF_MONITORS
			.matcher(valueToUpdate)
			.replaceAll(match -> {
				// If the context starts with the string "monitors", perform the corresponding replacement
				if (
					// CHECKSTYLE:OFF
					parts.length >= 4 &&
					"monitors".equals(parts[0]) &&
					JOBS.contains(parts[2]) &&
					("sources".equals(parts[3]) || "mapping".equals(parts[3]))
					// CHECKSTYLE:ON
				) {
					return Matcher.quoteReplacement(
						String.format(
							SOURCE_REF_FORMAT,
							Stream.of(parts[0], parts[1], parts[2], "sources", match.group(7)).collect(Collectors.joining("."))
						)
					);
				} else if (parts.length >= 2 && ("beforeAll".equals(parts[0]) || "afterAll".equals(parts[0]))) {
					// If the context starts with the string "beforeAll" or "afterAll", perform the corresponding replacement
					return Matcher.quoteReplacement(
						String.format(SOURCE_REF_FORMAT, Stream.of(parts[0], match.group(7)).collect(Collectors.joining(".")))
					);
				}

				return Matcher.quoteReplacement(match.group());
			});
	}

	/**
	 * Processes the given JsonNode by calling {@link JsonNodeUpdater}
	 *
	 * @param node The JsonNode to be processed.
	 * @return The processed node as a {@link JsonNode} instance
	 * @throws IOException thrown by the super class method
	 */
	@Override
	protected JsonNode processNode(final JsonNode node) throws IOException {
		JsonNodeContextUpdater
			.jsonNodeContextUpdaterBuilder()
			.withJsonNode(node)
			.withPredicate(Objects::nonNull)
			.withUpdater(this::updateSourceReferences)
			.build()
			.update();
		return node;
	}
}
