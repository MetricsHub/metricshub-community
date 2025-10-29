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
 * Represents an Awk computation task for monitoring.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Awk extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The AWK script to be executed for the computation task.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String script;

	/**
	 * The exclude parameter for the AWK task.
	 */
	private String exclude;
	/**
	 * The keep parameter for the AWK task.
	 */
	private String keep;
	/**
	 * The separators parameter for the AWK task.
	 */
	private String separators;
	/**
	 * The selectColumns parameter for the AWK task.
	 */
	private String selectColumns;

	/**
	 * Construct a new instance of Awk.
	 *
	 * @param type          The type of the computation task.
	 * @param script        The AWK script to be executed.
	 * @param exclude       The exclude parameter for the AWK task.
	 * @param keep          The keep parameter for the AWK task.
	 * @param separators    The separators parameter for the AWK task.
	 * @param selectColumns The selectColumns parameter for the AWK task.
	 */
	@Builder
	@JsonCreator
	public Awk(
		@JsonProperty("type") String type,
		@JsonProperty(value = "script", required = true) @NonNull String script,
		@JsonProperty("exclude") String exclude,
		@JsonProperty("keep") String keep,
		@JsonProperty("separators") String separators,
		@JsonProperty("selectColumns") String selectColumns
	) {
		super(type);
		this.script = script;
		this.exclude = exclude;
		this.keep = keep;
		this.separators = separators;
		this.selectColumns = selectColumns;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- script=", script);
		addNonNull(stringJoiner, "- exclude=", exclude);
		addNonNull(stringJoiner, "- keep=", keep);
		addNonNull(stringJoiner, "- separators=", separators);
		addNonNull(stringJoiner, "- selectColumns=", selectColumns);

		return stringJoiner.toString();
	}

	@Override
	public Awk copy() {
		return Awk
			.builder()
			.type(type)
			.script(script)
			.exclude(exclude)
			.keep(keep)
			.separators(separators)
			.selectColumns(selectColumns)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		script = updater.apply(script);
		exclude = updater.apply(exclude);
		keep = updater.apply(keep);
		separators = updater.apply(separators);
		selectColumns = updater.apply(selectColumns);
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
