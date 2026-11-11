package org.metricshub.engine.client;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.jflat.JFlat;
import org.metricshub.tablejoin.TableJoin;
import org.metricshub.xflat.XFlat;
import org.metricshub.xflat.exceptions.XFlatException;

/**
 * The ClientsExecutor class provides utility methods for executing
 * various operations through Clients. It includes functionalities for executing
 * computations and running scripts. The execution is done on utilities like
 * AWK, JFlat, TableJoin and XFlat are supported.
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientsExecutor {

	private static final long JSON_2_CSV_TIMEOUT = 60; //seconds

	private static final int PRIVATE_USE_FIRST = 0xE000;
	private static final int PRIVATE_USE_LAST = 0xF8FF;

	private TelemetryManager telemetryManager;

	/**
	 * Execute TableJoin
	 *
	 * @param leftTable              The left table.
	 * @param rightTable             The right table.
	 * @param leftKeyColumnNumber    The column number for the key in the left table.
	 * @param rightKeyColumnNumber   The column number for the key in the right table.
	 * @param defaultRightLine       The default line for the right table.
	 * @param wbemKeyType            {@code true} if WBEM.
	 * @param caseInsensitive        {@code true} for case-insensitive comparison.
	 * @return The result of the table join operation.
	 */
	public List<List<String>> executeTableJoin(
		final List<List<String>> leftTable,
		final List<List<String>> rightTable,
		final int leftKeyColumnNumber,
		final int rightKeyColumnNumber,
		final List<String> defaultRightLine,
		final boolean wbemKeyType,
		boolean caseInsensitive
	) {
		LoggingHelper.trace(
			() -> log.trace(
				"Executing Table Join request:\n- Left-table:\n{}\n- Right-table:\n{}\n",
				TextTableHelper.generateTextTable(leftTable),
				TextTableHelper.generateTextTable(rightTable)
			)
		);

		List<List<String>> result = TableJoin.join(
			leftTable,
			rightTable,
			leftKeyColumnNumber,
			rightKeyColumnNumber,
			defaultRightLine,
			wbemKeyType,
			caseInsensitive
		);

		LoggingHelper.trace(
			() -> log.trace(
				"Executed Table Join request:\n- Left-table:\n{}\n- Right-table:\n{}\n- Result:\n{}\n",
				TextTableHelper.generateTextTable(leftTable),
				TextTableHelper.generateTextTable(rightTable),
				TextTableHelper.generateTextTable(result)
			)
		);

		return result;
	}

	/**
	 * Execute JSON to CSV operation, the JSON being parsed as a tree.
	 *
	 * @param jsonSource    The JSON source string.
	 * @param jsonEntryKey  The JSON entry key.
	 * @param propertyList  The list of properties.
	 * @param separator     The separator for CSV.
	 * @param hostname      The hostname, for logging purpose.
	 * @return The CSV representation of the JSON.
	 * @throws TimeoutException       If the execution times out.
	 * @throws ExecutionException     If an execution exception occurs.
	 * @throws InterruptedException   If the execution is interrupted.
	 */
	public static String executeJson2Csv(
		String jsonSource,
		String jsonEntryKey,
		List<String> propertyList,
		String separator,
		String hostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		return executeJson2Csv(jsonSource, jsonEntryKey, propertyList, separator, false, hostname);
	}

	/**
	 * Execute JSON to CSV operation.
	 *
	 * @param jsonSource    The JSON source string.
	 * @param jsonEntryKey  The JSON entry key.
	 * @param propertyList  The list of properties.
	 * @param separator     The separator for CSV.
	 * @param eventParsing  Whether jflat reads the JSON as a stream of events instead of a tree.
	 * @param hostname      The hostname, for logging purpose.
	 * @return The CSV representation of the JSON.
	 * @throws TimeoutException       If the execution times out.
	 * @throws ExecutionException     If an execution exception occurs.
	 * @throws InterruptedException   If the execution is interrupted.
	 */
	public static String executeJson2Csv(
		String jsonSource,
		String jsonEntryKey,
		List<String> propertyList,
		String separator,
		boolean eventParsing,
		String hostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		LoggingHelper.trace(
			() -> log.trace(
				"Executing JSON to CSV conversion:\n- Json-source:\n{}\n- Json-entry-key: {}\n" + // NOSONAR
					"- Property-list: {}\n- Separator: {}\n",
				jsonSource,
				jsonEntryKey,
				propertyList,
				separator
			)
		);

		final Callable<String> jflatToCSV = () -> {
			try {
				JFlat jsonFlat = new JFlat(jsonSource, eventParsing);

				jsonFlat.parse();

				// Get the CSV
				return jsonFlat.toCSV(jsonEntryKey, propertyList.toArray(new String[0]), separator).toString();
			} catch (IllegalArgumentException e) {
				log.error(
					"Hostname {} - Error detected in the arguments when translating the JSON structure into CSV.",
					hostname
				);
			} catch (Exception e) {
				log.warn("Hostname {} - Error detected when running jsonFlat parsing:\n{}", hostname, jsonSource);
				log.debug("Hostname {} - Exception detected when running jsonFlat parsing: ", hostname, e);
			}

			return null;
		};

		String result = ThreadHelper.execute(jflatToCSV, JSON_2_CSV_TIMEOUT);

		LoggingHelper.trace(
			() -> log.trace(
				"Executed JSON to CSV conversion:\n- Json-source:\n{}\n- Json-entry-key: {}\n" + // NOSONAR
					"- Property-list: {}\n- Separator: {}\n- Result:\n{}\n",
				jsonSource,
				jsonEntryKey,
				propertyList,
				separator,
				result
			)
		);

		return result;
	}

	/**
	 * Execute JSON to table operation: one row per entry, the entry identifier then one cell per property, each
	 * value kept whole (line feeds and separators included, empty cells kept).
	 *
	 * @param jsonSource    The JSON source string.
	 * @param jsonEntryKey  The JSON entry key.
	 * @param propertyList  The list of properties.
	 * @param eventParsing  Whether jflat reads the JSON as a stream of events instead of a tree.
	 * @return The rows, empty when the JSON does not parse.
	 * @throws TimeoutException       If the execution times out.
	 * @throws ExecutionException     If an execution exception occurs.
	 * @throws InterruptedException   If the execution is interrupted.
	 */
	public List<List<String>> executeJson2Table(
		String jsonSource,
		String jsonEntryKey,
		List<String> propertyList,
		boolean eventParsing
	) throws InterruptedException, ExecutionException, TimeoutException {
		return executeJson2Table(jsonSource, jsonEntryKey, propertyList, eventParsing, telemetryManager.getHostname());
	}

	/**
	 * Execute JSON to table operation: one row per entry, the entry identifier then one cell per property, each
	 * value kept whole (line feeds and separators included, empty cells kept).
	 *
	 * @param jsonSource    The JSON source string.
	 * @param jsonEntryKey  The JSON entry key.
	 * @param propertyList  The list of properties.
	 * @param eventParsing  Whether jflat reads the JSON as a stream of events instead of a tree.
	 * @param hostname      The hostname, for logging purpose.
	 * @return The rows, empty when the JSON does not parse.
	 * @throws TimeoutException       If the execution times out.
	 * @throws ExecutionException     If an execution exception occurs.
	 * @throws InterruptedException   If the execution is interrupted.
	 */
	public static List<List<String>> executeJson2Table(
		String jsonSource,
		String jsonEntryKey,
		List<String> propertyList,
		boolean eventParsing,
		String hostname
	) throws InterruptedException, ExecutionException, TimeoutException {
		// jflat only writes text: it ends each cell with the separator and each row with a line feed. A separator
		// that no value can hold makes the cells exact.
		// ponytail: private-use separator because jflat can only write text; the ceiling is a document holding all
		// 6400 private-use characters (rejected). Upgrade: a jflat method emitting the rows, then drop this split.
		final String separator = cellSeparatorAbsentFrom(jsonSource == null ? "" : jsonSource);
		final String csv = executeJson2Csv(jsonSource, jsonEntryKey, propertyList, separator, eventParsing, hostname);
		final List<List<String>> rows = new ArrayList<>();
		if (csv == null) {
			return rows;
		}
		final int width = propertyList.size() + 1;
		final List<String> cells = Arrays.asList(csv.split(separator, -1));
		for (int start = 0; start + width < cells.size(); start += width) {
			final List<String> row = new ArrayList<>(cells.subList(start, start + width));
			if (start > 0) {
				// The line feed ending the previous row
				row.set(0, row.get(0).substring(1));
			}
			rows.add(row);
		}
		return rows;
	}

	/**
	 * A private-use character that a JSON document cannot decode to: neither the character nor its unicode escape
	 * appears in the document.
	 *
	 * @param json The JSON document
	 * @return the character, as a string that is also a literal regular expression
	 */
	static String cellSeparatorAbsentFrom(final String json) {
		// One pass: mark each private-use character found raw or as a backslash-u escape (hex in any case)
		final BitSet present = new BitSet();
		final int length = json.length();
		for (int i = 0; i < length; i++) {
			final char c = json.charAt(i);
			if (c >= PRIVATE_USE_FIRST && c <= PRIVATE_USE_LAST) {
				present.set(c - PRIVATE_USE_FIRST);
			} else if (c == '\\' && i + 5 < length && (json.charAt(i + 1) == 'u' || json.charAt(i + 1) == 'U')) {
				try {
					final int escaped = Integer.parseInt(json, i + 2, i + 6, 16);
					if (escaped >= PRIVATE_USE_FIRST && escaped <= PRIVATE_USE_LAST) {
						present.set(escaped - PRIVATE_USE_FIRST);
					}
				} catch (NumberFormatException e) {
					// Not an escape
				}
			}
		}
		final int free = PRIVATE_USE_FIRST + present.nextClearBit(0);
		if (free > PRIVATE_USE_LAST) {
			throw new IllegalArgumentException("The JSON document holds every private-use character");
		}
		return String.valueOf((char) free);
	}

	/**
	 * Parse a XML with the argument properties into a list of values list.
	 *
	 * @param xml        The XML.
	 * @param properties A string containing the paths to properties to retrieve separated by a semi-colon character.<br>
	 *                   If the property comes from an attribute, it will be preceded by a superior character: '>'.
	 * @param recordTag  A string containing the first element xml tags path to convert. example: /rootTag/tag2
	 * @return The list of values list.
	 * @throws XFlatException if an error occurred in the XML parsing.
	 */
	public List<List<String>> executeXmlParsing(final String xml, final String properties, final String recordTag)
		throws XFlatException {
		LoggingHelper.trace(
			() -> log.trace(
				"Executing XML parsing:\n- Xml-source:\n{}\n- Properties: {}\n- Record-tag: {}\n",
				xml,
				properties,
				recordTag
			)
		);

		List<List<String>> result = XFlat.parseXml(xml, properties, recordTag);

		LoggingHelper.trace(
			() -> log.trace(
				"Executed XML parsing:\n- Xml-source:\n{}\n- Properties: {}\n- Record-tag: {}\n- Result:\n{}\n",
				xml,
				properties,
				recordTag,
				TextTableHelper.generateTextTable(properties, result)
			)
		);

		return result;
	}
}
