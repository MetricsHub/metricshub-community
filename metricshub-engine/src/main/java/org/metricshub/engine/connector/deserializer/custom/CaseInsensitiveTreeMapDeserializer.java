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

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.TreeMap;

/**
 * Custom deserializer for converting JSON objects with string keys to a case-insensitive TreeMap.
 * The deserializer enforces that the keys are non-empty strings and creates a TreeMap with a case-insensitive
 * comparator for case-insensitive key matching.
 */
public class CaseInsensitiveTreeMapDeserializer extends AbstractMapDeserializer<String> {

	@Override
	protected String messageOnInvalidMap(String nodeKey) {
		return String.format("The key referenced by '%s' cannot be empty.", nodeKey);
	}

	@Override
	protected Map<String, String> fromMap(Map<String, String> map) {
		final Map<String, String> treeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		treeMap.putAll(map);
		return treeMap;
	}

	@Override
	protected boolean isExpectedInstance(Map<String, String> map) {
		return map instanceof TreeMap;
	}

	@Override
	protected Map<String, String> emptyMap() {
		return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	}

	@Override
	protected boolean isValidMap(Map<String, String> map) {
		return map.keySet().stream().noneMatch(key -> key == null || key.isBlank());
	}

	@Override
	protected TypeReference<Map<String, String>> getTypeReference() {
		return new TypeReference<Map<String, String>>() {};
	}
}
