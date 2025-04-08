package org.metricshub.extension.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Jawk Extension
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.metricshub.extension.jawk.KeyWords.EXECUTE_HTTP_REQUEST;
import static org.metricshub.extension.jawk.KeyWords.EXECUTE_IPMI_REQUEST;
import static org.metricshub.extension.jawk.KeyWords.EXECUTE_SNMP_GET;
import static org.metricshub.extension.jawk.KeyWords.EXECUTE_SNMP_TABLE;
import static org.metricshub.extension.jawk.KeyWords.EXECUTE_WBEM_REQUEST;
import static org.metricshub.extension.jawk.KeyWords.EXECUTE_WMI_REQUEST;
import static org.metricshub.extension.jawk.KeyWords.GET_VARIABLE;
import static org.metricshub.extension.jawk.KeyWords.JSON_2CSV;

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
import org.metricshub.jawk.NotImplementedError;
import org.metricshub.jawk.ext.AbstractExtension;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.jrt.AssocArray;

/**
 * This class implements the {@link JawkExtension} contract, reports the supported features, processes sources and computes.
 */
@Slf4j
@Builder
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
public class MetricsHubExtensionForJawk extends AbstractExtension implements JawkExtension {

	private SourceProcessor sourceProcessor;
	private String hostname;
	private String connectorId;

	@Override
	public String getExtensionName() {
		return "MetricsHubExtensionForJawk";
	}

	@Override
	public String[] extensionKeywords() {
		return new String[] {
			EXECUTE_HTTP_REQUEST,
			EXECUTE_IPMI_REQUEST,
			EXECUTE_SNMP_GET,
			EXECUTE_SNMP_TABLE,
			EXECUTE_WBEM_REQUEST,
			EXECUTE_WMI_REQUEST,
			JSON_2CSV,
			GET_VARIABLE
		};
	}

	@Override
	public int[] getAssocArrayParameterPositions(final String extensionKeyword, final int numArgs) {
		if (
			extensionKeyword.equals(EXECUTE_IPMI_REQUEST) ||
			extensionKeyword.equals(EXECUTE_SNMP_GET) ||
			extensionKeyword.equals(EXECUTE_SNMP_TABLE) ||
			extensionKeyword.equals(EXECUTE_WBEM_REQUEST) ||
			extensionKeyword.equals(EXECUTE_WMI_REQUEST) ||
			extensionKeyword.equals(EXECUTE_HTTP_REQUEST) ||
			extensionKeyword.equals(JSON_2CSV)
		) {
			return new int[] { 0 };
		} else if (extensionKeyword.equals(GET_VARIABLE)) {
			return new int[] {};
		} else {
			throw new NotImplementedError(extensionKeyword);
		}
	}

	@Override
	public Object invoke(String keyword, Object[] args) {
		if (keyword.equals(EXECUTE_HTTP_REQUEST)) {
			checkNumArgs(args, 1);
			return executeHttpRequest(args);
		} else if (keyword.equals(EXECUTE_IPMI_REQUEST)) {
			checkNumArgs(args, 1);
			return executeIpmiRequest(args);
		} else if (keyword.equals(EXECUTE_SNMP_GET)) {
			checkNumArgs(args, 1);
			return executeSnmpGetRequest(args);
		} else if (keyword.equals(EXECUTE_SNMP_TABLE)) {
			checkNumArgs(args, 1);
			return executeSnmpTableRequest(args);
		} else if (keyword.equals(EXECUTE_WBEM_REQUEST)) {
			checkNumArgs(args, 1);
			return executeWbemRequest(args);
		} else if (keyword.equals(EXECUTE_WMI_REQUEST)) {
			checkNumArgs(args, 1);
			return executeWmiRequest(args);
		} else if (keyword.equals(JSON_2CSV)) {
			checkNumArgs(args, 1);
			return executeJson2csv(args);
		} else if (keyword.equals(GET_VARIABLE)) {
			checkNumArgs(args, 1);
			return getVariable(args);
		} else {
			throw new NotImplementedError(keyword);
		}
	}

	/**
	 * Execute a HTTP request through the context.
	 * @param args The array of arguments to use to create the {@link HttpSource}.
	 * @return The table result from the execution of the {@link HttpSource}.
	 */
	private String executeHttpRequest(final Object[] args) {
		if ((args[0] instanceof AssocArray argsAssocArray)) {
			return executeSource(
				HttpSource
					.builder()
					.type("http")
					.method(HttpMethod.valueOf(toAwkString(argsAssocArray.get("method")).toUpperCase()))
					.path(toAwkString(argsAssocArray.get("path")))
					.header(toAwkString(argsAssocArray.get("header")))
					.body(toAwkString(argsAssocArray.get("body")))
					.authenticationToken(toAwkString(argsAssocArray.get("authenticationToken")))
					.resultContent(ResultContent.detect(toAwkString(argsAssocArray.get("resultContent"))))
					.forceSerialization(toAwkString(argsAssocArray.get("forceSerialization")).equals("true"))
					.build()
			);
		} else {
			log.warn("Hostname {} - Awk: executeHttpRequest(): expected Association array.", hostname);
			return "";
		}
	}

	/**
	 * Execute a Ipmi request through the context.
	 * @param args The array of arguments to use to create the {@link IpmiSource}.
	 * @return The table result from the execution of the {@link IpmiSource}.
	 */
	private String executeIpmiRequest(Object[] args) {
		if ((args[0] instanceof AssocArray argsAssocArray)) {
			return executeSource(
				IpmiSource
					.builder()
					.type("ipmi")
					.forceSerialization(toAwkString(argsAssocArray.get("forceSerialization")).equals("true"))
					.build()
			);
		} else {
			return executeSource(IpmiSource.builder().forceSerialization(false).build());
		}
	}

	/**
	 * Execute a Snmp Get request through the context.
	 * @param args The array of arguments to use to create the {@link SnmpGetSource}.
	 * @return The table result from the execution of the {@link SnmpGetSource}.
	 */
	private String executeSnmpGetRequest(final Object[] args) {
		if ((args[0] instanceof AssocArray argsAssocArray)) {
			return executeSource(
				SnmpGetSource
					.builder()
					.type("snmpGet")
					.oid(toAwkString(argsAssocArray.get("oid")))
					.forceSerialization(toAwkString(argsAssocArray.get("forceSerialization")).equals("true"))
					.build()
			);
		} else {
			log.warn("Hostname {} - Awk: executeSnmpGetRequest(): expected Association array.", hostname);
			return "";
		}
	}

	/**
	 * Execute a Snmp Table request through the context.
	 * @param args The array of arguments to use to create the {@link SnmpTableSource}.
	 * @return The table result from the execution of the {@link SnmpTableSource}.
	 */
	private String executeSnmpTableRequest(final Object[] args) {
		if ((args[0] instanceof AssocArray argsAssocArray)) {
			return executeSource(
				SnmpTableSource
					.builder()
					.type("snmpTable")
					.oid(toAwkString(argsAssocArray.get("oid")))
					.selectColumns(toAwkString(argsAssocArray.get("selectColumns")))
					.forceSerialization(toAwkString(argsAssocArray.get("forceSerialization")).equals("true"))
					.build()
			);
		} else {
			log.warn("Hostname {} - Awk: executeSnmpTableRequest(): expected Association array.", hostname);
			return "";
		}
	}

	/**
	 * Execute a Wbem request through the context.
	 * @param args The array of arguments to use to create the {@link WbemSource}.
	 * @return The table result from the execution of the {@link WbemSource}.
	 */
	private String executeWbemRequest(final Object[] args) {
		if ((args[0] instanceof AssocArray argsAssocArray)) {
			return executeSource(
				WbemSource
					.builder()
					.type("wbem")
					.query(toAwkString(argsAssocArray.get("query")))
					.namespace(toAwkString(argsAssocArray.get("namespace")))
					.forceSerialization(toAwkString(argsAssocArray.get("forceSerialization")).equals("true"))
					.build()
			);
		} else {
			log.warn("Hostname {} - Awk: executeWbemRequest(): expected Association array.", hostname);
			return "";
		}
	}

	/**
	 * Execute a Wmi request through the context.
	 * @param args The array of arguments to use to create the {@link WmiSource}.
	 * @return The table result from the execution of the {@link WmiSource}.
	 */
	private String executeWmiRequest(final Object[] args) {
		if ((args[0] instanceof AssocArray argsAssocArray)) {
			return executeSource(
				WmiSource
					.builder()
					.type("wmi")
					.query(toAwkString(argsAssocArray.get("query")))
					.namespace(toAwkString(argsAssocArray.get("namespace")))
					.forceSerialization(toAwkString(argsAssocArray.get("forceSerialization")).equals("true"))
					.build()
			);
		} else {
			log.warn("Hostname {} - Awk: executeWmiRequest(): expected Association array.", hostname);
			return "";
		}
	}

	/**
	 * Execute a {@link Source} through the context.
	 * @param source The {@link Source} to execute.
	 * @return The table result from the execution of the {@link Source}.
	 */
	private String executeSource(final Source source) {
		final SourceTable sourceTableResult = source.accept(sourceProcessor);

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
	 * @param args The array of arguments to use to create the {@link Json2Csv} compute.
	 * @return The table result from the execution of the compute.
	 */
	private String executeJson2csv(final Object[] args) {
		if ((args[0] instanceof AssocArray argsAssocArray)) {
			try {
				return ClientsExecutor
					.executeJson2Csv(
						toAwkString(argsAssocArray.get("jsonSource")),
						toAwkString(argsAssocArray.get("entryKey")),
						toAwkListString(argsAssocArray.get("properties")),
						toAwkString(argsAssocArray.get("separator")),
						hostname
					)
					.strip();
			} catch (Exception exception) {
				log.error(
					"Hostname {} - Json2Csv Operation has failed. Errors:\n{}\n",
					hostname,
					StringHelper.getStackMessages(exception)
				);
				return "";
			}
		} else {
			log.warn("Hostname {} - Awk: executeJson2csv(): expected Association array.", hostname);
			return "";
		}
	}

	/**
	 * Convert a Jawk variable to a {@link List} of {@link String}, assuming the Jawk variable contains a list of parameters separated by ';'.
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
	 * @param args The name of the variable to retrieve.
	 * @return The value of the variable.
	 */
	private String getVariable(final Object[] args) {
		final String variableName = toAwkString(args[0]);
		final ConnectorVariables connectorVariables = sourceProcessor
			.getTelemetryManager()
			.getHostConfiguration()
			.getConnectorVariables()
			.get(connectorId);
		if (connectorVariables != null) {
			return connectorVariables.getVariableValues().get(variableName);
		}
		return sourceProcessor
			.getTelemetryManager()
			.getConnectorStore()
			.getStore()
			.get(connectorId)
			.getConnectorIdentity()
			.getVariables()
			.get(variableName)
			.getDefaultValue();
	}
}
