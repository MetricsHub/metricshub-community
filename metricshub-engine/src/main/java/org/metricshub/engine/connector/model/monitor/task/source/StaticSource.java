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
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.StaticValueDeserializer;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source task with a static value.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StaticSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * The static value associated with the source, in its legacy string form: a source reference, or a table written
	 * as <code>;</code> CSV text. Null when the value is written as a list (see {@link #rows}).
	 */
	@JsonSetter(nulls = FAIL)
	private String value;

	/**
	 * The rows of the source, when the value is written as a YAML list: exactly these rows and cells. A list of
	 * cells is one row, <code>[ "", "" ]</code> being a row of two empty cells; a list of lists is one row per
	 * list; an empty list is an empty table. Null in the string form.
	 */
	private List<List<String>> rows;

	/**
	 * Constructs a new {@code StaticSource} instance with the provided attributes.
	 *
	 * @param type                  the type of the source
	 * @param computes              the list of compute operations to be applied
	 * @param forceSerialization    flag indicating whether serialization should be forced
	 * @param value                 the static value associated with the source: a {@link String} (source reference or
	 *                              <code>;</code> CSV text, legacy form), a {@link List} of cells (one row), or a
	 *                              {@link List} of rows, each a {@link List} of cells
	 * @param key                   the key associated with the source
	 * @param executeForEachEntryOf the execute-for-each-entry-of information
	 */
	@Builder
	@JsonCreator
	public StaticSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "value", required = true) @JsonDeserialize(
			using = StaticValueDeserializer.class
		) @NonNull Object value,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super("static", computes, forceSerialization, key, executeForEachEntryOf);
		if (value instanceof List<?> list) {
			// A list of cells is one row
			final List<?> rowList = list.isEmpty() || list.get(0) instanceof List ? list : List.of(list);
			this.rows = new ArrayList<>();
			for (final Object row : rowList) {
				this.rows.add(
					((List<?>) row).stream().map(String.class::cast).collect(Collectors.toCollection(ArrayList::new))
				);
			}
		} else {
			this.value = (String) value;
		}
	}

	/**
	 * Creates a new instance by copying the current instance.
	 *
	 * @return a new {@code StaticSource} instance
	 */
	public StaticSource copy() {
		return StaticSource.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.value(rows != null ? rows : value)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		value = updater.apply(value);
		if (rows != null) {
			rows.forEach(row -> row.replaceAll(updater));
		}
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- value=", value);
		addNonNull(stringJoiner, "- value=", rows);

		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
