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
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a JMX source task that fetches metrics via JMX.
 * Supports a single MBean ("mbean") with its "attributes" and optional "keyProperties".
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JmxSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * The ObjectName pattern to query.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String objectName;

	/**
	 * The list of attributes to fetch from the MBean.
	 */
	@JsonSetter(nulls = SKIP)
	private List<String> attributes = new ArrayList<>();

	/**
	 * Optional list of key‐property names (e.g. "scope", "name") to include as extra columns.
	 */
	@JsonSetter(nulls = SKIP)
	private List<String> keyProperties = new ArrayList<>();

	@Builder
	@JsonCreator
	public JmxSource(
		@JsonProperty(value = "type") String type,
		@JsonProperty(value = "computes") List<Compute> computes,
		@JsonProperty(value = "forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "objectName") @NonNull String objectName,
		@JsonProperty(value = "attributes") List<String> attributes,
		@JsonProperty(value = "keyProperties") List<String> keyProperties,
		@JsonProperty(value = "key") String key,
		@JsonProperty(value = "executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.objectName = objectName;
		this.attributes = (attributes != null) ? attributes : new ArrayList<>();
		this.keyProperties = (keyProperties != null) ? keyProperties : new ArrayList<>();

		if (this.attributes.isEmpty() && this.keyProperties.isEmpty()) {
			throw new IllegalArgumentException("At least one attribute or key property must be specified for JMX source.");
		}
	}

	@Override
	public JmxSource copy() {
		final List<Compute> computesCopy = getComputes() != null ? new ArrayList<>(getComputes()) : null;
		final List<String> attributesCopy = attributes != null ? new ArrayList<>(attributes) : null;
		final List<String> keyPropertiesCopy = keyProperties != null ? new ArrayList<>(keyProperties) : null;
		final ExecuteForEachEntryOf executeForEachEntryOfCopy = executeForEachEntryOf != null
			? executeForEachEntryOf.copy()
			: null;

		return JmxSource
			.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(computesCopy)
			.objectName(objectName)
			.attributes(attributesCopy)
			.keyProperties(keyPropertiesCopy)
			.executeForEachEntryOf(executeForEachEntryOfCopy)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		this.objectName = updater.apply(this.objectName);
		this.attributes = this.attributes.stream().map(updater).collect(Collectors.toCollection(ArrayList::new));
		this.keyProperties = this.keyProperties.stream().map(updater).collect(Collectors.toCollection(ArrayList::new));
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(MetricsHubConstants.NEW_LINE);
		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- objectName=", objectName);
		addNonNull(stringJoiner, "- attributes=", attributes);
		addNonNull(stringJoiner, "- keyProperties=", keyProperties);

		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
