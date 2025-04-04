package org.metricshub.engine.connector.model.common;

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

import static com.fasterxml.jackson.annotation.Nulls.FAIL;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;

/**
 * Represents a sql table column used for executing SQL queries.
 */
@Slf4j
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlColumn implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * The name of the column in the SQL database.
	 */
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String name;

	/**
	 * The type of the column in the SQL database.
	 */
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String type;

	/**
	 * The column number in the source.
	 */
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private Integer number;

	/**
	 * Creates a copy of the current SQL column.
	 *
	 * @return A new instance of {@link SqlColumn} with the same name, type and number.
	 */
	public SqlColumn copy() {
		return SqlColumn.builder().name(name).type(type).number(number).build();
	}

	@Override
	public String toString() {
		final StringBuilder stringBuilder = new StringBuilder();

		stringBuilder.append("SQL Column: name=");
		stringBuilder.append(name);
		stringBuilder.append(", type=");
		stringBuilder.append(type);
		stringBuilder.append(", column number=");
		stringBuilder.append(number);

		return stringBuilder.toString();
	}

	/**
	 * Detects the {@link SqlColumn} using the value defined in the connector code.
	 * @param value The value to detect.
	 * @return The corresponding {@link SqlColumn} instance.
	 */
	public static SqlColumn detect(final JsonNode value) {
		final JsonNode nameNode = value.get("name");
		if (nameNode == null) {
			log.error("Malformed SQL Column, missing parameter 'name'.");
		}

		final JsonNode typeNode = value.get("type");
		if (typeNode == null) {
			log.error("Malformed SQL Column, missing parameter 'type'.");
		}

		final JsonNode numberNode = value.get("number");
		if (numberNode == null) {
			log.error("Malformed SQL Column, missing parameter 'number'.");
		}

		return SqlColumn.builder().name(nameNode.asText()).type(typeNode.asText()).number(numberNode.asInt()).build();
	}
}
