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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.metricshub.engine.connector.deserializer.custom.EventLogLevelSetDeserializer;
import org.metricshub.engine.connector.deserializer.custom.LinkedHashSetDeserializer;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source that retrieves data from Windows Event Logs.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EventLogSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * Default max events per poll value
	 */
	private static final int DEFAULT_MAX_EVENTS_PER_POLL = 50;

	/**
	 * Unlimited events per poll
	 */
	public static final int UNLIMITED_EVENTS_PER_POLL = -1;

	@JsonSetter(nulls = SKIP)
	private String logName;

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = LinkedHashSetDeserializer.class)
	private Set<String> eventIds = new LinkedHashSet<>();

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = LinkedHashSetDeserializer.class)
	private Set<String> sources = new LinkedHashSet<>();

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = EventLogLevelSetDeserializer.class)
	private Set<EventLogLevel> levels = new LinkedHashSet<>();

	@JsonSetter(nulls = SKIP)
	private int maxEventsPerPoll = DEFAULT_MAX_EVENTS_PER_POLL;

	@Builder
	public EventLogSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty("logName") String logName,
		@JsonProperty("eventIds") Set<String> eventIds,
		@JsonProperty("sources") Set<String> sources,
		@JsonProperty("levels") Set<EventLogLevel> levels,
		@JsonProperty("maxEventsPerPoll") int maxEventsPerPoll,
		@JsonProperty("selectionRegex") String selectionRegex,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.logName = logName;
		this.eventIds = (eventIds != null) ? eventIds : new LinkedHashSet<>();
		this.sources = (sources != null) ? sources : new LinkedHashSet<>();
		this.levels = (levels != null) ? levels : new LinkedHashSet<>();

		if (maxEventsPerPoll == 0) {
			this.maxEventsPerPoll = DEFAULT_MAX_EVENTS_PER_POLL;
		} else {
			this.maxEventsPerPoll = maxEventsPerPoll > 0 ? maxEventsPerPoll : UNLIMITED_EVENTS_PER_POLL;
		}
	}

	@Override
	public Source copy() {
		return EventLogSource
			.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.logName(logName)
			.eventIds(eventIds != null ? eventIds : new LinkedHashSet<>())
			.sources(sources != null ? sources : new LinkedHashSet<>())
			.levels(levels != null ? new LinkedHashSet<>(levels) : new LinkedHashSet<>())
			.maxEventsPerPoll(maxEventsPerPoll)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		logName = updater.apply(logName);
		eventIds = eventIds.stream().map(updater).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
		sources = sources.stream().map(updater).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- logName=", logName);
		addNonNull(stringJoiner, "- eventIds=", eventIds);
		addNonNull(stringJoiner, "- sources=", sources);
		addNonNull(stringJoiner, "- levels=", levels);
		addNonNull(stringJoiner, "- maxEventsPerPoll=", maxEventsPerPoll);

		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
