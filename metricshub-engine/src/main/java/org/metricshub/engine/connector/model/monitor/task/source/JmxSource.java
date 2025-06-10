package org.metricshub.engine.connector.model.monitor.task.source;

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
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a JMX source task that fetches metrics via JMX.
 * Supports a single MBean ("mbean") with its "attributes" and optional "keysAsAttributes".
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JmxSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * The MBean ObjectName pattern to query.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private String mbean;

	/**
	 * The list of attributes to fetch from the MBean.
	 */
	@JsonSetter(nulls = FAIL)
	private List<String> attributes;

	/**
	 * Optional list of key‐property names (e.g. "scope", "name") to include as extra columns.
	 */
	@JsonSetter(nulls = FAIL)
	private List<String> keysAsAttributes;

	@Builder
	@JsonCreator
	public JmxSource(
		@JsonProperty(value = "type") String type,
		@JsonProperty(value = "computes") List<Compute> computes,
		@JsonProperty(value = "forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "mbean") String mbean,
		@JsonProperty(value = "attributes") List<String> attributes,
		@JsonProperty(value = "keysAsAttributes") List<String> keysAsAttributes,
		@JsonProperty(value = "key") String key,
		@JsonProperty(value = "executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		if (mbean == null || mbean.isBlank()) {
			throw new IllegalArgumentException("A JmxSource must specify a non‐blank 'mbean' field");
		}
		this.mbean = mbean;

		@SuppressWarnings("unchecked")
		List<String> attrs = (attributes != null) ? attributes : List.of();
		this.attributes = attrs;

		@SuppressWarnings("unchecked")
		List<String> keys = (keysAsAttributes != null) ? keysAsAttributes : List.of();
		this.keysAsAttributes = keys;
	}

	@Override
	public JmxSource copy() {
		return JmxSource
			.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? List.copyOf(getComputes()) : null)
			.mbean(mbean)
			.attributes(List.copyOf(attributes))
			.keysAsAttributes(keysAsAttributes != null ? List.copyOf(keysAsAttributes) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		this.mbean = updater.apply(this.mbean);
		// attributes and keysAsAttributes are literals; no update needed
	}

	@Override
	public String toString() {
		StringJoiner sj = new StringJoiner(MetricsHubConstants.NEW_LINE);
		sj.add(super.toString());

		addNonNull(sj, "- mbean=", mbean);
		addNonNull(sj, "- attributes=", attributes.toString());
		if (keysAsAttributes != null && !keysAsAttributes.isEmpty()) {
			addNonNull(sj, "- keysAsAttributes=", keysAsAttributes.toString());
		}
		return sj.toString();
	}

	@Override
	public SourceTable accept(ISourceProcessor sourceProcessor) {
		try {
			return sourceProcessor.process(this);
		} catch (Exception e) {
			throw new RuntimeException("Error processing JmxSource: " + e.getMessage(), e);
		}
	}
}
