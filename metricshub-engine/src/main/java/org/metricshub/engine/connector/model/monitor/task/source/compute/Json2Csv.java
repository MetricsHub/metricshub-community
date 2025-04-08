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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
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
import org.metricshub.engine.strategy.source.compute.IComputeProcessor;

/**
 * Represents a Json2Csv computation task for monitoring.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Json2Csv extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The entry key for Json2Csv computation.
	 */
	@JsonSetter(nulls = SKIP)
	private String entryKey = "/";

	/**
	 * The properties for Json2Csv computation.
	 */
	private String properties;
	/**
	 * The separator for Json2Csv computation.
	 */
	private String separator;

	/**
	 * Construct a new instance of Json2Csv.
	 *
	 * @param type      The type of the computation task.
	 * @param entryKey  The entry key for Json2Csv computation.
	 * @param properties The properties for Json2Csv computation.
	 * @param separator The separator for Json2Csv computation.
	 */
	@Builder
	@JsonCreator
	public Json2Csv(
		@JsonProperty("type") String type,
		@JsonProperty("entryKey") String entryKey,
		@JsonProperty("properties") String properties,
		@JsonProperty("separator") String separator
	) {
		super(type);
		this.entryKey = entryKey == null ? "/" : entryKey;
		this.properties = properties;
		this.separator = separator == null ? TABLE_SEP : separator;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- entryKey=", entryKey);
		addNonNull(stringJoiner, "- properties=", properties);
		addNonNull(stringJoiner, "- separator=", separator);

		return stringJoiner.toString();
	}

	@Override
	public Json2Csv copy() {
		return Json2Csv.builder().type(type).entryKey(entryKey).properties(properties).separator(separator).build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		entryKey = updater.apply(entryKey);
		separator = updater.apply(separator);
		properties = updater.apply(properties);
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
