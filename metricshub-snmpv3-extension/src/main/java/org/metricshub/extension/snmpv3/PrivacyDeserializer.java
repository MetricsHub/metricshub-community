package org.metricshub.extension.snmpv3;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub SNMP V3 Extension
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
import java.io.IOException;
import org.metricshub.extension.snmpv3.SnmpV3Configuration.Privacy;

/**
 * Custom deserializer for converting privacy values from JSON during deserialization.
 * It maps the JSON value to the corresponding  {@link Privacy} enum.
 */
public class PrivacyDeserializer extends JsonDeserializer<SnmpV3Configuration.Privacy> {

	/**
	 * Deserializes the privacy value from JSON and maps it to the {@link Privacy } enum.
	 *
	 * @param parser     JSON parser
	 * @param ctxt  Deserialization context
	 * @return The deserialized Privacy value
	 * @throws IOException If an I/O error occurs during deserialization
	 */
	@Override
	public SnmpV3Configuration.Privacy deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		if (parser == null) {
			return null;
		}

		String value = parser.getText();
		try {
			return SnmpV3Configuration.Privacy.interpretValueOf(value);
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage());
		}
	}
}
