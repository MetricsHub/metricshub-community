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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import org.metricshub.engine.connector.model.common.ITranslationTable;
import org.metricshub.engine.connector.model.common.ReferenceTranslationTable;
import org.metricshub.engine.connector.model.common.TranslationTable;

/**
 * Custom deserializer for deserializing translation tables.
 */
public class TranslationTableDeserializer extends JsonDeserializer<ITranslationTable> {

	@Override
	public ITranslationTable deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		if (parser == null) {
			return null;
		}

		final String key = parser.currentName();
		final JsonNode node = parser.readValueAsTree();

		if (node != null) {
			if (node.isTextual()) {
				return new ReferenceTranslationTable(node.asText());
			} else if (node.isObject()) {
				final Map<String, String> map = new ObjectMapper()
					.convertValue(node, new TypeReference<Map<String, String>>() {});
				final Map<String, String> caseInsensitiveTreeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				caseInsensitiveTreeMap.putAll(map);
				return new TranslationTable(caseInsensitiveTreeMap);
			}
		}

		throw new InvalidFormatException(
			parser,
			String.format("Invalid translation table value encountered for property '%s'.", key),
			node,
			JsonNode.class
		);
	}
}
