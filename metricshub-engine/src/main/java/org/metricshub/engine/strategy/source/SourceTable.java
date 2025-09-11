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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code SourceTable} class represents a table of data obtained from monitor sources.
 * It includes methods for transforming the table to and from CSV format and performing other related operations.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SourceTable {

	private static final String ALTERNATE_COLUMN_SEPARATOR = ",";

	@Default
	private List<List<String>> table = new ArrayList<>();

	@Default
	private List<String> headers = new ArrayList<>();

	private String rawData;

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
			return table
				.stream()
				.filter(Objects::nonNull)
				.map(line ->
					replaceSeparator
						? line.stream().map(val -> val.replace(separator, ALTERNATE_COLUMN_SEPARATOR)).collect(Collectors.toList()) // NOSONAR
						: line
				)
				.map(line -> String.join(separator, line) + separator)
				.collect(Collectors.joining(NEW_LINE));
		}

		return "";
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
			return Stream
				.of(csvTable.split("\n"))
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
	 * Creates an empty {@code SourceTable} instance.
	 *
	 * @return An empty {@code SourceTable} instance.
	 */
	public static SourceTable empty() {
		return SourceTable.builder().build();
	}

	/**
	 * Whether the current source table is empty or not
	 *
	 * @return boolean value
	 */
	public boolean isEmpty() {
		return (rawData == null || rawData.isEmpty()) && (table == null || table.isEmpty());
	}

	/**
	 * Find the source table instance from the connector namespace.<br>
	 * If we have a hard-coded source then we will create a source wrapping the
	 * csv input.
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
		return Optional.of(
			SourceTable
				.builder()
				.table(SourceTable.csvToTable(sourceKey, MetricsHubConstants.TABLE_SEP))
				.rawData(sourceKey)
				.build()
		);
	}
}
