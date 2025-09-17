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
 * Represents a Decode computation task for monitoring.
 * This compute is used to decode a column content through various decoding types.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Decode extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The column index used in the Decode computation.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private Integer column;

	/**
	 * The type of decoding to use.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String decoding;

	/**
	 * Construct a new instance of Decode.
	 *
	 * @param type   The type of the computation task.
	 * @param column The column index used in the computation.
	 * @param decoding The type of decoding to use.
	 */
	@Builder
	@JsonCreator
	public Decode(
		@JsonProperty("type") String type,
		@JsonProperty(value = "column", required = true) @NonNull Integer column,
		@JsonProperty(value = "decoding", required = true) @NonNull String decoding
	) {
		super(type);
		this.column = column;
		this.decoding = decoding;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- column=", column);
		addNonNull(stringJoiner, "- decoding=", decoding);

		return stringJoiner.toString();
	}

	@Override
	public Decode copy() {
		return Decode.builder().type(type).column(column).decoding(decoding).build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
