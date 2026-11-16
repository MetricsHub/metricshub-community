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
import com.fasterxml.jackson.databind.ObjectReader;
import io.jawk.ext.AbstractExtension;
import io.jawk.ext.JawkExtension;
import io.jawk.ext.annotations.JawkAssocArray;
import io.jawk.ext.annotations.JawkFunction;
import io.jawk.jrt.AssocArray;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
		// A number that is not an integer keeps the digits of the document
		.with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

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
	public String executeHttpRequest(final @JawkAssocArray Map<Object, Object> argMap) {
		return executeSource(Source.fromMap(HttpSource.class, argMap));
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
	 * @return The table result from the execution of the {@link SnmpGetSource}.
	 */
	@JawkFunction("executeSnmpGet")
	public String executeSnmpGetRequest(final @JawkAssocArray Map<Object, Object> argMap) {
		return executeSource(Source.fromMap(SnmpGetSource.class, argMap));
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
				: SourceTable.tableToCsv(sourceTableResult.getTable(), TABLE_SEP, false);
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
	 * @param argMap   The array of arguments to use to create the {@link HttpSource}. Its <code>resultContent</code>
	 *                 plays no part: the function reads the status and the body of the response.
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
		final HttpResult result = sourceProcessor.executeHttpRequest(Source.fromMap(HttpSource.class, argMap));
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
				fill(response, prepare(document));
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
					// Read as a stream of events: the same rows, without the whole document as a tree in memory
					true,
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
	 * Prepares a JSON document for an AWK script, in place and at any depth: a number that is not a 64-bit integer
	 * becomes its text, which the script gets as a numeric string, like a field. It computes and compares as a
	 * number, and prints with the digits of the document, where a double prints with <code>OFMT</code> (6
	 * significant digits).
	 *
	 * @param value A value of the document, as Jackson reads it
	 * @return the value to give to the script
	 */
	@SuppressWarnings("unchecked")
	private static Object prepare(final Object value) {
		if (value instanceof Map) {
			((Map<Object, Object>) value).replaceAll((key, item) -> prepare(item));
		} else if (value instanceof List) {
			((List<Object>) value).replaceAll(MetricsHubExtensionForJawk::prepare);
		} else if (value instanceof BigDecimal || value instanceof BigInteger) {
			return value.toString();
		}
		return value;
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
