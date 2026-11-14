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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.strategy.source.compute.IComputeProcessor;

/**
 * Converts a text result into a table: each line becomes a row, split on <code>separator</code>. Lines can be
 * filtered with <code>keep</code> and <code>exclude</code>, and columns selected with <code>selectColumns</code>,
 * with the same semantics as on the <code>commandLine</code> source and the <code>awk</code> compute.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToTable extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The column separator of the text. Default <code>;</code>.
	 */
	private String separator = TABLE_SEP;

	/**
	 * Regular expression of the lines to keep.
	 */
	private String keep;

	/**
	 * Regular expression of the lines to exclude.
	 */
	private String exclude;

	/**
	 * The columns to keep, as on <code>commandLine</code> (e.g. <code>1,3-5</code>).
	 */
	private String selectColumns;

	/**
	 * Construct a new instance of ToTable.
	 *
	 * @param type          The type of the computation task.
	 * @param separator     The column separator, null for <code>;</code>.
	 * @param keep          Regular expression of the lines to keep.
	 * @param exclude       Regular expression of the lines to exclude.
	 * @param selectColumns The columns to keep.
	 */
	@Builder
	@JsonCreator
	public ToTable(
		@JsonProperty("type") String type,
		@JsonProperty("separator") String separator,
		@JsonProperty("keep") String keep,
		@JsonProperty("exclude") String exclude,
		@JsonProperty("selectColumns") String selectColumns
	) {
		super(type);
		this.separator = separator == null || separator.isEmpty() ? TABLE_SEP : separator;
		this.keep = keep;
		this.exclude = exclude;
		this.selectColumns = selectColumns;
	}

	@Override
	public ContentType getInputContentType() {
		return ContentType.TEXT;
	}

	@Override
	public ContentType getOutputContentType() {
		return ContentType.TABLE;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		stringJoiner.add(super.toString());
		addNonNull(stringJoiner, "- separator=", separator);
		addNonNull(stringJoiner, "- keep=", keep);
		addNonNull(stringJoiner, "- exclude=", exclude);
		addNonNull(stringJoiner, "- selectColumns=", selectColumns);
		return stringJoiner.toString();
	}

	@Override
	public ToTable copy() {
		return ToTable.builder()
			.type(type)
			.separator(separator)
			.keep(keep)
			.exclude(exclude)
			.selectColumns(selectColumns)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// A reference resolving to nothing must not leave an empty separator: back to the default
		final String updated = updater.apply(separator);
		separator = updated == null || updated.isEmpty() ? TABLE_SEP : updated;
		keep = updater.apply(keep);
		exclude = updater.apply(exclude);
		selectColumns = updater.apply(selectColumns);
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
