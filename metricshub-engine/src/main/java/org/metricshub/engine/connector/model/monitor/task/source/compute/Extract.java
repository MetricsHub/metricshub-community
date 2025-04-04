package org.metricshub.engine.connector.model.monitor.task.source.compute;

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
 * Represents an Extract computation task for monitoring.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Extract extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The main column from which to extract values.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private Integer column;

	/**
	 * The sub-column (position) from which to extract values within the main column.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private Integer subColumn;

	/**
	 * The sub-separators used to identify sub-columns within the main column.
	 */
	private String subSeparators;

	/**
	 * Construct a new instance of Extract.
	 *
	 * @param type         The type of the computation task.
	 * @param column       The main column from which to extract values.
	 * @param subColumn    The sub-column (position) from which to extract values within the main column.
	 * @param subSeparators The sub-separators used to identify sub-columns within the main column.
	 */
	@Builder
	@JsonCreator
	public Extract(
		@JsonProperty("type") String type,
		@JsonProperty(value = "column", required = true) @NonNull Integer column,
		@JsonProperty(value = "subColumn", required = true) @NonNull Integer subColumn,
		@JsonProperty("subSeparators") String subSeparators
	) {
		super(type);
		this.column = column;
		this.subColumn = subColumn;
		this.subSeparators = subSeparators;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- column=", column);
		addNonNull(stringJoiner, "- subColumn=", subColumn);
		addNonNull(stringJoiner, "- subSeparators=", subSeparators);

		return stringJoiner.toString();
	}

	@Override
	public Extract copy() {
		return Extract.builder().type(type).column(column).subColumn(subColumn).subSeparators(subSeparators).build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		subSeparators = updater.apply(subSeparators);
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
