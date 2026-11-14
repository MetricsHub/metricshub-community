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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
	 * Construct a new instance of ToText.
	 *
	 * @param type The type of the computation task.
	 */
	@Builder
	@JsonCreator
	public ToText(@JsonProperty("type") String type) {
		super(type);
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
		return super.toString();
	}

	@Override
	public ToText copy() {
		return ToText.builder().type(type).build();
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
