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

import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.ConnectorVariables;
import org.metricshub.engine.connector.model.common.HttpMethod;
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
import org.metricshub.jawk.ext.AbstractExtension;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.ext.annotations.JawkAssocArray;
import org.metricshub.jawk.ext.annotations.JawkFunction;
import org.metricshub.jawk.jrt.AssocArray;
import org.metricshub.jawk.jrt.JRT;

/**
 * This class implements the {@link JawkExtension} contract, reports the supported features, processes sources and
 * computes.
 */
@Slf4j
@Builder
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
public class MetricsHubExtensionForJawk extends AbstractExtension implements JawkExtension {

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
		return executeSource(
			HttpSource
				.builder()
				.type("http")
				.method(HttpMethod.valueOf(toAwkString(argMap.get("method")).toUpperCase()))
				.path(toAwkString(argMap.get("path")))
				.header(toAwkString(argMap.get("header")))
				.body(toAwkString(argMap.get("body")))
				.authenticationToken(toAwkString(argMap.get("authenticationToken")))
				.resultContent(ResultContent.detect(toAwkString(argMap.get("resultContent"))))
				.forceSerialization(toAwkString(argMap.get("forceSerialization")).equals("true"))
				.build()
		);
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
			return executeSource(
				IpmiSource
					.builder()
					.type("ipmi")
					.forceSerialization(toAwkString(args[0].get("forceSerialization")).equals("true"))
					.build()
			);
		}

		return executeSource(IpmiSource.builder().forceSerialization(false).build());
	}

	/**
	 * Execute a Snmp Get request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link SnmpGetSource}.
	 * @return The table result from the execution of the {@link SnmpGetSource}.
	 */
	@JawkFunction("executeSnmpGet")
	public String executeSnmpGetRequest(final @JawkAssocArray AssocArray argMap) {
		return executeSource(
			SnmpGetSource
				.builder()
				.type("snmpGet")
				.oid(toAwkString(argMap.get("oid")))
				.forceSerialization(toAwkString(argMap.get("forceSerialization")).equals("true"))
				.build()
		);
	}

	/**
	 * Execute a Snmp Table request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link SnmpTableSource}.
	 * @return The table result from the execution of the {@link SnmpTableSource}.
	 */
	@JawkFunction("executeSnmpTable")
	public String executeSnmpTableRequest(final @JawkAssocArray AssocArray argMap) {
		return executeSource(
			SnmpTableSource
				.builder()
				.type("snmpTable")
				.oid(toAwkString(argMap.get("oid")))
				.selectColumns(toAwkString(argMap.get("selectColumns")))
				.forceSerialization(toAwkString(argMap.get("forceSerialization")).equals("true"))
				.build()
		);
	}

	/**
	 * Execute a Wbem request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link WbemSource}.
	 * @return The table result from the execution of the {@link WbemSource}.
	 */
	@JawkFunction("executeWbemRequest")
	public String executeWbemRequest(final @JawkAssocArray AssocArray argMap) {
		return executeSource(
			WbemSource
				.builder()
				.type("wbem")
				.query(toAwkString(argMap.get("query")))
				.namespace(toAwkString(argMap.get("namespace")))
				.forceSerialization(toAwkString(argMap.get("forceSerialization")).equals("true"))
				.build()
		);
	}

	/**
	 * Execute a Wmi request through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link WmiSource}.
	 * @return The table result from the execution of the {@link WmiSource}.
	 */
	@JawkFunction("executeWmiRequest")
	public String executeWmiRequest(final @JawkAssocArray AssocArray argMap) {
		return executeSource(
			WmiSource
				.builder()
				.type("wmi")
				.query(toAwkString(argMap.get("query")))
				.namespace(toAwkString(argMap.get("namespace")))
				.forceSerialization(toAwkString(argMap.get("forceSerialization")).equals("true"))
				.build()
		);
	}

	/**
	 * Execute a {@link Source} through the context.
	 *
	 * @param source The {@link Source} to execute.
	 * @return The table result from the execution of the {@link Source}.
	 */
	public String executeSource(final Source source) {
		final SourceTable sourceTableResult = source.accept(((MetricsHubAwkSettings) getSettings()).getSourceProcessor());

		if (sourceTableResult != null && !sourceTableResult.isEmpty()) {
			return sourceTableResult.getRawData() != null
				? sourceTableResult.getRawData()
				: SourceTable.tableToCsv(sourceTableResult.getTable(), TABLE_SEP, false);
		} else {
			return "";
		}
	}

	/**
	 * Execute a {@link Json2Csv} compute on the current source through the context.
	 *
	 * @param argMap The array of arguments to use to create the {@link Json2Csv} compute.
	 * @return The table result from the execution of the compute.
	 */
	@JawkFunction("json2csv")
	public String executeJson2csv(final @JawkAssocArray AssocArray argMap) {
		try {
			return ClientsExecutor
				.executeJson2Csv(
					toAwkString(argMap.get("jsonSource")),
					toAwkString(argMap.get("entryKey")),
					toAwkListString(argMap.get("properties")),
					toAwkString(argMap.get("separator")),
					((MetricsHubAwkSettings) getSettings()).getHostname()
				)
				.strip();
		} catch (Exception exception) {
			log.error(
				"Hostname {} - Json2Csv Operation has failed. Errors:\n{}\n",
				((MetricsHubAwkSettings) getSettings()).getHostname(),
				StringHelper.getStackMessages(exception)
			);
			return "";
		}
	}

	/**
	 * Convert a Jawk variable to a {@link List} of {@link String}, assuming the Jawk variable contains a list of
	 * parameters separated by ';'.
	 *
	 * @param arg The Jawk variable to convert.
	 * @return The Jawk variable converted to a {@link List} of {@link String}.
	 */
	private List<String> toAwkListString(final Object arg) {
		final String stringArg = toAwkString(arg);
		return Arrays.asList(stringArg.split(";"));
	}

	/**
	 * Return the value of the variable in parameter if it exists.
	 *
	 * @param variableNameObj The name of the variable to retrieve.
	 * @return The value of the variable.
	 */
	@JawkFunction("getVariable")
	public String getVariable(final Object variableNameObj) {
		MetricsHubAwkSettings settings = (MetricsHubAwkSettings) getSettings();
		final String variableName = toAwkString(variableNameObj);
		final SourceProcessor sourceProcessor = settings.getSourceProcessor();
		final ConnectorVariables connectorVariables = sourceProcessor
			.getTelemetryManager()
			.getHostConfiguration()
			.getConnectorVariables()
			.get(settings.getConnectorId());
		if (connectorVariables != null) {
			return connectorVariables.getVariableValues().get(variableName);
		}
		return sourceProcessor
			.getTelemetryManager()
			.getConnectorStore()
			.getStore()
			.get(settings.getConnectorId())
			.getConnectorIdentity()
			.getVariables()
			.get(variableName)
			.getDefaultValue();
	}

	/**
	 * Converts a byte count (base-2) given as a string to a human-readable form using
	 * IEC units (B, KiB, MiB, GiB, TiB, PiB, EiB).
	 * <p>
	 * If {@code o} cannot be parsed as a number: returns zero.
	 *
	 * @param o byte count as a String or Number
	 * @return human-readable string (e.g., {@code "1.00 KiB"}), {@code "0 B"} for non-numeric, empty string {@code ""}
	 *         for null or blank
	 */
	@JawkFunction("bytes2HumanFormatBase2")
	public String bytes2HumanFormatBase2(Object o) {
		if (o == null || String.valueOf(o).isBlank()) {
			return "";
		}
		Double value = JRT.toDouble(o);
		String[] units = { "B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB" };
		return humanFormat(value, 1024.0, units);
	}

	/**
	 * Converts a byte count (base-10) given as a string to a human-readable form using
	 * SI units (B, KB, MB, GB, TB, PB, EB).
	 *
	 * @param o byte count as a String or Number
	 * @return human-readable string (e.g., {@code "1.00 MB"}), {@code "0 B"} for non-numeric, empty string {@code ""}
	 *         for null or blank
	 */
	@JawkFunction("bytes2HumanFormatBase10")
	public String bytes2HumanFormatBase10(Object o) {
		if (o == null || String.valueOf(o).isBlank()) {
			return "";
		}
		Double value = JRT.toDouble(o);
		String[] units = { "B", "KB", "MB", "GB", "TB", "PB", "EB" };
		return humanFormat(value, 1000.0, units);
	}

	/**
	 * Converts a quantity in mebibytes (MiB) given as a string to a human-readable form
	 * using IEC units (MiB, GiB, TiB, PiB, EiB).
	 *
	 * @param o size in MiB as a String or Number
	 * @return human-readable string (e.g., {@code "2.00 GiB"}), {@code "0 B"} for non-numeric, empty string {@code ""}
	 *         for null or blank
	 */
	@JawkFunction("mebiBytes2HumanFormat")
	public String mebiBytes2HumanFormat(Object o) {
		if (o == null || String.valueOf(o).isBlank()) {
			return "";
		}
		Double value = JRT.toDouble(o);
		String[] units = { "MiB", "GiB", "TiB", "PiB", "EiB" };
		return humanFormat(value, 1024.0, units);
	}

	/**
	 * Converts a frequency in megahertz (MHz) given as a string to a human-readable form
	 * using SI units (MHz, GHz, THz, PHz, EHz).
	 *
	 * @param o frequency in MHz as a String or Number
	 * @return human-readable string (e.g., {@code "3.20 GHz"}), {@code "0 B"} for non-numeric, empty string {@code ""}
	 *         for null or blank
	 */
	@JawkFunction("megaHertz2HumanFormat")
	public String megaHertz2HumanFormat(Object o) {
		if (o == null || String.valueOf(o).isBlank()) {
			return "";
		}
		Double value = JRT.toDouble(o);
		String[] units = { "MHz", "GHz", "THz", "PHz", "EHz" };
		return humanFormat(value, 1000.0, units);
	}

	/**
	 * Formats {@code value} with the best-fitting unit from {@code units}, dividing by {@code base}
	 * while {@code value >= base}. Uses two decimals.
	 *
	 * @param value numeric value to scale
	 * @param base 1000 (SI) or 1024 (IEC)
	 * @param units ordered units from smallest to largest
	 * @return formatted value with unit (e.g., "1.23 GiB")
	 */
	private String humanFormat(double value, double base, String[] units) {
		int i = 0;
		while (value >= base && i < units.length - 1) {
			value /= base;
			i++;
		}
		return String.format(getSettings().getLocale(), "%.2f %s", value, units[i]);
	}

	/**
	 * Joins strings with a separator.
	 *
	 * @param sep separator to place between parts
	 * @param parts String elements to be joined
	 * @return All parts joined with separator
	 */
	@JawkFunction("join")
	public String join(String sep, String... parts) {
		if (parts == null || parts.length == 0) {
			return "";
		}
		String actualSep = sep == null ? "" : sep;
		StringBuilder result = new StringBuilder(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			result.append(actualSep).append(parts[i] == null ? "" : parts[i]);
		}
		return result.toString();
	}
}
