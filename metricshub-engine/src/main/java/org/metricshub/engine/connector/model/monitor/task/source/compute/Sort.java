package org.metricshub.engine.connector.model.monitor.task.source.compute;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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
 * Represents a Sort computation task, which sorts the lines of the table on a column.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Sort extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The column to sort on.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private Integer column;

	/**
	 * Whether the values are compared as numbers rather than alphabetically.
	 */
	private boolean numeric;

	/**
	 * Whether the alphabetical comparison ignores the case.
	 */
	private boolean ignoreCase;

	/**
	 * Whether the lines are sorted in descending order.
	 */
	private boolean descending;

	/**
	 * Construct a new instance of Sort.
	 *
	 * @param type       The type of the computation task.
	 * @param column     The column to sort on.
	 * @param numeric    Whether the values are compared as numbers.
	 * @param ignoreCase Whether the alphabetical comparison ignores the case.
	 * @param descending Whether the lines are sorted in descending order.
	 */
	@Builder
	@JsonCreator
	public Sort(
		@JsonProperty("type") String type,
		@JsonProperty(value = "column", required = true) @NonNull Integer column,
		@JsonProperty("numeric") boolean numeric,
		@JsonProperty("ignoreCase") boolean ignoreCase,
		@JsonProperty("descending") boolean descending
	) {
		super(type);
		this.column = column;
		this.numeric = numeric;
		this.ignoreCase = ignoreCase;
		this.descending = descending;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- column=", column);
		addNonNull(stringJoiner, "- numeric=", numeric);
		addNonNull(stringJoiner, "- ignoreCase=", ignoreCase);
		addNonNull(stringJoiner, "- descending=", descending);

		return stringJoiner.toString();
	}

	@Override
	public Sort copy() {
		return Sort.builder()
			.type(type)
			.column(column)
			.numeric(numeric)
			.ignoreCase(ignoreCase)
			.descending(descending)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// Not implemented because this class doesn't define any string member
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
