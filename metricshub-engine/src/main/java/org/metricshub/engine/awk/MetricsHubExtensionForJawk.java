package org.metricshub.engine.awk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2025 MetricsHub
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.JawkExtension;
import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.ext.annotations.JawkOptional;
import io.jawk.jrt.AssocArray;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.ConnectorVariables;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.compute.ComputeProcessor;

/**
 * This class implements the {@link JawkExtension} contract, reports the supported features, processes sources and
 * computes.
 */
@Slf4j
@Builder
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
public class MetricsHubExtensionForJawk extends AbstractExtension {

	/**
	 * The result of a HTTP request asked with its status: "Status: 200", the header lines, an empty line, the body
	 */
	private static final Pattern STATUS_AND_BODY = Pattern.compile(
		"(?s)Status: (\\d+)(?:\\n(?:[^\\n]+\\n)*\\n(.*))?\\n?"
	);

	/**
	 * Reads the JSON result of a HTTP request: a number keeps its digits, and anything after the document is an error
	 */
	private static final ObjectReader JSON_READER = JsonHelper.buildObjectMapper()
		.reader()
		.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
		.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	@EqualsAndHashCode.Exclude
	private SourceProcessor sourceProcessor;

	private String hostname;
	private String connectorId;

	@Override
	public String getExtensionName() {
		return "MetricsHub";
	}

	/**
	 * Execute a HTTP request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link HttpSource}.
	 * @return The table result from the execution of the {@link HttpSource}.
	 */
	@JawkFunction("executeHttpRequest")
	public String executeHttpRequest(final @JawkAssocArray AssocArray argMap) {
		return executeSource(toSource(HttpSource.class, argMap));
	}

	/**
	 * Execute a Ipmi request through the context.
	 *
	 * @param args Optional array of arguments to use to create the {@link IpmiSource}.
	 * @return The table result from the execution of the {@link IpmiSource}.
	 */
	@JawkFunction("executeIpmiRequest")
	public String executeIpmiRequest(@JawkAssocArray AssocArray... args) {
		if (args[0] != null) {
			return executeSource(toSource(IpmiSource.class, args[0]));
		}
		return executeSource(IpmiSource.builder().build());
	}

	/**
	 * Execute a Snmp Get request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link SnmpGetSource}.
	 * @return The table result from the execution of the {@link SnmpGetSource}.
	 */
	@JawkFunction("executeSnmpGet")
	public String executeSnmpGetRequest(final @JawkAssocArray AssocArray argMap) {
		return executeSource(toSource(SnmpGetSource.class, argMap));
	}

	/**
	 * Execute a Snmp Table request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link SnmpTableSource}.
	 * @return The table result from the execution of the {@link SnmpTableSource}.
	 */
	@JawkFunction("executeSnmpTable")
	public String executeSnmpTableRequest(final @JawkAssocArray AssocArray argMap) {
		return executeSource(toSource(SnmpTableSource.class, argMap));
	}

	/**
	 * Execute a Wbem request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link WbemSource}.
	 * @return The table result from the execution of the {@link WbemSource}.
	 */
	@JawkFunction("executeWbemRequest")
	public String executeWbemRequest(final @JawkAssocArray AssocArray argMap) {
		return executeSource(toSource(WbemSource.class, argMap));
	}

	/**
	 * Execute a Wmi request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link WmiSource}.
	 * @return The table result from the execution of the {@link WmiSource}.
	 */
	@JawkFunction("executeWmiRequest")
	public String executeWmiRequest(final @JawkAssocArray AssocArray argMap) {
		return executeSource(toSource(WmiSource.class, argMap));
	}

	/**
	 * Execute a {@link Source} through the context.
	 *
	 * @param source The {@link Source} to execute.
	 * @return The table result from the execution of the {@link Source}.
	 */
	public String executeSource(final Source source) {
		final SourceTable sourceTableResult = source.accept(sourceProcessor);

		if (sourceTableResult != null && !sourceTableResult.isEmpty()) {
			return sourceTableResult.getContentType() == ContentType.TEXT
				? sourceTableResult.getText()
				: SourceTable.tableToCsv(sourceTableResult.getTable(), TABLE_SEP, false);
		} else {
			return "";
		}
	}

	/**
	 * Execute a HTTP request through the context and parse its result as JSON: the script crawls the document as
	 * nested arrays, such as <code>response["data"]["items"][0]["name"]</code>.
	 * <p>
	 * A JSON object is an array indexed by its keys, a JSON array an array indexed from 0. A string is a string, a
	 * number a number (with all its digits, even beyond what a double holds), <code>true</code> and
	 * <code>false</code> are 1 and 0, <code>null</code> is an empty string. A result followed by anything else than
	 * white space is not JSON.
	 * The function fills an array instead of returning one: Jawk cannot assign a returned array to a variable.
	 * <p>
	 * It returns the HTTP status code, so that a script tells a refused request (401) from an empty answer (200). The
	 * body of an error response is parsed too: it often says why.
	 *
	 * @param argMap   The array of arguments to use to create the {@link HttpSource}. Its <code>resultContent</code>
	 *                 is ignored: the function reads the status and the body.
	 * @param response The array to fill with the document. It is emptied first, and stays empty when the body is
	 *                 not a JSON object or array.
	 * @return The HTTP status code of the response (200, 401, ...), 0 when the request got no response.
	 */
	@JawkFunction("executeHttpJsonRequest")
	public int executeHttpJsonRequest(
		final @JawkAssocArray AssocArray argMap,
		final @JawkAssocArray Map<Object, Object> response
	) {
		response.clear();
		final HttpSource source = toSource(HttpSource.class, argMap);
		source.setResultContent(ResultContent.ALL_WITH_STATUS);

		// "Status: 200", the header lines, an empty line, the body
		final Matcher result = STATUS_AND_BODY.matcher(executeSource(source));
		if (!result.matches()) {
			return 0;
		}
		final int status = Integer.parseInt(result.group(1));
		final String body = result.group(2) == null ? "" : result.group(2);
		if (body.isBlank()) {
			return status;
		}
		try {
			final JsonNode document = JSON_READER.readTree(body);
			if (document == null || !document.isContainerNode()) {
				log.warn("Hostname {} - executeHttpJsonRequest: the body is not a JSON object or array.", hostname);
				return status;
			}
			fill(response, toAwkValue(document));
		} catch (JsonProcessingException e) {
			// The kind and the location only: the message of the parser quotes the document
			log.warn(
				"Hostname {} - executeHttpJsonRequest: the body does not read as JSON ({} {}).",
				hostname,
				e.getClass().getSimpleName(),
				e.getLocation() != null ? e.getLocation().offsetDescription() : "no location"
			);
		}
		return status;
	}

	/**
	 * Builds the source of an <code>execute*</code> function from the AWK array of its arguments.
	 *
	 * @param <T>    The class of the source
	 * @param clazz  The class of the source
	 * @param argMap The AWK array of the arguments
	 * @return the source
	 */
	private static <T extends Source> T toSource(final Class<T> clazz, final Map<Object, Object> argMap) {
		@SuppressWarnings("unchecked")
		final Map<Object, Object> plain = (Map<Object, Object>) plainValue(argMap);
		return Source.fromMap(clazz, plain);
	}

	/**
	 * An AWK value as plain Java data, which Jackson reads: maps and lists copied with plain values, strings,
	 * numbers, booleans and <code>null</code> as they are, anything else as its text. Jawk hands over a field or a
	 * value of an awk variable list, map or table as a numeric string (<code>StrNum</code>), which Jackson would read
	 * as a bean (<code>{"number": false}</code>) instead of its text.
	 *
	 * @param value The AWK value
	 * @return the plain value
	 */
	static Object plainValue(final Object value) {
		if (value instanceof Map<?, ?> map) {
			final Map<Object, Object> plain = new LinkedHashMap<>();
			map.forEach((key, item) -> plain.put(key, plainValue(item)));
			return plain;
		}
		if (value instanceof List<?> list) {
			final List<Object> plain = new ArrayList<>(list.size());
			list.forEach(item -> plain.add(plainValue(item)));
			return plain;
		}
		if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
			return value;
		}
		return value.toString();
	}

	/**
	 * Extract the entries of a JSON document as rows, as the <code>json2Table</code> compute does:
	 * <code>rows[i][0]</code> is the path of the entry, <code>rows[i][1]</code> its first property, and so on, both
	 * indexes starting at 0. A cell holds its value whole, whatever it contains.
	 *
	 * @param json        The JSON document.
	 * @param entryKey    The path of the entries, such as <code>/Members</code>.
	 * @param properties  The properties to extract from each entry, separated by ";".
	 * @param rows        The array to fill with the rows. It is emptied first.
	 * @param parsingMode Optional: "tree" (the default) or "events", see {@link Json2Csv.ParsingMode}.
	 * @return The number of rows.
	 */
	@JawkFunction("json2Table")
	public int json2Table(
		final Object json,
		final Object entryKey,
		final Object properties,
		final @JawkAssocArray Map<Object, Object> rows,
		final @JawkOptional Object parsingMode
	) {
		rows.clear();
		try {
			fill(
				rows,
				ClientsExecutor.executeJson2Table(
					toAwkString(json),
					toAwkString(entryKey),
					Arrays.asList(toAwkString(properties).split(";")),
					parsingMode != null && Json2Csv.ParsingMode.EVENTS.name().equalsIgnoreCase(toAwkString(parsingMode)),
					hostname
				)
			);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.error("Hostname {} - json2Table has failed. Errors:\n{}\n", hostname, StringHelper.getStackMessages(e));
		}
		return rows.size();
	}

	/**
	 * Extract every leaf value of a JSON document, as the <code>json2List</code> compute does, indexed by its path:
	 * <code>list["/data/token"]</code>, <code>list["/items[0]/name"]</code>. An array holds one value per path: when
	 * two values of the document have the same path (a key written twice, or the key <code>a/b</code> next to the key
	 * <code>b</code> of an object <code>a</code>), the last one is kept. The <code>json2List</code> compute gives
	 * both rows.
	 *
	 * @param json The JSON document.
	 * @param list The array to fill with the values. It is emptied first, and stays empty when the document is not a
	 *             JSON object or array.
	 * @return The number of values.
	 */
	@JawkFunction("json2List")
	public int json2List(final Object json, final @JawkAssocArray Map<Object, Object> list) {
		list.clear();
		try {
			final Map<String, Object> values = new LinkedHashMap<>();
			ComputeProcessor.jsonToList(toAwkString(json)).forEach(row -> values.put(row.get(0), row.get(1)));
			fill(list, values);
		} catch (IOException | NumberFormatException e) {
			// The kind only: the message of the parser quotes the document
			log.warn(
				"Hostname {} - json2List: the document does not read as a JSON object or array ({}).",
				hostname,
				e.getClass().getSimpleName()
			);
		}
		return list.size();
	}

	/**
	 * Fill an AWK array with a Java list or map, at any depth. A list is indexed from 0, and a string becomes a
	 * numeric string when it looks like a number, as a field does.
	 *
	 * @param array The AWK array to fill.
	 * @param value The {@link List} or {@link Map} to copy into it.
	 */
	@SuppressWarnings("unchecked")
	private void fill(final Map<Object, Object> array, final Object value) {
		array.putAll(
			(Map<Object, Object>) AssocArray.normalizeValue(
				value,
				getSettings().isUseSortedArrayKeys(),
				getJrt()::toInputScalar
			)
		);
	}

	/**
	 * Convert a JSON value to what an AWK script reads: a {@link Map} for an object, a {@link List} for an array, a
	 * number for a number, 1 or 0 for a boolean, an empty string for <code>null</code>.
	 * <p>
	 * Jackson's own conversion (<code>readValue(json, Object.class)</code>) gives the maps and the lists, but not
	 * values Jawk can hold: a {@link Boolean} stops the script ("Unknown operand_stack type"), and a
	 * {@link java.math.BigDecimal} or a {@link java.math.BigInteger} is rounded to a double when it is printed.
	 *
	 * @param node The JSON value.
	 * @return The value for AWK.
	 */
	private static Object toAwkValue(final JsonNode node) {
		if (node.isObject()) {
			final Map<String, Object> map = new LinkedHashMap<>();
			node.fields().forEachRemaining(field -> map.put(field.getKey(), toAwkValue(field.getValue())));
			return map;
		}
		if (node.isArray()) {
			final List<Object> list = new ArrayList<>();
			node.forEach(element -> list.add(toAwkValue(element)));
			return list;
		}
		if (node.isBoolean()) {
			return node.booleanValue() ? 1L : 0L;
		}
		if (node.isNumber()) {
			// A number that is not a long keeps its digits, as a numeric string: a double would round an unsigned
			// 64-bit counter or a long decimal, and print them with OFMT
			return node.isIntegralNumber() && node.canConvertToLong()
				? (Object) node.longValue()
				: (Object) node.decimalValue().toString();
		}
		return node.isNull() ? "" : node.asText();
	}

	/**
	 * Return the value of the variable in parameter if it exists.
	 *
	 * @param variableNameObj The name of the variable to retrieve.
	 * @return The value of the variable.
	 */
	@JawkFunction("getVariable")
	public String getVariable(final Object variableNameObj) {
		final String variableName = toAwkString(variableNameObj);
		final ConnectorVariables connectorVariables = sourceProcessor.getTelemetryManager()
			.getHostConfiguration()
			.getConnectorVariables()
			.get(connectorId);
		if (connectorVariables != null) {
			return connectorVariables.getVariableValues().get(variableName);
		}
		return sourceProcessor.getTelemetryManager()
			.getConnectorStore()
			.getStore()
			.get(connectorId)
			.getConnectorIdentity()
			.getVariables()
			.get(variableName)
			.getDefaultValue();
	}
}
