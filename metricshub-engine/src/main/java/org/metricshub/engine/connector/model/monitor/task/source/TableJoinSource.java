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
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.deserializer.custom.PositiveIntegerDeserializer;
import org.metricshub.engine.connector.deserializer.custom.StringOrListDeserializer;
import org.metricshub.engine.connector.deserializer.custom.TableLiteralDeserializer;
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
	 * The left table to join, in its string form: a source reference, or a legacy table literal written as
	 * <code>;</code> CSV text. Null when the table is written as a list of rows (see {@link #leftTableRows}).
	 */
	@JsonSetter(nulls = FAIL)
	private String leftTable;

	/**
	 * The rows of the left table to join, when it is written as a table literal, a YAML list of rows each a list of
	 * cells: exactly these rows and cells. Null in the string form.
	 */
	private List<List<String>> leftTableRows;

	/**
	 * The right table to join, in its string form: a source reference, or a legacy table literal written as
	 * <code>;</code> CSV text. Null when the table is written as a list of rows (see {@link #rightTableRows}).
	 */
	@JsonSetter(nulls = FAIL)
	private String rightTable;

	/**
	 * The rows of the right table to join, when it is written as a table literal: exactly these rows and cells. Null
	 * in the string form.
	 */
	private List<List<String>> rightTableRows;

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
	 * The default line for right table entries when there is no match, in its legacy string form: split on
	 * <code>;</code> when the join runs. Null when the line is written as a list (see {@link #defaultRightLineCells}).
	 */
	private String defaultRightLine;

	/**
	 * The cells of the default line for right table entries when there is no match, when the line is written as a
	 * YAML list: exactly these cells, <code>[ "", "" ]</code> being two empty cells. Null in the string form.
	 */
	private List<String> defaultRightLineCells;

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
	 * @param leftTable            The left table to join: a {@link String} (source reference or <code>;</code> CSV
	 *                             literal) or a {@link List} of rows, each a {@link List} of cells.
	 * @param rightTable           The right table to join, in the same forms as the left table.
	 * @param leftKeyColumn        The column index from the left table to use as the key for the join.
	 * @param rightKeyColumn       The column index from the right table to use as the key for the join.
	 * @param defaultRightLine     The default line for right table entries when there is no match: a {@link String}
	 *                             split on <code>;</code> when the join runs (legacy form) or a {@link List} of cells.
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
		@JsonProperty(value = "leftTable", required = true) @JsonDeserialize(
			using = TableLiteralDeserializer.class
		) Object leftTable,
		@JsonProperty(value = "rightTable", required = true) @JsonDeserialize(
			using = TableLiteralDeserializer.class
		) Object rightTable,
		@JsonProperty(value = "leftKeyColumn", required = true) Integer leftKeyColumn,
		@JsonProperty(value = "rightKeyColumn", required = true) Integer rightKeyColumn,
		@JsonProperty("defaultRightLine") @JsonDeserialize(using = StringOrListDeserializer.class) Object defaultRightLine,
		@JsonProperty("keyType") String keyType,
		@JsonProperty("key") String key,
		@JsonProperty("executeForEachEntryOf") ExecuteForEachEntryOf executeForEachEntryOf
	) {
		super(type, computes, forceSerialization, key, executeForEachEntryOf);
		if (leftTable instanceof List<?> rows) {
			this.leftTableRows = copyRows(rows);
		} else {
			this.leftTable = (String) leftTable;
		}
		if (rightTable instanceof List<?> rows) {
			this.rightTableRows = copyRows(rows);
		} else {
			this.rightTable = (String) rightTable;
		}
		this.leftKeyColumn = leftKeyColumn;
		this.rightKeyColumn = rightKeyColumn;
		if (defaultRightLine instanceof List<?> cells) {
			this.defaultRightLineCells = cells.stream()
				.map(String.class::cast)
				.collect(Collectors.toCollection(ArrayList::new));
		} else {
			this.defaultRightLine = (String) defaultRightLine;
		}
		this.keyType = keyType;
	}

	@Override
	public TableJoinSource copy() {
		return TableJoinSource.builder()
			.type(type)
			.key(key)
			.forceSerialization(forceSerialization)
			.computes(getComputes() != null ? new ArrayList<>(getComputes()) : null)
			.executeForEachEntryOf(executeForEachEntryOf != null ? executeForEachEntryOf.copy() : null)
			.leftTable(leftTableRows != null ? leftTableRows : leftTable)
			.rightTable(rightTableRows != null ? rightTableRows : rightTable)
			.leftKeyColumn(leftKeyColumn)
			.rightKeyColumn(rightKeyColumn)
			.defaultRightLine(defaultRightLineCells != null ? defaultRightLineCells : defaultRightLine)
			.keyType(keyType)
			.build();
	}

	@Override
	public void update(UnaryOperator<String> updater) {
		leftTable = updater.apply(leftTable);
		rightTable = updater.apply(rightTable);
		if (leftTableRows != null) {
			leftTableRows.forEach(row -> row.replaceAll(updater));
		}
		if (rightTableRows != null) {
			rightTableRows.forEach(row -> row.replaceAll(updater));
		}
		keyType = updater.apply(keyType);
		defaultRightLine = updater.apply(defaultRightLine);
		if (defaultRightLineCells != null) {
			defaultRightLineCells.replaceAll(updater);
		}
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		stringJoiner.add(super.toString());

		addNonNull(stringJoiner, "- leftTable=", leftTable);
		addNonNull(stringJoiner, "- leftTable=", leftTableRows);
		addNonNull(stringJoiner, "- rightTable=", rightTable);
		addNonNull(stringJoiner, "- rightTable=", rightTableRows);
		addNonNull(stringJoiner, "- leftKeyColumn=", leftKeyColumn);
		addNonNull(stringJoiner, "- rightKeyColumn=", rightKeyColumn);
		addNonNull(stringJoiner, "- defaultRightLine=", defaultRightLine);
		addNonNull(stringJoiner, "- defaultRightLine=", defaultRightLineCells);
		addNonNull(stringJoiner, "- keyType=", keyType);

		return stringJoiner.toString();
	}

	/**
	 * A mutable copy of the rows of a table literal: {@link #update(UnaryOperator)} changes the cells of the copy.
	 *
	 * @param rows The rows, each a {@link List} of {@link String} cells
	 * @return the new rows
	 */
	private static List<List<String>> copyRows(final List<?> rows) {
		return rows.stream()
			.map(row -> ((List<?>) row).stream().map(String.class::cast).collect(Collectors.toCollection(ArrayList::new)))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	@Override
	public SourceTable accept(final ISourceProcessor sourceProcessor) {
		return sourceProcessor.process(this);
	}
}
