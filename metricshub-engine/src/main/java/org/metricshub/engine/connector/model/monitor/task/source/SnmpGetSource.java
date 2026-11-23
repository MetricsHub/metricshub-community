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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source task that retrieves data using SNMP GET. Its result is the value of the OID, as a text, or with
 * <code>output: table</code> as a table of one row of one cell.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SnmpGetSource extends SnmpSource {

	private static final long serialVersionUID = 1L;

	/**
	 * What the source produces: {@link ContentType#TEXT} (the value, as it is) or {@link ContentType#TABLE} (one row of
	 * one cell holding the value, never split). Default text.
	 */
	@JsonSetter(nulls = SKIP)
	private ContentType output = ContentType.TEXT;

	/**
	 * Constructs a new {@code SnmpGetSource} instance with the provided attributes.
	 *
	 * @param type                  the type of the source
	 * @param computes              the list of compute operations to be applied
	 * @param forceSerialization    flag indicating whether serialization should be forced
	 * @param oid                   the SNMP OID to retrieve data
	 * @param key                   the key associated with the source
	 * @param executeForEachEntryOf the execute-for-each-entry-of information
	 * @param output                what the source produces, null for text
	 */
	@Builder
	@JsonCreator
	public SnmpGetSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "oid", required = true) String oid,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf,
		@JsonProperty("output") ContentType output
	) {
		super(type, computes, forceSerialization, oid, key, executeForEachEntryOf);
		this.output = output == null ? ContentType.TEXT : output;
	}

	/**
	 * Copy the current instance
	 *
	 * @return new {@link SnmpGetSource} instance
	 */
	public SnmpGetSource copy() {
		return SnmpGetSource.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.oid(oid)
			.output(output)
			.build();
	}

	@Override
	public ContentType getOutputContentType() {
		return output == null ? ContentType.TEXT : output;
	}

	@Override
	public String toString() {
		return super.toString();
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
