package org.metricshub.engine.connector.deserializer.custom;

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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Custom deserializer for converting boolean values from various representations to Java Boolean objects.
 * Supports mapping of strings to boolean values and handles case-insensitive comparison.
 */
public class BooleanDeserializer extends JsonDeserializer<Boolean> {

	private static final Map<String, Boolean> BOOLEAN_MAP = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	static {
		BOOLEAN_MAP.put("0", Boolean.FALSE);
		BOOLEAN_MAP.put("1", Boolean.TRUE);
		BOOLEAN_MAP.put("true", Boolean.TRUE);
		BOOLEAN_MAP.put("false", Boolean.FALSE);
	}

	@Override
	public Boolean deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
		if (jsonParser == null) {
			return false;
		}

		final String key = jsonParser.currentName();

		final String str = jsonParser.getValueAsString();
		if (str == null) {
			return false;
		}
		Boolean booleanValue = BOOLEAN_MAP.get(str);
		if (booleanValue == null) {
			throw new InvalidFormatException(
				jsonParser,
				String.format("Invalid boolean value encountered for property '%s'. Value:%s", key, str),
				str,
				Boolean.class
			);
		}
		return booleanValue;
	}
}
