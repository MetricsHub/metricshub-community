package org.metricshub.engine.connector.model.monitor.task.source;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TAB;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WHITE_SPACE;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.BooleanDeserializer;
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;
import org.metricshub.engine.connector.deserializer.custom.PositiveIntegerDeserializer;
import org.metricshub.engine.connector.deserializer.custom.TimeoutDeserializer;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source that executes an operating system command to retrieve data.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CommandLineSource extends Source {

	private static final String WHITE_SPACE_TAB = WHITE_SPACE + TAB;
	private static final long serialVersionUID = 1L;

	/**
	 * The command line to execute.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String commandLine;

	/**
	 * The timeout for executing the command.
	 */
	@JsonDeserialize(using = TimeoutDeserializer.class)
	private Long timeout;

	/**
	 * Flag indicating whether to execute the command locally.
	 */
	@JsonDeserialize(using = BooleanDeserializer.class)
	@JsonSetter(nulls = SKIP)
	private Boolean executeLocally = false;

	/**
	 * The pattern to exclude lines from the command output.
	 */
	private String exclude;
	/**
	 * The pattern to keep lines from the command output.
	 */
	private String keep;

	/**
	 * The line number to begin extracting output from.
	 */
	@JsonDeserialize(using = PositiveIntegerDeserializer.class)
	private Integer beginAtLineNumber;

	/**
	 * The line number to end extracting output at.
	 */
	@JsonDeserialize(using = PositiveIntegerDeserializer.class)
	private Integer endAtLineNumber;

	/**
	 * The separators for columns in the command output.
	 */
	private String separators = WHITE_SPACE_TAB;
	/**
	 * The columns to select from the command output.
	 */
	private String selectColumns;

	/**
	 * Builder for creating instances of {@code CommandLineSource}.
	 *
	 * @param type                 The type of the source.
	 * @param computes             List of computations to be applied to the source.
	 * @param forceSerialization   Flag indicating whether to force serialization.
	 * @param commandLine          The command line to execute.
	 * @param timeout              The timeout for executing the command.
	 * @param executeLocally       Flag indicating whether to execute the command locally.
	 * @param exclude              The pattern to exclude lines from the command output.
	 * @param keep                 The pattern to keep lines from the command output.
	 * @param beginAtLineNumber    The line number to begin extracting output from.
	 * @param endAtLineNumber      The line number to end extracting output at.
	 * @param separators           The separators for columns in the command output.
	 * @param selectColumns        The columns to select from the command output.
	 * @param key                  The key associated with the source.
	 * @param executeForEachEntryOf The execution context for each entry of the source.
	 */
	@Builder
	@JsonCreator
	public CommandLineSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "commandLine", required = true) @NonNull String commandLine,
		@JsonProperty("timeout") Long timeout,
		@JsonProperty("executeLocally") Boolean executeLocally,
		@JsonProperty("exclude") String exclude,
		@JsonProperty("keep") String keep,
		@JsonProperty("beginAtLineNumber") Integer beginAtLineNumber,
		@JsonProperty("endAtLineNumber") Integer endAtLineNumber,
		@JsonProperty("separators") String separators,
		@JsonProperty("selectColumns") String selectColumns,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.commandLine = commandLine;
		this.timeout = timeout;
		this.executeLocally = executeLocally != null && executeLocally;
		this.exclude = exclude;
		this.keep = keep;
		this.beginAtLineNumber = beginAtLineNumber;
		this.endAtLineNumber = endAtLineNumber;
		this.separators = separators == null ? WHITE_SPACE_TAB : separators;
		this.selectColumns = selectColumns;
	}

	/**
	 * Copy the current instance
	 *
	 * @return new {@link CommandLineSource} instance
	 */
	public CommandLineSource copy() {
		return CommandLineSource
			.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.commandLine(commandLine)
			.executeLocally(executeLocally)
			.exclude(exclude)
			.keep(keep)
			.beginAtLineNumber(beginAtLineNumber)
			.endAtLineNumber(endAtLineNumber)
			.selectColumns(selectColumns)
			.separators(separators)
			.timeout(timeout)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		commandLine = updater.apply(commandLine);
		exclude = updater.apply(exclude);
		keep = updater.apply(keep);
		separators = updater.apply(separators);
		selectColumns = updater.apply(selectColumns);
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- commandLine=", commandLine);
		addNonNull(stringJoiner, "- timeout=", timeout);
		addNonNull(stringJoiner, "- executeLocally=", executeLocally);
		addNonNull(stringJoiner, "- exclude=", exclude);
		addNonNull(stringJoiner, "- keep=", keep);
		addNonNull(stringJoiner, "- beginAtLineNumber=", beginAtLineNumber);
		addNonNull(stringJoiner, "- endAtLineNumber=", endAtLineNumber);
		addNonNull(stringJoiner, "- separators=", separators);
		addNonNull(stringJoiner, "- selectColumns=", selectColumns);

		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
