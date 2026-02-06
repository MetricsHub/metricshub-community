package org.metricshub.extension.bmchelix.shiftright;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Helix Enrichment Extension
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

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to map OpenTelemetry attributes to/from string maps.
 */
public class BmcHelixOtelAttributeMapper {

	/**
	 * Convert OpenTelemetry attributes to a string map.
	 *
	 * @param attributes the OpenTelemetry attributes
	 * @return string attribute map
	 */
	public Map<String, String> toMap(final List<KeyValue> attributes) {
		final Map<String, String> mappedAttributes = new HashMap<>();
		if (attributes == null) {
			return mappedAttributes;
		}

		for (KeyValue attribute : attributes) {
			mappedAttributes.put(attribute.getKey(), toStringValue(attribute.getValue()));
		}

		return mappedAttributes;
	}

	/**
	 * Convert a string map to OpenTelemetry attributes.
	 *
	 * @param attributes string attribute map
	 * @return OpenTelemetry key-value list
	 */
	List<KeyValue> toKeyValues(final Map<String, String> attributes) {
		if (attributes == null || attributes.isEmpty()) {
			return List.of();
		}

		return attributes
			.entrySet()
			.stream()
			.filter(entry -> entry.getKey() != null && entry.getValue() != null)
			.map(entry ->
				KeyValue
					.newBuilder()
					.setKey(entry.getKey())
					.setValue(AnyValue.newBuilder().setStringValue(entry.getValue()).build())
					.build()
			)
			.toList();
	}

	/**
	 * Convert an OpenTelemetry AnyValue to a string representation.
	 *
	 * @param value the value to convert
	 * @return string representation or null
	 */
	private String toStringValue(final AnyValue value) {
		if (value == null) {
			return null;
		}

		return switch (value.getValueCase()) {
			case STRING_VALUE -> value.getStringValue();
			case BOOL_VALUE -> String.valueOf(value.getBoolValue());
			case INT_VALUE -> String.valueOf(value.getIntValue());
			case DOUBLE_VALUE -> String.valueOf(value.getDoubleValue());
			default -> value.toString();
		};
	}
}
