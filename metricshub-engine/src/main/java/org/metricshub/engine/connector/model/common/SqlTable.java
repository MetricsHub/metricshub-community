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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Represents a sql table used for executing SQL queries.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlTable implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * The name of the source.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String source;

	/**
	 * The source alias.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String alias;

	/**
	 * The source columns.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private List<SqlColumn> columns;

	/**
	 * Creates a copy of the current SQL table.
	 *
	 * @return A new instance of {@link SqlTable} with the same source, alias and columns.
	 */
	public SqlTable copy() {
		return SqlTable
			.builder()
			.source(source)
			.alias(alias)
			.columns(columns.stream().map(SqlColumn::copy).collect(Collectors.toCollection(ArrayList::new)))
			.build();
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		addNonNull(stringJoiner, "Source ", source);
		addNonNull(stringJoiner, " AS ", alias);
		if (columns != null && !columns.isEmpty()) {
			stringJoiner.add(" Columns:");
			columns.stream().forEach(column -> stringJoiner.add(" - " + column.toString()));
		}

		return stringJoiner.toString();
	}
}
