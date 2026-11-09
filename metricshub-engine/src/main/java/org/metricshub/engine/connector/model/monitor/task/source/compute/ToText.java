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
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * Converts a table into text: each row becomes a line whose cells are joined with <code>;</code> and followed by a
 * trailing <code>;</code>, a <code>;</code> inside a cell being replaced by <code>,</code>. This is the
 * serialization the <code>awk</code> compute has always applied to a table, so existing scripts keep working.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToText extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * Whether the compute was inserted by the content-type desugaring step rather than written in the connector. It
	 * converts like a written one; a later pass of the step removes it before desugaring the chain as written again.
	 * Never set from YAML.
	 */
	@JsonIgnore
	@EqualsAndHashCode.Exclude
	private boolean generated;

	/**
	 * Construct a new instance of ToText.
	 *
	 * @param type The type of the computation task.
	 */
	@Builder
	@JsonCreator
	public ToText(@JsonProperty("type") String type) {
		super(type);
	}

	/**
	 * A <code>toText</code> inserted by the content-type desugaring step: it converts the table like a written one,
	 * and a later pass of the step removes it.
	 *
	 * @return a new generated instance
	 */
	public static ToText generated() {
		final ToText toText = ToText.builder().type("toText").build();
		toText.setGenerated(true);
		return toText;
	}

	@Override
	public ContentType getInputContentType() {
		return ContentType.TABLE;
	}

	@Override
	public ContentType getOutputContentType() {
		return ContentType.TEXT;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		stringJoiner.add(super.toString());
		addNonNull(stringJoiner, "- generated=", generated ? Boolean.TRUE : null);
		return stringJoiner.toString();
	}

	@Override
	public ToText copy() {
		final ToText copy = ToText.builder().type(type).build();
		copy.setGenerated(generated);
		return copy;
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// Nothing to update
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
