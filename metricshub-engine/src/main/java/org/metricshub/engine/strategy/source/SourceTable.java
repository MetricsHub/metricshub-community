package org.metricshub.engine.strategy.source;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WINDOWS_NEW_LINE;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The result of a source or of a compute: either a text or a table, as its {@link ContentType} tells.
 * <p>
 * A reader of the other type sees an empty value: a table reader of a text sees no rows, a text reader of a table
 * sees an empty text. {@link #asText()} and {@link #asTable()} are the conversions.
 */
@EqualsAndHashCode
@ToString
public class SourceTable {

	private static final String ALTERNATE_COLUMN_SEPARATOR = ",";

	/**
	 * Never null: tells whether {@link #text} or {@link #table} holds the value.
	 */
	private ContentType contentType;

	/**
	 * The text, non-null only when the content type is {@link ContentType#TEXT}.
	 */
	private String text;

	/**
	 * The table, non-null only when the content type is {@link ContentType#TABLE}.
	 */
	private List<List<String>> table;

	private SourceTable() {}

	/**
	 * A source table holding a text.
	 *
	 * @param text The text, null meaning empty
	 * @return a new source table whose content type is {@link ContentType#TEXT}
	 */
	public static SourceTable ofText(final String text) {
		final SourceTable sourceTable = new SourceTable();
		sourceTable.setText(text);
		return sourceTable;
	}

	/**
	 * A source table holding a table.
	 *
	 * @param table The table, null meaning empty
	 * @return a new source table whose content type is {@link ContentType#TABLE}
	 */
	public static SourceTable ofTable(final List<List<String>> table) {
		final SourceTable sourceTable = new SourceTable();
		sourceTable.setTable(table);
		return sourceTable;
	}

	/**
	 * Creates an empty table. A new instance on every call: the strategies compare it by identity.
	 *
	 * @return a new empty {@code SourceTable} whose content type is {@link ContentType#TABLE}
	 */
	public static SourceTable empty() {
		return ofTable(null);
	}

	/**
	 * The content type of this source table, never null.
	 *
	 * @return {@link ContentType#TEXT} or {@link ContentType#TABLE}
	 */
	public ContentType getContentType() {
		return contentType;
	}

	/**
	 * The text of this source table.
	 *
	 * @return the text, an empty text for a table
	 */
	public String getText() {
		return text == null ? EMPTY : text;
	}

	/**
	 * The table of this source table, which the table computes change in place.
	 *
	 * @return the table, an empty table for a text (the legacy rule: a table reader of a text sees no rows)
	 */
	public List<List<String>> getTable() {
		return table == null ? new ArrayList<>() : table;
	}

	/**
	 * Makes this source table a text, dropping its table.
	 *
	 * @param text The text, null meaning empty
	 */
	public void setText(final String text) {
		this.contentType = ContentType.TEXT;
		this.text = text == null ? EMPTY : text;
		this.table = null;
	}

	/**
	 * Makes this source table a table, dropping its text.
	 *
	 * @param table The table, null meaning empty
	 */
	public void setTable(final List<List<String>> table) {
		this.contentType = ContentType.TABLE;
		this.table = table == null ? new ArrayList<>() : table;
		this.text = null;
	}

	/**
	 * Whether the value of this source table is empty.
	 *
	 * @return true for an empty text or a table with no rows
	 */
	public boolean isEmpty() {
		return contentType == ContentType.TEXT ? text.isEmpty() : table.isEmpty();
	}

	/**
	 * This source table as a text: the text itself, or the table with one line per row, cells joined with
	 * <code>;</code> and a trailing <code>;</code>, a <code>;</code> inside a cell replaced by <code>,</code>, and
	 * never a CRLF. This source table is not changed.
	 *
	 * @return the text
	 */
	public String asText() {
		if (contentType == ContentType.TEXT) {
			return text;
		}
		return tableToCsv(table, TABLE_SEP, true).replace(WINDOWS_NEW_LINE, NEW_LINE);
	}

	/**
	 * This source table as a table: a copy of the table itself, or the text split on <code>;</code> by
	 * {@link #textToTable(String, String)}. This source table is not changed.
	 *
	 * @return a new table
	 */
	public List<List<String>> asTable() {
		if (contentType == ContentType.TABLE) {
			return table.stream().map(row -> row == null ? null : new ArrayList<>(row)).collect(Collectors.toList()); //NOSONAR
		}
		return textToTable(text, TABLE_SEP);
	}

	/**
	 * Splits a text into a table: one row per line, CRLF or LF, split on a literal separator. The empty cells are
	 * kept but the one after a trailing separator; a blank separator (a space, a tab) splits on runs of it and keeps
	 * no empty cell. A blank line, or a line that gives no cell, gives no row.
	 *
	 * @param text      The text, null meaning empty
	 * @param separator The literal separator
	 * @return a new table
	 */
	public static List<List<String>> textToTable(final String text, final String separator) {
		final List<List<String>> rows = new ArrayList<>();
		if (text == null) {
			return rows;
		}
		for (final String line : lineToList(text.replace(WINDOWS_NEW_LINE, NEW_LINE), NEW_LINE)) {
			final List<String> cells = splitLine(line, separator);
			if (!cells.isEmpty()) {
				rows.add(cells);
			}
		}
		return rows;
	}

	/**
	 * Splits a line into cells on a literal separator, as described in {@link #textToTable(String, String)}.
	 *
	 * @param line      The line
	 * @param separator The literal separator
	 * @return the cells, mutable, none for an empty line
	 */
	private static List<String> splitLine(final String line, final String separator) {
		if (line == null || line.isEmpty()) {
			return new ArrayList<>();
		}
		final String[] cells = line.split(Pattern.quote(separator), -1);
		if (separator.isBlank()) {
			return Stream.of(cells).filter(cell -> !cell.isEmpty()).collect(Collectors.toCollection(ArrayList::new));
		}
		final int count = line.endsWith(separator) ? cells.length - 1 : cells.length;
		return Stream.of(cells).limit(count).collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Transform the {@link List} table to a {@link String} representation
	 * [[a1,b1,c2],[a1,b1,c1]]
	 * =>
	 * a1,b1,c1,
	 * a2,b2,c2,
	 *
	 * @param table The table result we wish to parse
	 * @param separator The cells separator on each line
	 * @param replaceSeparator Whether we should replace the separator by comma
	 * @return {@link String} value
	 */
	public static String tableToCsv(
		final List<List<String>> table,
		final String separator,
		final boolean replaceSeparator
	) {
		if (table != null) {
			return table.stream()
				.filter(Objects::nonNull)
				.map(line -> lineToCsv(line, separator, replaceSeparator))
				.collect(Collectors.joining(NEW_LINE));
		}

		return "";
	}

	/**
	 * Convert a line of data to a CSV-formatted string.
	 *
	 * @param line The non-null row of data to convert.
	 * @param separator The non-null separator to use between values.
	 * @param replaceSeparator Whether to replace the separator with an alternate column separator.
	 * @return The CSV-formatted string.
	 */
	public static String lineToCsv(
		@NonNull final List<String> line,
		@NonNull final String separator,
		final boolean replaceSeparator
	) {
		Stream<String> stream = line.stream().map(val -> val == null ? EMPTY : val);

		if (replaceSeparator) {
			stream = stream.map(val -> val.replace(separator, ALTERNATE_COLUMN_SEPARATOR));
		}

		return stream.collect(Collectors.joining(separator, EMPTY, separator));
	}

	/**
	 * Safe version of the {@link #lineToCsv(List, String, boolean)} method that handles null input.
	 *
	 * @param line The row of data to convert.
	 * @param separator The non-null separator to use between values.
	 * @param replaceSeparator Whether to replace the separator with an alternate column separator.
	 * @return The CSV-formatted string.
	 */
	public static String lineToCsvSafe(
		final List<String> line,
		@NonNull final String separator,
		final boolean replaceSeparator
	) {
		if (line == null) {
			return EMPTY;
		}
		return lineToCsv(line, separator, replaceSeparator);
	}

	/**
	 * Return the List representation of the CSV String table :
	 * a1,b1,c1,
	 * a2,b2,c2,
	 * =>
	 * [[a1,b1,c2],[a1,b1,c1]]
	 *
	 * @param csvTable The CSV table we wish to parse
	 * @param separator The cells separator
	 * @return {@link List} of {@link List} table
	 */
	public static List<List<String>> csvToTable(final String csvTable, final String separator) {
		if (csvTable != null) {
			return Stream.of(csvTable.split("\n"))
				.map(line -> lineToList(line, separator))
				.filter(line -> !line.isEmpty())
				.collect(Collectors.toList()); //NOSONAR
		}
		return new ArrayList<>();
	}

	/**
	 * Transforms a line of CSV-formatted data to a list.
	 * <p>
	 * a1,b1,c1, => [ a1, b1, c1 ]
	 * </p>
	 *
	 * @param line      The CSV-formatted line to be transformed.
	 * @param separator The separator between cells.
	 * @return The list of strings representing the line.
	 */
	public static List<String> lineToList(String line, final String separator) {
		if (line != null && !line.isEmpty()) {
			// Make sure the line ends with the separator
			line = !line.endsWith(separator) ? line + separator : line;

			// Make sure we don't change the integrity of the line with the split in case of empty cells
			final String[] split = line.split(separator, -1);
			return Stream.of(split).limit(split.length - 1L).collect(Collectors.toList()); //NOSONAR
		}
		return new ArrayList<>();
	}

	/**
	 * Find the source table instance from the connector namespace.<br>
	 * A hard-coded source gives a new table parsed from the csv input.
	 * @param sourceKey The reference of the source or hard-coded source information
	 * @param connectorId The connector identifier used to retrieve {@link SourceTable} from the connector namespace.
	 * @param telemetryManager The instance wrapping the host properties where the connector namespace is located.
	 * @return {@link Optional} instance of {@link SourceTable}
	 */
	public static Optional<SourceTable> lookupSourceTable(
		final String sourceKey,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		final Matcher matcher = SOURCE_REF_PATTERN.matcher(sourceKey);

		if (matcher.find()) {
			return Optional.ofNullable(
				telemetryManager.getHostProperties().getConnectorNamespace(connectorId).getSourceTable(matcher.group())
			);
		}

		// Hard-coded source
		return Optional.of(ofTable(csvToTable(sourceKey, TABLE_SEP)));
	}

	/**
	 * Find the table of a <code>tableUnion</code> member or of a <code>tableJoin</code> input: a table literal written
	 * as a list of rows gives a new table of exactly these rows, a string is looked up by
	 * {@link #lookupSourceTable(String, String, TelemetryManager)}.
	 *
	 * @param table            A {@link List} of rows, each a {@link List} of cells, or a {@link String}
	 * @param connectorId      The connector identifier used to retrieve {@link SourceTable} from the connector namespace.
	 * @param telemetryManager The instance wrapping the host properties where the connector namespace is located.
	 * @return {@link Optional} instance of {@link SourceTable}
	 */
	@SuppressWarnings("unchecked")
	public static Optional<SourceTable> lookupTableOrLiteral(
		final Object table,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		if (table instanceof List<?> rows) {
			return Optional.of(
				ofTable(
					rows.stream().map(row -> (List<String>) new ArrayList<>((List<String>) row)).collect(Collectors.toList()) //NOSONAR
				)
			);
		}
		return lookupSourceTable((String) table, connectorId, telemetryManager);
	}
}
