package org.sentrysoftware.metricshub.engine.client;

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

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.jflat.JFlat;
import org.sentrysoftware.metricshub.engine.awk.AwkException;
import org.sentrysoftware.metricshub.engine.awk.AwkExecutor;
import org.sentrysoftware.metricshub.engine.common.helpers.LoggingHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.ThreadHelper;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
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
		LoggingHelper.trace(() ->
			log.trace(
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

		LoggingHelper.trace(() ->
			log.trace(
				"Executed Table Join request:\n- Left-table:\n{}\n- Right-table:\n{}\n- Result:\n{}\n",
				TextTableHelper.generateTextTable(leftTable),
				TextTableHelper.generateTextTable(rightTable),
				TextTableHelper.generateTextTable(result)
			)
		);

		return result;
	}

	/**
	 * Call AwkExecutor in order to execute the Awk script on the given input
	 *
	 * @param embeddedFileScript The embedded file script.
	 * @param input              The input for the Awk script.
	 * @return The result of executing the Awk script.
	 * @throws AwkException if an error occurs during Awk script execution.
	 */
	@WithSpan("AWK")
	public String executeAwkScript(
		@SpanAttribute("awk.script") String embeddedFileScript,
		@SpanAttribute("awk.input") String input
	) throws AwkException {
		if (embeddedFileScript == null || input == null) {
			return null;
		}

		return AwkExecutor.executeAwk(embeddedFileScript, input);
	}

	/**
	 * Execute JSON to CSV operation.
	 *
	 * @param jsonSource    The JSON source string.
	 * @param jsonEntryKey  The JSON entry key.
	 * @param propertyList  The list of properties.
	 * @param separator     The separator for CSV.
	 * @return The CSV representation of the JSON.
	 * @throws TimeoutException       If the execution times out.
	 * @throws ExecutionException     If an execution exception occurs.
	 * @throws InterruptedException   If the execution is interrupted.
	 */
	public String executeJson2Csv(String jsonSource, String jsonEntryKey, List<String> propertyList, String separator)
		throws InterruptedException, ExecutionException, TimeoutException {
		return executeJson2Csv(jsonSource, jsonEntryKey, propertyList, separator, telemetryManager.getHostname());
	}

	/**
	 * Execute JSON to CSV operation.
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
		LoggingHelper.trace(() ->
			log.trace(
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
				JFlat jsonFlat = new JFlat(jsonSource);

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

		LoggingHelper.trace(() ->
			log.trace(
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
		LoggingHelper.trace(() ->
			log.trace(
				"Executing XML parsing:\n- Xml-source:\n{}\n- Properties: {}\n- Record-tag: {}\n",
				xml,
				properties,
				recordTag
			)
		);

		List<List<String>> result = XFlat.parseXml(xml, properties, recordTag);

		LoggingHelper.trace(() ->
			log.trace(
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
