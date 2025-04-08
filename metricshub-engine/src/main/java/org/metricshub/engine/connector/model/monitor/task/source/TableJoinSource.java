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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
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
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;
import org.metricshub.engine.connector.deserializer.custom.PositiveIntegerDeserializer;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * Represents a source that performs a join operation on two tables.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TableJoinSource extends Source {

	private static final long serialVersionUID = 1L;

	/**
	 * The name of the left table to join.
	 */
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String leftTable;

	/**
	 * The name of the right table to join.
	 */
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String rightTable;

	/**
	 * The column index from the left table to use as the key for the join.
	 */
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = PositiveIntegerDeserializer.class)
	private Integer leftKeyColumn;

	/**
	 * The column index from the right table to use as the key for the join.
	 */
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = PositiveIntegerDeserializer.class)
	private Integer rightKeyColumn;

	/**
	 * The default line for right table entries when there is no match.
	 */
	private String defaultRightLine;
	/**
	 * The type of key used for the join.
	 */
	private String keyType;

	/**
	 * Builder for creating instances of {@code TableJoinSource}.
	 *
	 * @param type                 The type of the source.
	 * @param computes             List of computations to be applied to the source.
	 * @param forceSerialization   Flag indicating whether to force serialization.
	 * @param leftTable            The name of the left table to join.
	 * @param rightTable           The name of the right table to join.
	 * @param leftKeyColumn        The column index from the left table to use as the key for the join.
	 * @param rightKeyColumn       The column index from the right table to use as the key for the join.
	 * @param defaultRightLine     The default line for right table entries when there is no match.
	 * @param keyType              The type of key used for the join.
	 * @param key                  The key associated with the source.
	 * @param executeForEachEntryOf The execution context for each entry of the source.
	 */
	@Builder
	@JsonCreator
	public TableJoinSource(
		@JsonProperty("type") String type,
		@JsonProperty("computes") List<Compute> computes,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "leftTable", required = true) String leftTable,
		@JsonProperty(value = "rightTable", required = true) String rightTable,
		@JsonProperty(value = "leftKeyColumn", required = true) Integer leftKeyColumn,
		@JsonProperty(value = "rightKeyColumn", required = true) Integer rightKeyColumn,
		@JsonProperty("defaultRightLine") String defaultRightLine,
		@JsonProperty("keyType") String keyType,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		this.leftTable = leftTable;
		this.rightTable = rightTable;
		this.leftKeyColumn = leftKeyColumn;
		this.rightKeyColumn = rightKeyColumn;
		this.defaultRightLine = defaultRightLine;
		this.keyType = keyType;
	}

	@Override
	public TableJoinSource copy() {
		return TableJoinSource
			.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.leftTable(leftTable)
			.rightTable(rightTable)
			.leftKeyColumn(leftKeyColumn)
			.rightKeyColumn(rightKeyColumn)
			.defaultRightLine(defaultRightLine)
			.keyType(keyType)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		leftTable = updater.apply(leftTable);
		rightTable = updater.apply(rightTable);
		keyType = updater.apply(keyType);
		defaultRightLine = updater.apply(defaultRightLine);
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- leftTable=", leftTable);
		addNonNull(stringJoiner, "- rightTable=", rightTable);
		addNonNull(stringJoiner, "- leftKeyColumn=", leftKeyColumn);
		addNonNull(stringJoiner, "- rightKeyColumn=", rightKeyColumn);
		addNonNull(stringJoiner, "- defaultRightLine=", defaultRightLine);
		addNonNull(stringJoiner, "- keyType=", keyType);

		return stringJoiner.toString();
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
