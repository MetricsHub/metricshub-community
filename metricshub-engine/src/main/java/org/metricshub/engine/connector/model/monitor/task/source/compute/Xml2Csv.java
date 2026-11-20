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
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.strategy.source.compute.IComputeProcessor;

/**
 * Represents an Xml2Csv computation task for monitoring.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Xml2Csv extends Compute {

	private static final long serialVersionUID = 1L;

	/**
	 * The record tag used in the Xml2Csv computation.
	 * Default value is "/" if not provided.
	 */
	@JsonSetter(nulls = SKIP)
	private String recordTag = "/";

	/**
	 * The properties, one per column. Written in the connector as a YAML list, each item one property. Null when the
	 * connector sets none. An item must not hold a <code>;</code>: XFlat takes the properties as one string, the items
	 * joined with <code>;</code>, removes the white space and the empty items, and splits it on <code>;</code>. An item
	 * is never null.
	 */
	@JsonSetter(contentNulls = FAIL)
	private List<String> properties;

	/**
	 * Construct a new instance of Xml2Csv.
	 *
	 * @param type       The type of the computation task.
	 * @param recordTag  The record tag used in the computation.
	 * @param properties The properties, null for none.
	 */
	@Builder
	@JsonCreator
	public Xml2Csv(
		@JsonProperty("type") final String type,
		@JsonProperty("recordTag") final String recordTag,
		@JsonProperty("properties") final List<String> properties
	) {
		super(type);
		this.recordTag = recordTag == null ? "/" : recordTag;
		this.properties = properties == null ? null : new ArrayList<>(properties);
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

		addNonNull(stringJoiner, "- recordTag=", recordTag);
		addNonNull(stringJoiner, "- properties=", properties);

		return stringJoiner.toString();
	}

	@Override
	public Xml2Csv copy() {
		return Xml2Csv.builder().type(type).recordTag(recordTag).properties(properties).build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		recordTag = updater.apply(recordTag);
		if (properties != null) {
			properties.replaceAll(updater);
		}
	}

	@Override
	public void accept(IComputeProcessor computeProcessor) {
		computeProcessor.process(this);
	}
}
