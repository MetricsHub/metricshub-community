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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.JawkExtension;
import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.jrt.AssocArray;
import io.jawk.jrt.GawkBool;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.LongStream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.ConnectorVariables;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.extension.HttpResult;
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
	 * Reads the JSON result of a HTTP request: anything after the document is an error
	 */
	private static final ObjectReader JSON_READER = JsonHelper.buildObjectMapper()
		.readerFor(Object.class)
		.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
		// A number that is not an integer keeps the digits of the document: Jawk gives a BigDecimal to the script
		// as a numeric string
		.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

	/**
	 * Writes the JSON body of a HTTP request, on one line
	 */
	private static final ObjectWriter JSON_WRITER = JsonHelper.buildObjectMapper()
		.writer()
		.without(SerializationFeature.INDENT_OUTPUT);

	/**
	 * A <code>Content-Type</code> line in the header of a HTTP request
	 */
	private static final Pattern CONTENT_TYPE_HEADER = Pattern.compile("(?im)^\\s*content-type\\s*:");

	private static final String JSON_CONTENT_TYPE = "Content-Type: application/json";

	/**
	 * A macro that names no escape, as <code>MacrosUpdater</code> reads it: <code>%{USERNAME}</code>,
	 * <code>${::PASSWORD}</code>.
	 */
	private static final Pattern UNESCAPED_MACRO = Pattern.compile("([$%]\\{)(?:::)?([A-Z0-9_-]+\\})");

	/**
	 * The start of a reference to an embedded file, after its <code>$</code>.
	 */
	private static final Pattern FILE_REFERENCE = Pattern.compile("(?i)\\$(\\{file::)");

	private static final String BODY = "body";

	/**
	 * What follows each cell in the text that an <code>execute*</code> function returns for a table.
	 */
	private static final String CELL_SEPARATOR = ";";

	private static final String HEADER = "header";

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
	 * @param argMap The array of arguments to use to create the {@link HttpSource}. Its <code>body</code> may be an
	 *               array, written as JSON: see {@link #httpSource(Map)}.
	 * @return The table result from the execution of the {@link HttpSource}.
	 */
	@JawkFunction("executeHttpRequest")
	public String executeHttpRequest(final @JawkAssocArray Map<Object, Object> argMap) {
		return executeSource(httpSource(argMap));
	}

	/**
	 * Creates the HTTP source of a request from the arguments of a script. A <code>body</code> that is an array is
	 * written as the JSON body of the request, the way a response is read by
	 * {@link #executeHttpJsonRequest(Map, Map)}, the other way round:
	 * <ul>
	 * <li>An array whose indexes are 0 to n-1 is a JSON array, any other array an object, an empty one included. The
	 * properties of an object are written in alphabetical order: an AWK array has no order, and a recorded request
	 * is found by its body.</li>
	 * <li>A number is a number (<code>3</code>, not <code>3.0</code>), a boolean-typed number <code>true</code> or
	 * <code>false</code>.</li>
	 * <li>Anything else is a string: a string, a numeric string read from the input (<code>$4</code> holding
	 * <code>001</code> is <code>"001"</code>, and <code>$4 + 0</code> the number 1), an element without a value (an
	 * empty string: <code>null</code> cannot be written).</li>
	 * </ul>
	 * <code>Content-Type: application/json</code> is added to a header that names no content type. The header is a
	 * text: a script gets the text of a header file through a variable, and no embedded file is read here. The array of
	 * the script is not modified.
	 * <p>
	 * What the request does to its body afterwards does not undo that:
	 * <ul>
	 * <li>A macro in a value (<code>%{USERNAME}</code>) is replaced when the request is sent: it becomes
	 * <code>%{esc(json)::USERNAME}</code>, so that its value is escaped as the rest of the document is. A macro
	 * that names its escape is left as it is.</li>
	 * <li>A value that reads as a reference to an embedded file is the text of the value: the reference would
	 * otherwise replace the whole body.</li>
	 * </ul>
	 *
	 * @param argMap The array of arguments of the script
	 * @return the HTTP source
	 */
	private HttpSource httpSource(final Map<Object, Object> argMap) {
		if (!(argMap.get(BODY) instanceof Map)) {
			return Source.fromMap(HttpSource.class, argMap);
		}
		final Map<Object, Object> arguments = new LinkedHashMap<>(argMap);
		try {
			// In a JSON document, "${" and "%{" are only found in a string: both replacements stay in it
			final String json = JSON_WRITER.writeValueAsString(toJson(argMap.get(BODY)));
			final String escaped = UNESCAPED_MACRO.matcher(json).replaceAll("$1esc(json)::$2");
			arguments.put(BODY, FILE_REFERENCE.matcher(escaped).replaceAll("\\\\u0024$1"));
		} catch (JsonProcessingException e) {
			// Maps, lists, strings, numbers and booleans: Jackson writes them all
			throw new UncheckedIOException(e);
		}
		final String header = Objects.toString(argMap.get(HEADER), "");
		if (!CONTENT_TYPE_HEADER.matcher(header).find()) {
			arguments.put(HEADER, header.isBlank() ? JSON_CONTENT_TYPE : header.stripTrailing() + "\n" + JSON_CONTENT_TYPE);
		}
		return Source.fromMap(HttpSource.class, arguments);
	}

	/**
	 * The JSON value of an AWK value, as {@link #httpSource(Map)} describes it.
	 *
	 * @param value A value of the array of a script, as its plain view gives it
	 * @return a list, a map, a number, a boolean or a string
	 */
	private Object toJson(final Object value) {
		if (value instanceof Map<?, ?> array) {
			final int size = array.size();
			if (size > 0 && LongStream.range(0, size).allMatch(array::containsKey)) {
				return LongStream.range(0, size).mapToObj(index -> toJson(array.get(index))).toList();
			}
			final Map<String, Object> object = new TreeMap<>();
			array.forEach((key, item) -> object.put(toAwkString(key), toJson(item)));
			return object;
		}
		if (value instanceof GawkBool bool) {
			return bool.booleanValue();
		}
		if (value instanceof Double number && number == Math.rint(number) && Math.abs(number) < Long.MAX_VALUE) {
			return number.longValue();
		}
		return value;
	}

	/**
	 * Execute a Ipmi request through the context.
	 *
	 * @param args Optional array of arguments to use to create the {@link IpmiSource}.
	 * @return The table result from the execution of the {@link IpmiSource}.
	 */
	@JawkFunction("executeIpmiRequest")
	@SafeVarargs
	public final String executeIpmiRequest(@JawkAssocArray Map<Object, Object>... args) {
		if (args.length > 0 && args[0] != null) {
			return executeSource(Source.fromMap(IpmiSource.class, args[0]));
		}
		return executeSource(IpmiSource.builder().build());
	}

	/**
	 * Execute a Snmp Get request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link SnmpGetSource}.
	 * @return The value of the OID as it is (line breaks as LF), or an empty string when the request fails. Always a
	 *         text: an <code>output</code> in the arguments is ignored.
	 */
	@JawkFunction("executeSnmpGet")
	public String executeSnmpGetRequest(final @JawkAssocArray Map<Object, Object> argMap) {
		final SnmpGetSource snmpGetSource = Source.fromMap(SnmpGetSource.class, argMap);
		snmpGetSource.setOutput(ContentType.TEXT);
		return executeSource(snmpGetSource);
	}

	/**
	 * Execute a Snmp Table request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link SnmpTableSource}.
	 * @return The table result from the execution of the {@link SnmpTableSource}.
	 */
	@JawkFunction("executeSnmpTable")
	public String executeSnmpTableRequest(final @JawkAssocArray Map<Object, Object> argMap) {
		return executeSource(Source.fromMap(SnmpTableSource.class, argMap));
	}

	/**
	 * Execute a Wbem request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link WbemSource}.
	 * @return The table result from the execution of the {@link WbemSource}.
	 */
	@JawkFunction("executeWbemRequest")
	public String executeWbemRequest(final @JawkAssocArray Map<Object, Object> argMap) {
		return executeSource(Source.fromMap(WbemSource.class, argMap));
	}

	/**
	 * Execute a Wmi request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link WmiSource}.
	 * @return The table result from the execution of the {@link WmiSource}.
	 */
	@JawkFunction("executeWmiRequest")
	public String executeWmiRequest(final @JawkAssocArray Map<Object, Object> argMap) {
		return executeSource(Source.fromMap(WmiSource.class, argMap));
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
				: SourceTable.tableToCsv(sourceTableResult.getTable(), CELL_SEPARATOR, false);
		} else {
			return "";
		}
	}

	/**
	 * Execute a HTTP request through the context and parse its result as JSON: the script crawls the document as
	 * nested arrays, such as <code>response["data"]["items"][0]["name"]</code>.
	 * <p>
	 * A JSON object is an array indexed by its keys, a JSON array an array indexed from 0. A string is a string,
	 * <code>true</code> and <code>false</code> are 1 and 0. An integer is a 64-bit integer, and any other number
	 * computes as a double and prints with the digits of the document. A <code>null</code> is an element without a
	 * value, as Jawk makes it: it reads as an empty string, or as an empty array where the script expects an object.
	 * A result followed by anything else than white space is not JSON.
	 * The function fills an array instead of returning one: Jawk cannot assign a returned array to a variable.
	 * <p>
	 * It returns the HTTP status code, so that a script tells a refused request (401) from an empty answer (200). The
	 * body of an error response is parsed too: it often says why.
	 *
	 * @param argMap   The array of arguments to use to create the {@link HttpSource}. Its <code>body</code> may be
	 *                 an array, written as JSON: see {@link #httpSource(Map)}. Its <code>resultContent</code> plays
	 *                 no part: the function reads the status and the body of the response.
	 * @param response The array to fill with the document. It is emptied first, and stays empty when the body is
	 *                 not a JSON object or array.
	 * @return The HTTP status code of the response (200, 401, ...), 0 when the request got no response.
	 */
	@JawkFunction("executeHttpJsonRequest")
	public int executeHttpJsonRequest(
		final @JawkAssocArray Map<Object, Object> argMap,
		final @JawkAssocArray Map<Object, Object> response
	) {
		response.clear();
		final HttpResult result = sourceProcessor.executeHttpRequest(httpSource(argMap));
		if (result == null) {
			return 0;
		}
		final String body = result.body();
		if (body == null || body.isBlank()) {
			return result.statusCode();
		}
		try {
			// Jackson gives the maps, the lists and the values that Jawk reads
			final Object document = JSON_READER.readValue(body);
			if (document instanceof Map || document instanceof List) {
				fill(response, document);
			} else {
				log.warn("Hostname {} - executeHttpJsonRequest: the body is not a JSON object or array.", hostname);
			}
		} catch (JsonProcessingException e) {
			// The kind and the location only: the message of the parser quotes the document
			log.warn(
				"Hostname {} - executeHttpJsonRequest: the body does not read as JSON ({} {}).",
				hostname,
				e.getClass().getSimpleName(),
				e.getLocation() != null ? e.getLocation().offsetDescription() : "no location"
			);
		}
		return result.statusCode();
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
	 * @return The number of rows.
	 */
	@JawkFunction("json2Table")
	public int json2Table(
		final Object json,
		final Object entryKey,
		final Object properties,
		final @JawkAssocArray Map<Object, Object> rows
	) {
		rows.clear();
		try {
			fill(
				rows,
				ClientsExecutor.executeJson2Table(
					toAwkString(json),
					toAwkString(entryKey),
					Arrays.asList(toAwkString(properties).split(";")),
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
