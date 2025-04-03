package org.metricshub.extension.snmpv3;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub SNMP V3 Extension
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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import org.metricshub.extension.snmpv3.SnmpV3Configuration.AuthType;

/**
 * Custom deserializer for converting authentication type values from JSON during deserialization.
 * It maps the JSON value to the corresponding {@link AuthType} enum.
 */
public class AuthTypeDeserializer extends JsonDeserializer<AuthType> {

	/**
	 * Deserializes the authentication type value from JSON and maps it to
	 * {@link AuthType } enum.
	 *
	 * @param parser JSON parser
	 * @param ctxt   Deserialization context
	 * @return The deserialized AuthType value
	 * @throws IOException If an I/O error occurs during deserialization
	 */
	@Override
	public SnmpV3Configuration.AuthType deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		if (parser == null) {
			return null;
		}

		try {
			return AuthType.interpretValueOf(parser.getValueAsString());
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage());
		}
	}
}
