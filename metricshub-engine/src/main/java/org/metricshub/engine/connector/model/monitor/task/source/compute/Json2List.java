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
 * Turns a JSON document into a table of two columns, the path of each leaf value in the JFlat syntax that the
 * <code>json2Csv</code> properties use (<code>/data/items[0]/name</code>) and the value, whole. One row per leaf
 * value, in document order.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Json2List extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * Construct a new instance of Json2List.
	 *
	 * @param type The type of the computation task.
	 */
	@Builder
	@JsonCreator
	public Json2List(@JsonProperty("type") String type) {
		super(type);
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
		return super.toString();
	}

	@Override
	public Json2List copy() {
		return Json2List.builder().type(type).build();
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
