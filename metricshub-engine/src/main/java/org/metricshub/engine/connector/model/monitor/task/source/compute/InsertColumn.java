package org.metricshub.engine.connector.model.monitor.task.source.compute;

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
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.strategy.source.compute.IComputeProcessor;

/**
 * Inserts a new column at a 1-based position in every row, holding a literal value or the content of another column
 * of the same row (<code>$n</code>). Replaces the idiom of an <code>append</code> or <code>prepend</code> whose value
 * contains <code>;</code>.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InsertColumn extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The 1-based position of the new column: 1 inserts before the first column, <em>size + 1</em> appends after
	 * the last one.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private Integer column;

	/**
	 * The value of the new column: a literal, where <code>$n</code> is replaced by the content of column
	 * <em>n</em> of the same row.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String value;

	/**
	 * Construct a new instance of InsertColumn.
	 *
	 * @param type   The type of the computation task.
	 * @param column The 1-based position of the new column.
	 * @param value  The value of the new column.
	 */
	@Builder
	@JsonCreator
	public InsertColumn(
		@JsonProperty("type") String type,
		@JsonProperty(value = "column", required = true) @NonNull Integer column,
		@JsonProperty(value = "value", required = true) @NonNull String value
	) {
		super(type);
		this.column = column;
		this.value = value;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		stringJoiner.add(super.toString());
		addNonNull(stringJoiner, "- column=", column);
		addNonNull(stringJoiner, "- value=", value);
		return stringJoiner.toString();
	}

	@Override
	public InsertColumn copy() {
		return InsertColumn.builder().type(type).column(column).value(value).build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		value = updater.apply(value);
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
