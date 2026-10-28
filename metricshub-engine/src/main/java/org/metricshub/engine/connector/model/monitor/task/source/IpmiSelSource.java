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
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * A source that returns the System Event Log (SEL) entries a BMC added since the previous poll, over IPMI.
 * The first poll returns no entry: it only sets the cursor.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IpmiSelSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * Default maximum number of entries returned by one poll
	 */
	public static final int DEFAULT_MAX_EVENTS_PER_POLL = 50;

	/**
	 * No limit on the entries returned by one poll
	 */
	public static final int UNLIMITED_EVENTS_PER_POLL = -1;

	@JsonSetter(nulls = SKIP)
	private int maxEventsPerPoll = DEFAULT_MAX_EVENTS_PER_POLL;

	/**
	 * Constructs a new {@code IpmiSelSource} instance with the provided attributes.
	 *
	 * @param type                  the type of the source
	 * @param computes              the list of compute operations to be applied
	 * @param forceSerialization    ignored: the IPMI sources are always serialized
	 * @param maxEventsPerPoll      the maximum number of entries returned by one poll; 0 for the default, negative for no limit
	 * @param key                   the key associated with the source
	 * @param executeForEachEntryOf the execute-for-each-entry-of information
	 */
	@Builder
	@JsonCreator
	public IpmiSelSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty("maxEventsPerPoll") int maxEventsPerPoll,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		if (maxEventsPerPoll == 0) {
			this.maxEventsPerPoll = DEFAULT_MAX_EVENTS_PER_POLL;
		} else {
			this.maxEventsPerPoll = maxEventsPerPoll > 0 ? maxEventsPerPoll : UNLIMITED_EVENTS_PER_POLL;
		}
	}

	/**
	 * Always {@code true}: a BMC accepts few simultaneous sessions, so the IPMI requests of a connector to a host
	 * are serialized whatever the connector says.
	 */
	@Override
	public boolean isForceSerialization() {
		return true;
	}

	public IpmiSelSource copy() {
		return IpmiSelSource.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.maxEventsPerPoll(maxEventsPerPoll)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		// Nothing to update
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		stringJoiner.add(super.toString());
		addNonNull(stringJoiner, "- maxEventsPerPoll=", maxEventsPerPoll);
		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
