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
 * Represents a Substring computation task for monitoring.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Substring extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The column index used in the Substring computation.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private Integer column;

	/**
	 * The starting position for the Substring operation.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String start;

	/**
	 * The length of the substring to extract.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String length;

	/**
	 * Construct a new instance of Substring.
	 *
	 * @param type   The type of the computation task.
	 * @param column The column index used in the computation.
	 * @param start  The starting position for the Substring operation.
	 * @param length The length of the substring to extract.
	 */
	@Builder
	@JsonCreator
	public Substring(
		@JsonProperty("type") String type,
		@JsonProperty(value = "column", required = true) @NonNull Integer column,
		@JsonProperty(value = "start", required = true) @NonNull String start,
		@JsonProperty(value = "length", required = true) @NonNull String length
	) {
		super(type);
		this.column = column;
		this.start = start;
		this.length = length;
	}

	/**
	 * Copy the current instance
	 *
	 * @return new {@link Substring} instance
	 */
	public Substring copy() {
		return Substring.builder().type(type).column(column).start(start).length(length).build();
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- column=", column);
		addNonNull(stringJoiner, "- start=", start);
		addNonNull(stringJoiner, "- length=", length);

		return stringJoiner.toString();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		start = updater.apply(start);
		length = updater.apply(length);
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
