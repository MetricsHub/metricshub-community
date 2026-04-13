package org.metricshub.engine.connector.model.monitor.task.source;

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

import static com.fasterxml.jackson.annotation.Nulls.FAIL;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;
import org.metricshub.engine.connector.deserializer.custom.SqlTableDeserializer;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.common.SqlTable;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source that performs a SQL operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
public class InternalDbQuerySource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * The list of tables to be used in the SQL query.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = SqlTableDeserializer.class)
	private List<SqlTable> tables = new ArrayList<>();

	/**
	 * The query to execute.
	 */
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String query;

	/**
	 * Builder for creating instances of {@code InternalDbQuerySource}.
	 *
	 * @param type                  The type of the source.
	 * @param computes              List of computations to be applied to the source.
	 * @param forceSerialization    Flag indicating whether to force serialization.
	 * @param key                   The key associated with the source.
	 * @param executeForEachEntryOf The execution context for each entry of the source.
	 * @param tables                The list of tables to be used in the SQL query.
	 * @param query                 The query to execute.
	 */
	@Builder
	@JsonCreator
	public InternalDbQuerySource(
		@JsonProperty(value = "type") String type,
		@JsonProperty(value = "computes") List<Compute> computes,
		@JsonProperty(value = "forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "key") String key,
		@JsonProperty(value = "executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf,
		@JsonProperty(value = "tables", required = true) @NonNull List<SqlTable> tables,
		@JsonProperty(value = "query", required = true) @NonNull String query
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.tables = tables;
		this.query = query;
	}

	@Override
	public InternalDbQuerySource copy() {
		return InternalDbQuerySource
			.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.tables(
				tables != null ? tables.stream().map(SqlTable::copy).collect(Collectors.toCollection(ArrayList::new)) : null
			)
			.query(query)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// For now, there is nothing to update
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		if (tables != null && !tables.isEmpty()) {
			final String tablesString = tables.stream().map(SqlTable::toString).collect(Collectors.joining(NEW_LINE));
			addNonNull(stringJoiner, "- tables=", tablesString);
		}

		addNonNull(stringJoiner, "- query=", query);

		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
