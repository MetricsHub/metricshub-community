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
import java.io.IOException;
import org.metricshub.engine.connector.model.monitor.task.source.FileSourceProcessingMode;

/**
 * Custom deserializer for converting FileSourceMode values from JSON during deserialization.
 * It maps the JSON value to the corresponding {@link FileSourceProcessingMode} enum with case-insensitive matching.
 */
public class FileSourceModeDeserializer extends JsonDeserializer<FileSourceProcessingMode> {

	/**
	 * Deserializes the FileSourceMode value from JSON and maps it to the {@link FileSourceProcessingMode} enum.
	 *
	 * @param parser JSON parser
	 * @param ctxt   Deserialization context
	 * @return The deserialized {@link FileSourceProcessingMode} value
	 * @throws IOException If an I/O error occurs during deserialization
	 */
	@Override
	public FileSourceProcessingMode deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		if (parser == null) {
			return null;
		}

		try {
			return FileSourceProcessingMode.interpretValueOf(parser.getValueAsString());
		} catch (IllegalArgumentException e) {
			throw new IOException(e.getMessage());
		}
	}
}
