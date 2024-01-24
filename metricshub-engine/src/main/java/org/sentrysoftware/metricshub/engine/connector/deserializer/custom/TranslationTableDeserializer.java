package org.sentrysoftware.metricshub.engine.connector.deserializer.custom;

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
import org.sentrysoftware.metricshub.engine.connector.model.common.ITranslationTable;
import org.sentrysoftware.metricshub.engine.connector.model.common.ReferenceTranslationTable;
import org.sentrysoftware.metricshub.engine.connector.model.common.TranslationTable;

public class TranslationTableDeserializer extends JsonDeserializer<ITranslationTable> {

	@Override
	public ITranslationTable deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		if (parser == null) {
			return null;
		}

		final String key = parser.getCurrentName();
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
