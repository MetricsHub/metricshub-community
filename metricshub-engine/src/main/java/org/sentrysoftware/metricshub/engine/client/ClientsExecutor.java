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

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.springframework.util.Assert.isTrue;
import static org.springframework.util.Assert.notNull;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.http.HttpClient;
import org.sentrysoftware.http.HttpResponse;
import org.sentrysoftware.ipmi.client.IpmiClient;
import org.sentrysoftware.ipmi.client.IpmiClientConfiguration;
import org.sentrysoftware.jflat.JFlat;
import org.sentrysoftware.metricshub.engine.awk.AwkException;
import org.sentrysoftware.metricshub.engine.awk.AwkExecutor;
import org.sentrysoftware.metricshub.engine.client.http.Body;
import org.sentrysoftware.metricshub.engine.client.http.Header;
import org.sentrysoftware.metricshub.engine.client.http.HttpMacrosUpdater;
import org.sentrysoftware.metricshub.engine.client.http.HttpRequest;
import org.sentrysoftware.metricshub.engine.client.http.Url;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.exception.RetryableException;
import org.sentrysoftware.metricshub.engine.common.helpers.NetworkHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.StringHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.configuration.HttpConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IpmiConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.SnmpConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.TransportProtocols;
import org.sentrysoftware.metricshub.engine.configuration.WbemConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.WinRmConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.WmiConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.ResultContent;
import org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandHelper;
import org.sentrysoftware.metricshub.engine.strategy.utils.RetryOperation;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.snmp.client.SnmpClient;
import org.sentrysoftware.ssh.SshClient;
import org.sentrysoftware.tablejoin.TableJoin;
import org.sentrysoftware.vcenter.VCenterClient;
import org.sentrysoftware.wbem.client.WbemExecutor;
import org.sentrysoftware.wbem.client.WbemQueryResult;
import org.sentrysoftware.wbem.javax.wbem.WBEMException;
import org.sentrysoftware.winrm.WinRMHttpProtocolEnum;
import org.sentrysoftware.winrm.WindowsRemoteCommandResult;
import org.sentrysoftware.winrm.command.WinRMCommandExecutor;
import org.sentrysoftware.winrm.service.client.auth.AuthenticationEnum;
import org.sentrysoftware.winrm.wql.WinRMWqlExecutor;
import org.sentrysoftware.wmi.WmiHelper;
import org.sentrysoftware.wmi.WmiStringConverter;
import org.sentrysoftware.wmi.remotecommand.WinRemoteCommandExecutor;
import org.sentrysoftware.wmi.wbem.WmiWbemServices;
import org.sentrysoftware.xflat.XFlat;
import org.sentrysoftware.xflat.exceptions.XFlatException;

/**
 * The ClientsExecutor class provides utility methods for executing
 * various operations through Clients. It includes functionalities for executing
 * requests, running scripts, and handling general execution tasks. The
 * execution is done on a remote host, and various protocols, clients, and
 * utilities like AWK, HTTP, IPMI, JFlat, SNMP, SSH, TableJoin, VCenter, WBEM,
 * WMI, WinRM, and XFlat are supported.
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientsExecutor {

	private static final String MASK = "*****";
	private static final char[] CHAR_ARRAY_MASK = MASK.toCharArray();
	private static final String TIMEOUT_CANNOT_BE_NULL = "Timeout cannot be null";
	private static final String PASSWORD_CANNOT_BE_NULL = "Password cannot be null";
	private static final String USERNAME_CANNOT_BE_NULL = "Username cannot be null";
	private static final String HOSTNAME_CANNOT_BE_NULL = "hostname cannot be null";
	private static final String PROTOCOL_CANNOT_BE_NULL = "protocol cannot be null";

	private static final long JSON_2_CSV_TIMEOUT = 60; //seconds

	private static final String SSH_FILE_MODE = "0700";
	private static final String SSH_REMOTE_DIRECTORY = "/var/tmp/";

	private TelemetryManager telemetryManager;

	/**
	 * Run the given {@link Callable} using the passed timeout in seconds.
	 *
	 * @param <T>
	 * @param callable
	 * @param timeout
	 * @return {@link T} result returned by the callable.
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 */
	<T> T execute(final Callable<T> callable, final long timeout)
		throws InterruptedException, ExecutionException, TimeoutException {
		final ExecutorService executorService = Executors.newSingleThreadExecutor();
		try {
			final Future<T> handler = executorService.submit(callable);

			return handler.get(timeout, TimeUnit.SECONDS);
		} finally {
			executorService.shutdownNow();
		}
	}

	/**
	 * Execute SNMP GetNext request
	 *
	 * @param oid            The Object Identifier (OID) for the SNMP GETNEXT request.
	 * @param configuration  The SNMP configuration specifying parameters like version, community, etc.
	 * @param hostname       The hostname or IP address of the SNMP-enabled device.
	 * @param logMode        A boolean indicating whether to log errors and warnings during execution.
	 * @return The SNMP response as a String value.
	 * @throws InterruptedException If the execution is interrupted.
	 * @throws ExecutionException  If an exception occurs during execution.
	 * @throws TimeoutException    If the execution times out.
	 */
	@WithSpan("SNMP Get Next")
	public String executeSNMPGetNext(
		@NonNull @SpanAttribute("snmp.oid") final String oid,
		@NonNull @SpanAttribute("snmp.config") final SnmpConfiguration configuration,
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		final boolean logMode
	) throws InterruptedException, ExecutionException, TimeoutException {
		trace(() -> log.trace("Executing SNMP GetNext request:\n- OID: {}\n", oid));

		final long startTime = System.currentTimeMillis();

		String result = executeSNMPGetRequest(SnmpGetRequest.GETNEXT, oid, configuration, hostname, null, logMode);

		final long responseTime = System.currentTimeMillis() - startTime;

		trace(() ->
			log.trace(
				"Executed SNMP GetNext request:\n- OID: {}\n- Result: {}\n- response-time: {}\n",
				oid,
				result,
				responseTime
			)
		);

		return result;
	}

	/**
	 * Execute SNMP Get request
	 *
	 * @param oid            The Object Identifier (OID) for the SNMP GET request.
	 * @param configuration  The SNMP configuration specifying parameters like version, community, etc.
	 * @param hostname       The hostname or IP address of the SNMP-enabled device.
	 * @param logMode        A boolean indicating whether to log errors and warnings during execution.
	 * @return The SNMP response as a String value.
	 * @throws InterruptedException If the execution is interrupted.
	 * @throws ExecutionException  If an exception occurs during execution.
	 * @throws TimeoutException    If the execution times out.
	 */
	@WithSpan("SNMP Get")
	public String executeSNMPGet(
		@NonNull @SpanAttribute("snmp.oid") final String oid,
		@NonNull @SpanAttribute("snmp.config") final SnmpConfiguration configuration,
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		final boolean logMode
	) throws InterruptedException, ExecutionException, TimeoutException {
		trace(() -> log.trace("Executing SNMP Get request:\n- OID: {}\n", oid));

		final long startTime = System.currentTimeMillis();

		String result = executeSNMPGetRequest(SnmpGetRequest.GET, oid, configuration, hostname, null, logMode);

		final long responseTime = System.currentTimeMillis() - startTime;

		trace(() ->
			log.trace("Executed SNMP Get request:\n- OID: {}\n- Result: {}\n- response-time: {}\n", oid, result, responseTime)
		);

		return result;
	}

	/**
	 * Execute SNMP Table
	 *
	 * @param oid               The SNMP Object Identifier (OID) representing the table.
	 * @param selectColumnArray An array of column names to select from the SNMP table.
	 * @param configuration     The SNMP configuration containing connection details.
	 * @param hostname          The hostname or IP address of the SNMP-enabled device.
	 * @param logMode           Flag indicating whether to log warnings in case of errors.
	 * @return A list of rows, where each row is a list of string cells representing the SNMP table.
	 * @throws InterruptedException If the thread executing this method is interrupted.
	 * @throws ExecutionException  If an exception occurs during the execution of the SNMP request.
	 * @throws TimeoutException    If the SNMP request times out.
	 */
	@WithSpan("SNMP Get Table")
	public List<List<String>> executeSNMPTable(
		@NonNull @SpanAttribute("snmp.oid") final String oid,
		@NonNull @SpanAttribute("snmp.columns") String[] selectColumnArray,
		@NonNull @SpanAttribute("snmp.config") final SnmpConfiguration configuration,
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		final boolean logMode
	) throws InterruptedException, ExecutionException, TimeoutException {
		trace(() ->
			log.trace("Executing SNMP Table request:\n- OID: {}\n- Columns: {}\n", oid, Arrays.toString(selectColumnArray))
		);

		final long startTime = System.currentTimeMillis();

		List<List<String>> result = executeSNMPGetRequest(
			SnmpGetRequest.TABLE,
			oid,
			configuration,
			hostname,
			selectColumnArray,
			logMode
		);

		final long responseTime = System.currentTimeMillis() - startTime;

		trace(() ->
			log.trace(
				"Executed SNMP Table request:\n- OID: {}\n- Columns: {}\n- Result:\n{}\n- response-time: {}\n",
				oid,
				Arrays.toString(selectColumnArray),
				TextTableHelper.generateTextTable(selectColumnArray, result),
				responseTime
			)
		);

		return result;
	}

	/**
	 * Execute an SNMP Get request based on the specified SNMP Get Request type.
	 *
	 * @param request           The type of SNMP Get request (GET, GETNEXT, TABLE).
	 * @param oid               The SNMP Object Identifier (OID) for the request.
	 * @param protocol          The SNMP configuration containing connection details.
	 * @param hostname          The hostname or IP address of the SNMP-enabled device.
	 * @param selectColumnArray An array of column names for TABLE requests.
	 * @param logMode           Flag indicating whether to log warnings in case of errors.
	 * @return The result of the SNMP request, which can be a single value, a table, or null if an error occurs.
	 * @throws InterruptedException If the thread executing this method is interrupted.
	 * @throws ExecutionException  If an exception occurs during the execution of the SNMP request.
	 * @throws TimeoutException    If the SNMP request times out.
	 */
	@SuppressWarnings("unchecked")
	private <T> T executeSNMPGetRequest(
		final SnmpGetRequest request,
		final String oid,
		final SnmpConfiguration protocol,
		final String hostname,
		final String[] selectColumnArray,
		final boolean logMode
	) throws InterruptedException, ExecutionException, TimeoutException {
		// Create the SNMPClient and run the GetNext request
		return (T) execute(
			() -> {
				final SnmpClient snmpClient = new SnmpClient(
					hostname,
					protocol.getPort(),
					protocol.getVersion().getIntVersion(),
					null,
					protocol.getCommunity(),
					null,
					null,
					null,
					null,
					null,
					null,
					null
				);

				try {
					switch (request) {
						case GET:
							return snmpClient.get(oid);
						case GETNEXT:
							return snmpClient.getNext(oid);
						case TABLE:
							return snmpClient.table(oid, selectColumnArray);
						default:
							throw new IllegalArgumentException("Not implemented.");
					}
				} catch (Exception e) {
					if (logMode) {
						log.warn(
							"Hostname {} - Error detected when running SNMP {} Query OID: {}. Error message: {}.",
							hostname,
							request,
							oid,
							e.getMessage()
						);
					}
					return null;
				} finally {
					snmpClient.freeResources();
				}
			},
			protocol.getTimeout()
		);
	}

	/**
	 * Enum representing different types of SNMP requests.
	 * These requests are used to specify the type of SNMP operation
	 * when interacting with SNMP agents.
	 */
	public enum SnmpGetRequest {
		/**
		 * Represents an SNMP GET request.
		 * Used to retrieve the value of a single SNMP object.
		 */
		GET,
		/**
		 * Represents an SNMP GETNEXT request.
		 * Used to retrieve the value of the next SNMP object.
		 */
		GETNEXT,
		/**
		 * Represents an SNMP TABLE request.
		 * Used to retrieve a table of SNMP objects.
		 */
		TABLE
	}

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
		trace(() ->
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

		trace(() ->
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
	 * @throws InterruptedException  If the execution is interrupted.
	 */
	public String executeJson2Csv(String jsonSource, String jsonEntryKey, List<String> propertyList, String separator)
		throws InterruptedException, ExecutionException, TimeoutException {
		trace(() ->
			log.trace(
				"Executing JSON to CSV conversion:\n- Json-source:\n{}\n- Json-entry-key: {}\n" + // NOSONAR
				"- Property-list: {}\n- Separator: {}\n",
				jsonSource,
				jsonEntryKey,
				propertyList,
				separator
			)
		);

		final String hostname = telemetryManager.getHostConfiguration().getHostname();

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

		String result = execute(jflatToCSV, JSON_2_CSV_TIMEOUT);

		trace(() ->
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
		trace(() ->
			log.trace(
				"Executing XML parsing:\n- Xml-source:\n{}\n- Properties: {}\n- Record-tag: {}\n",
				xml,
				properties,
				recordTag
			)
		);

		List<List<String>> result = XFlat.parseXml(xml, properties, recordTag);

		trace(() ->
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

	/**
	 * Perform a WQL query, either against a CIM server (WBEM) or WMI
	 * <br>
	 *
	 * @param hostname      Hostname
	 * @param configuration The WbemConfiguration or WmiConfiguration object specifying how to connect to specified host
	 * @param query         WQL query to execute
	 * @param namespace     The namespace
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything wrong happens
	 */
	public List<List<String>> executeWql(
		final String hostname,
		final IConfiguration configuration,
		final String query,
		final String namespace
	) throws ClientException {
		if (configuration instanceof WbemConfiguration wbemConfiguration) {
			return executeWbem(hostname, wbemConfiguration, query, namespace);
		} else if (configuration instanceof WmiConfiguration wmiConfiguration) {
			return executeWmi(hostname, wmiConfiguration, query, namespace);
		} else if (configuration instanceof WinRmConfiguration winRmConfiguration) {
			return executeWqlThroughWinRm(hostname, winRmConfiguration, query, namespace);
		}

		throw new IllegalStateException("WQL queries can be executed only in WBEM, WMI and WinRM protocols.");
	}

	/**
	 * Perform a WQL remote command query, either against a CIM server (WBEM) or WMI
	 * <br>
	 *
	 * @param hostname      Hostname
	 * @param configuration The WbemConfiguration or WmiConfiguration object specifying how to connect to specified host
	 * @param command       Windows remote command to execute
	 * @param embeddedFiles The list of embedded files used in the wql remote command query
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything wrong happens
	 */
	public static String executeWinRemoteCommand(
		final String hostname,
		final IConfiguration configuration,
		final String command,
		final List<String> embeddedFiles
	) throws ClientException {
		if (configuration instanceof WmiConfiguration wmiConfiguration) {
			return executeWmiRemoteCommand(
				command,
				hostname,
				wmiConfiguration.getUsername(),
				wmiConfiguration.getPassword(),
				wmiConfiguration.getTimeout().intValue(),
				embeddedFiles
			);
		} else if (configuration instanceof WinRmConfiguration winRmConfiguration) {
			return executeRemoteWinRmCommand(hostname, winRmConfiguration, command);
		}

		throw new IllegalStateException("Windows commands can be executed only in WMI and WinRM protocols.");
	}

	/**
	 * Determine if a vCenter server is configured and call the appropriate method to run the WBEM query.
	 * <br>
	 *
	 * @param hostname   Hostname
	 * @param wbemConfig WBEM Protocol configuration, incl. credentials
	 * @param query      WQL query to execute
	 * @param namespace  WBEM namespace
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("WBEM")
	public List<List<String>> executeWbem(
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		@NonNull @SpanAttribute("wbem.config") final WbemConfiguration wbemConfig,
		@NonNull @SpanAttribute("wbem.query") final String query,
		@NonNull @SpanAttribute("wbem.namespace") final String namespace
	) throws ClientException {
		// handle vCenter case
		if (wbemConfig.getVCenter() != null) {
			return doVCenterQuery(hostname, wbemConfig, query, namespace);
		} else {
			return doWbemQuery(hostname, wbemConfig, query, namespace);
		}
	}

	/**
	 * Perform a WBEM query using vCenter ticket authentication.
	 * <br>
	 *
	 * @param hostname   Hostname
	 * @param wbemConfig WBEM Protocol configuration, incl. credentials
	 * @param query      WQL query to execute
	 * @param namespace  WBEM namespace
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	private List<List<String>> doVCenterQuery(
		@NonNull final String hostname,
		@NonNull final WbemConfiguration wbemConfig,
		@NonNull final String query,
		@NonNull final String namespace
	) throws ClientException {
		String ticket = telemetryManager.getHostProperties().getVCenterTicket();

		if (ticket == null) {
			ticket =
				refreshVCenterTicket(
					wbemConfig.getVCenter(),
					wbemConfig.getUsername(),
					wbemConfig.getPassword(),
					hostname,
					wbemConfig.getTimeout()
				);
		}

		final WbemConfiguration vCenterWbemConfig = WbemConfiguration
			.builder()
			.username(ticket)
			.password(ticket.toCharArray())
			.namespace(wbemConfig.getNamespace())
			.port(wbemConfig.getPort())
			.protocol(wbemConfig.getProtocol())
			.timeout(wbemConfig.getTimeout())
			.build();

		try {
			return doWbemQuery(hostname, vCenterWbemConfig, query, namespace);
		} catch (ClientException e) {
			if (isRefreshTicketNeeded(e.getCause())) {
				ticket =
					refreshVCenterTicket(
						wbemConfig.getVCenter(),
						wbemConfig.getUsername(),
						wbemConfig.getPassword(),
						hostname,
						wbemConfig.getTimeout()
					);
				vCenterWbemConfig.setUsername(ticket);
				vCenterWbemConfig.setPassword(ticket.toCharArray());
				return doWbemQuery(hostname, vCenterWbemConfig, query, namespace);
			} else {
				throw e;
			}
		} finally {
			telemetryManager.getHostProperties().setVCenterTicket(ticket);
		}
	}

	/**
	 * Perform a query to a vCenterServer in order to obtain an authentication ticket.
	 * <br>
	 *
	 * @param vCenter  vCenter server FQDN or IP
	 * @param username Username
	 * @param password Password
	 * @param hostname Hostname
	 * @param timeout  Timeout
	 * @return A ticket String
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	private String refreshVCenterTicket(
		@NonNull String vCenter,
		@NonNull String username,
		@NonNull char[] password,
		@NonNull String hostname,
		@NonNull Long timeout
	) throws ClientException {
		VCenterClient.setDebug(() -> true, log::debug);
		try {
			String ticket = execute(
				() -> VCenterClient.requestCertificate(vCenter, username, new String(password), hostname),
				timeout
			);
			if (ticket == null) {
				throw new ClientException("Cannot get the ticket through vCenter module");
			}
			return ticket;
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.error("Hostname {} - Vcenter ticket refresh query failed. Exception: {}", e);
			throw new ClientException("vCenter refresh ticket query failed on " + hostname + ".", e);
		}
	}

	/**
	 * Assess whether the exception (or any of its causes) is an access denied error saying that we must refresh the vCenter ticket.
	 * <br>
	 *
	 * @param t Exception to verify
	 * @return whether specified exception tells us that the ticket needs to be refreshed
	 */
	private static boolean isRefreshTicketNeeded(Throwable t) {
		if (t == null) {
			return false;
		}

		if (t instanceof WBEMException wbemException) {
			final int cimErrorType = wbemException.getID();
			return cimErrorType == WBEMException.CIM_ERR_ACCESS_DENIED;
		}

		// Now check recursively the cause
		return isRefreshTicketNeeded(t.getCause());
	}

	/**
	 * Perform a WBEM query.
	 * <br>
	 *
	 * @param hostname   Hostname
	 * @param wbemConfig WBEM Protocol configuration, incl. credentials
	 * @param query      WQL query to execute
	 * @param namespace  WBEM namespace
	 * @return A table (as a {@link List} of {@link List} of {@link String}s)
	 * resulting from the execution of the query.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	private List<List<String>> doWbemQuery(
		final String hostname,
		final WbemConfiguration wbemConfig,
		final String query,
		final String namespace
	) throws ClientException {
		try {
			String urlSpec = String.format("%s://%s:%d", wbemConfig.getProtocol().toString(), hostname, wbemConfig.getPort());

			final URL url = new URI(urlSpec).toURL();

			trace(() ->
				log.trace(
					"Executing WBEM request:\n- Hostname: {}\n- Port: {}\n- Protocol: {}\n- URL: {}\n" + // NOSONAR
					"- Username: {}\n- Query: {}\n- Namespace: {}\n- Timeout: {} s\n",
					hostname,
					wbemConfig.getPort(),
					wbemConfig.getProtocol().toString(),
					url,
					wbemConfig.getUsername(),
					query,
					namespace,
					wbemConfig.getTimeout()
				)
			);

			final long startTime = System.currentTimeMillis();

			WbemQueryResult wbemQueryResult = WbemExecutor.executeWql(
				url,
				namespace,
				wbemConfig.getUsername(),
				wbemConfig.getPassword(),
				query,
				wbemConfig.getTimeout().intValue() * 1000,
				null
			);

			final long responseTime = System.currentTimeMillis() - startTime;

			List<List<String>> result = wbemQueryResult.getValues();

			trace(() ->
				log.trace(
					"Executed WBEM request:\n- Hostname: {}\n- Port: {}\n- Protocol: {}\n- URL: {}\n" + // NOSONAR
					"- Username: {}\n- Query: {}\n- Namespace: {}\n- Timeout: {} s\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					wbemConfig.getPort(),
					wbemConfig.getProtocol().toString(),
					url,
					wbemConfig.getUsername(),
					query,
					namespace,
					wbemConfig.getTimeout(),
					TextTableHelper.generateTextTable(wbemQueryResult.getProperties(), result),
					responseTime
				)
			);

			return result;
		} catch (Exception e) { // NOSONAR an exception is already thrown
			throw new ClientException("WBEM query failed on " + hostname + ".", e);
		}
	}

	/**
	 * Execute a WMI query
	 *
	 * @param hostname  The hostname of the device where the WMI service is running (<code>null</code> for localhost)
	 * @param wmiConfig WMI Protocol configuration (credentials, timeout)
	 * @param wbemQuery The WQL to execute
	 * @param namespace The WBEM namespace where all the classes reside
	 * @return A list of rows, where each row is represented as a list of strings.
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("WMI")
	public List<List<String>> executeWmi(
		@SpanAttribute("host.hostname") final String hostname,
		@SpanAttribute("wmi.config") @NonNull final WmiConfiguration wmiConfig,
		@SpanAttribute("wmi.query") @NonNull final String wbemQuery,
		@SpanAttribute("wmi.namespace") @NonNull final String namespace
	) throws ClientException {
		// Where to connect to?
		// Local: namespace
		// Remote: hostname\namespace
		final String networkResource = NetworkHelper.isLocalhost(hostname)
			? namespace
			: String.format("\\\\%s\\%s", hostname, namespace);

		trace(() ->
			log.trace(
				"Executing WMI request:\n- Hostname: {}\n- Network-resource: {}\n- Username: {}\n- Query: {}\n" + // NOSONAR
				"- Namespace: {}\n- Timeout: {} s\n",
				hostname,
				networkResource,
				wmiConfig.getUsername(),
				wbemQuery,
				namespace,
				wmiConfig.getTimeout()
			)
		);

		// Go!
		try (
			WmiWbemServices wbemServices = WmiWbemServices.getInstance(
				networkResource,
				wmiConfig.getUsername(),
				wmiConfig.getPassword()
			)
		) {
			final long startTime = System.currentTimeMillis();

			// Execute the WQL and get the result
			final List<Map<String, Object>> result = wbemServices.executeWql(wbemQuery, wmiConfig.getTimeout() * 1000);

			final long responseTime = System.currentTimeMillis() - startTime;

			// Extract the exact property names (case sensitive), in the right order
			final List<String> properties = WmiHelper.extractPropertiesFromResult(result, wbemQuery);

			// Build the table
			List<List<String>> resultTable = buildWmiTable(result, properties);

			trace(() ->
				log.trace(
					"Executed WMI request:\n- Hostname: {}\n- Network-resource: {}\n- Username: {}\n- Query: {}\n" + // NOSONAR
					"- Namespace: {}\n- Timeout: {} s\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					networkResource,
					wmiConfig.getUsername(),
					wbemQuery,
					namespace,
					wmiConfig.getTimeout(),
					TextTableHelper.generateTextTable(properties, resultTable),
					responseTime
				)
			);

			return resultTable;
		} catch (Exception e) {
			throw new ClientException("WMI query failed on " + hostname + ".", e);
		}
	}

	/**
	 * Execute a command on a remote Windows system through Client and return an object with
	 * the output of the command.
	 *
	 * @param command    The command to execute. (Mandatory)
	 * @param hostname   The host to connect to.  (Mandatory)
	 * @param username   The username.
	 * @param password   The password.
	 * @param timeout    Timeout in seconds
	 * @param localFiles The local files list
	 * @return The output of the executed command.
	 * @throws ClientException For any problem encountered.
	 */
	@WithSpan("Remote Command WMI")
	public static String executeWmiRemoteCommand(
		@SpanAttribute("wmi.command") final String command,
		@SpanAttribute("host.hostname") final String hostname,
		@SpanAttribute("wmi.username") final String username,
		final char[] password,
		@SpanAttribute("wmi.timeout") final int timeout,
		@SpanAttribute("wmi.local_files") final List<String> localFiles
	) throws ClientException {
		try {
			trace(() ->
				log.trace(
					"Executing WMI remote command:\n- Command: {}\n- Hostname: {}\n- Username: {}\n" + // NOSONAR
					"- Timeout: {} s\n- Local-files: {}\n",
					command,
					hostname,
					username,
					timeout,
					localFiles
				)
			);

			final long startTime = System.currentTimeMillis();

			final WinRemoteCommandExecutor result = WinRemoteCommandExecutor.execute(
				command,
				hostname,
				username,
				password,
				null,
				timeout * 1000L,
				localFiles,
				true
			);

			String resultStdout = result.getStdout();

			final long responseTime = System.currentTimeMillis() - startTime;

			trace(() ->
				log.trace(
					"Executed WMI remote command:\n- Command: {}\n- Hostname: {}\n- Username: {}\n" + // NOSONAR
					"- Timeout: {} s\n- Local-files: {}\n- Result:\n{}\n- response-time: {}\n",
					command,
					hostname,
					username,
					timeout,
					localFiles,
					resultStdout,
					responseTime
				)
			);

			return resultStdout;
		} catch (Exception e) {
			throw new ClientException((Exception) e.getCause());
		}
	}

	/**
	 * Convert the given result to a {@link List} of {@link List} table
	 *
	 * @param result     The result we want to process
	 * @param properties The ordered properties
	 * @return {@link List} of {@link List} table
	 */
	List<List<String>> buildWmiTable(final List<Map<String, Object>> result, final List<String> properties) {
		final List<List<String>> table = new ArrayList<>();
		final WmiStringConverter stringConverter = new WmiStringConverter();

		// Transform the result to a list of list
		result.forEach(row -> {
			final List<String> line = new ArrayList<>();

			// loop over the right order
			properties.forEach(property -> line.add(stringConverter.convert(row.get(property))));

			// We have a line?
			if (!line.isEmpty()) {
				table.add(line);
			}
		});
		return table;
	}

	/**
	 * Executes the given HTTP request
	 *
	 * @param httpRequest The {@link HttpRequest} values.
	 * @param logMode     Whether or not logging is enabled.
	 * @return The result of the execution of the given HTTP request.
	 */
	@WithSpan("HTTP")
	public String executeHttp(@SpanAttribute("http.config") @NonNull HttpRequest httpRequest, boolean logMode) {
		final HttpConfiguration httpConfiguration = httpRequest.getHttpConfiguration();
		notNull(httpConfiguration, PROTOCOL_CANNOT_BE_NULL);

		final String hostname = httpRequest.getHostname();
		notNull(hostname, HOSTNAME_CANNOT_BE_NULL);

		// Get the HTTP method (GET, POST, DELETE, PUT, ...). Default: GET
		String requestMethod = httpRequest.getMethod();
		final String method = requestMethod != null ? requestMethod : "GET";

		// Username and password
		final String username = httpConfiguration.getUsername();
		final char[] password = httpConfiguration.getPassword();

		// Update macros in the authentication token
		final String httpRequestAuthToken = httpRequest.getAuthenticationToken();
		final String authenticationToken = HttpMacrosUpdater.update(
			httpRequestAuthToken,
			username,
			password,
			httpRequestAuthToken,
			hostname
		);

		// Get the header to send
		final Header header = httpRequest.getHeader();

		// This will get the header content as a new map by updating all the known macros
		final Map<String, String> headerContent = header == null
			? Collections.emptyMap()
			: header.getContent(username, password, authenticationToken, hostname);

		// This will get the header content as a new map by updating all the known macros
		// except sensitive data such as password and authentication token
		final Map<String, String> headerContentProtected = header == null
			? Collections.emptyMap()
			: header.getContent(username, CHAR_ARRAY_MASK, MASK, hostname);

		// Get the body to send
		final Body body = httpRequest.getBody();

		// This will get the body content as map by updating all the known macros
		final String bodyContent = body == null
			? EMPTY
			: body.getContent(username, password, authenticationToken, hostname);

		// This will get the body content as map by updating all the known macros
		// except sensitive data such as password and authentication token
		final String bodyContentProtected = body == null
			? EMPTY
			: body.getContent(username, CHAR_ARRAY_MASK, MASK, hostname);

		// Set the protocol http or https
		final String protocol = Boolean.TRUE.equals(httpConfiguration.getHttps()) ? "https" : "http";

		// Get the HTTP request URL
		final String httpRequestUrl = httpRequest.getUrl();

		// Get the HTTP request Path
		final String httpRequestPath = httpRequest.getPath();

		// Update the known HTTP macros, and return empty if the httpRequestPath is null
		final String path = HttpMacrosUpdater.update(httpRequestPath, username, password, authenticationToken, hostname);

		// Update the known HTTP macros, and return empty if the httpRequestUrl is null
		final String url = HttpMacrosUpdater.update(httpRequestUrl, username, password, authenticationToken, hostname);

		// Build the full URL
		final String fullUrl = Url.format(protocol, hostname, httpConfiguration.getPort(), path, url);

		trace(() ->
			log.trace(
				"Executing HTTP request: {} {}\n- hostname: {}\n- url: {}\n- path: {}\n" + // NOSONAR
				"- Protocol: {}\n- Port: {}\n" +
				"- Request-headers:\n{}\n- Request-body:\n{}\n- Timeout: {} s\n- Get-result-content: {}\n",
				method,
				fullUrl,
				hostname,
				url,
				path,
				protocol,
				httpConfiguration.getPort(),
				StringHelper.prettyHttpHeaders(headerContentProtected),
				bodyContentProtected,
				httpConfiguration.getTimeout().intValue(),
				httpRequest.getResultContent()
			)
		);

		return RetryOperation
			.<String>builder()
			.withDescription(String.format("%s %s", method, fullUrl))
			.withWaitStrategy((int) telemetryManager.getHostConfiguration().getRetryDelay())
			.withMaxRetries(1)
			.withHostname(hostname)
			.withDefaultValue(EMPTY)
			.build()
			.run(() ->
				doHttpRequest(
					httpRequest.getResultContent(),
					logMode,
					httpConfiguration,
					hostname,
					method,
					username,
					password,
					headerContent,
					headerContentProtected,
					bodyContent,
					bodyContentProtected,
					url,
					path,
					protocol,
					fullUrl
				)
			);
	}

	/**
	 * Execute the HTTP request
	 *
	 * @param resultContent          Which result should be returned. E.g. HTTP status, body, header or all
	 * @param logMode                Whether or not logging is enabled
	 * @param httpConfiguration      HTTP protocol configuration
	 * @param hostname               The device hostname
	 * @param method                 The HTTP method: GET, POST, DELETE, ...etc.
	 * @param username               The HTTP server username
	 * @param password               The HTTP server password
	 * @param headerContent          The HTTP header
	 * @param headerContentProtected The HTTP header without sensitive information
	 * @param bodyContent            The HTTP body
	 * @param bodyContentProtected   The HTTP body without sensitive information
	 * @param url                    The HTTP URL
	 * @param path                    The HTTP URL path
	 * @param protocol               The protocol: http or https
	 * @param fullUrl                The full HTTP URL. E.g. <pre>http://www.example.com:1080/api/v1/examples</pre>
	 * @return String value
	 */
	private String doHttpRequest(
		final ResultContent resultContent,
		final boolean logMode,
		final HttpConfiguration httpConfiguration,
		final String hostname,
		final String method,
		final String username,
		final char[] password,
		final Map<String, String> headerContent,
		final Map<String, String> headerContentProtected,
		final String bodyContent,
		final String bodyContentProtected,
		final String url,
		final String path,
		final String protocol,
		final String fullUrl
	) {
		try {
			final long startTime = System.currentTimeMillis();

			// Sending the request
			HttpResponse httpResponse = sendHttpRequest(
				fullUrl,
				method,
				username,
				password,
				headerContent,
				bodyContent,
				httpConfiguration.getTimeout().intValue()
			);

			// Compute the response time
			final long responseTime = System.currentTimeMillis() - startTime;

			// The request returned an error
			final int statusCode = httpResponse.getStatusCode();
			if (statusCode >= HTTP_BAD_REQUEST) {
				log.warn("Hostname {} - Bad response for HTTP request {} {}: {}.", hostname, method, fullUrl, statusCode);

				// Retry the request when receiving the following HTTP statuses
				// 500 Internal Server Error
				// 503 Service Unavailable
				// 504 Gateway Timeout
				// 507 Insufficient Storage
				if (statusCode == 500 || statusCode == 503 || statusCode == 504 || statusCode == 507) {
					throw new RetryableException();
				}

				return "";
			}

			// The request has been successful
			String result;
			switch (resultContent) {
				case BODY:
					result = httpResponse.getBody();
					break;
				case HEADER:
					result = httpResponse.getHeader();
					break;
				case HTTP_STATUS:
					result = String.valueOf(statusCode);
					break;
				case ALL:
					result = httpResponse.toString();
					break;
				default:
					throw new IllegalArgumentException("Unsupported ResultContent: " + resultContent);
			}

			trace(() ->
				log.trace(
					"Executed HTTP request: {} {}\n- Hostname: {}\n- Url: {}\n- Path: {}\n- Protocol: {}\n- Port: {}\n" + // NOSONAR
					"- Request-headers:\n{}\n- Request-body:\n{}\n- Timeout: {} s\n" +
					"- get-result-content: {}\n- response-status: {}\n- response-headers:\n{}\n" +
					"- response-body:\n{}\n- response-time: {}\n",
					method,
					fullUrl,
					hostname,
					url,
					path,
					protocol,
					httpConfiguration.getPort(),
					StringHelper.prettyHttpHeaders(headerContentProtected),
					bodyContentProtected,
					httpConfiguration.getTimeout().intValue(),
					resultContent,
					statusCode,
					httpResponse.getHeader(),
					httpResponse.getBody(),
					responseTime
				)
			);

			return result;
		} catch (IOException e) {
			if (logMode) {
				log.error(
					"Hostname {} - Error detected when running HTTP request {} {}: {}\nReturning null.",
					hostname,
					method,
					fullUrl,
					e.getMessage()
				);

				log.debug("Hostname {} - Exception detected when running HTTP request {} {}:", hostname, method, fullUrl, e);
			}
		}

		return null;
	}

	/**
	 * @param url           The full URL of the HTTP request.
	 * @param method        The HTTP method (GET, POST, ...).
	 * @param username      The username for the connexion.
	 * @param password      The password for the connexion.
	 * @param headerContent The {@link Map} of properties-values in the header.
	 * @param bodyContent   The body as a plain text.
	 * @param timeout       The timeout of the request.
	 * @return The {@link HttpResponse} returned by the server.
	 * @throws IOException If a reading or writing operation fails.
	 */
	private HttpResponse sendHttpRequest(
		String url,
		String method,
		String username,
		char[] password,
		Map<String, String> headerContent,
		String bodyContent,
		int timeout
	) throws IOException {
		return HttpClient.sendRequest(
			url,
			method,
			null,
			username,
			password,
			null,
			0,
			null,
			null,
			null,
			headerContent,
			bodyContent,
			timeout,
			null
		);
	}

	/**
	 * Use ssh-client in order to run ssh command.
	 *
	 * @param hostname         The hostname or IP address to connect to.
	 * @param username         The SSH username.
	 * @param password         The SSH password as a character array.
	 * @param keyFilePath      The path to the SSH key file.
	 * @param command          The SSH command to execute.
	 * @param timeout          The timeout for the command execution in seconds.
	 * @param localFiles       List of local files to be transferred to the remote host.
	 * @param noPasswordCommand The command to execute without password.
	 * @return The result of the SSH command.
	 * @throws ClientException If an error occurs during the SSH command execution.
	 */
	@WithSpan("SSH")
	public static String runRemoteSshCommand(
		@NonNull @SpanAttribute("host.hostname") final String hostname,
		@NonNull @SpanAttribute("ssh.username") final String username,
		final char[] password,
		@SpanAttribute("ssh.key_file_path") final File keyFilePath,
		final String command,
		@SpanAttribute("ssh.timeout") final long timeout,
		@SpanAttribute("ssh.local_files") final List<File> localFiles,
		@SpanAttribute("ssh.command") final String noPasswordCommand
	) throws ClientException {
		trace(() ->
			log.trace(
				"Executing Remote SSH command:\n- hostname: {}\n- username: {}\n- key-file-path: {}\n" + // NOSONAR
				"- command: {}\n- timeout: {} s\n- local-files: {}\n",
				hostname,
				username,
				keyFilePath,
				command,
				timeout,
				localFiles
			)
		);

		isTrue(command != null && !command.trim().isEmpty(), "Command cannot be null nor empty.");
		isTrue(timeout > 0, "Timeout cannot be negative nor zero.");
		final long timeoutInMilliseconds = timeout * 1000;

		final String updatedCommand = updateCommandWithLocalList(command, localFiles);

		final String noPasswordUpdatedCommand = noPasswordCommand == null
			? updatedCommand
			: updateCommandWithLocalList(noPasswordCommand, localFiles);

		// We have a command: execute it
		try (SshClient sshClient = createSshClientInstance(hostname)) {
			sshClient.connect((int) timeoutInMilliseconds);

			if (password == null) {
				log.warn("Hostname {} - Password could not be read. Using an empty password instead.", hostname);
			}

			authenticateSsh(sshClient, hostname, username, password, keyFilePath);

			if (localFiles != null && !localFiles.isEmpty()) {
				// copy all local files using SCP
				for (final File file : localFiles) {
					sshClient.scp(file.getAbsolutePath(), file.getName(), SSH_REMOTE_DIRECTORY, SSH_FILE_MODE);
				}
			}

			final long startTime = System.currentTimeMillis();

			final SshClient.CommandResult commandResult = sshClient.executeCommand(
				updatedCommand,
				(int) timeoutInMilliseconds
			);

			final long responseTime = System.currentTimeMillis() - startTime;

			if (!commandResult.success) {
				final String message = String.format(
					"Hostname %s - Command \"%s\" failed with result %s.",
					hostname,
					noPasswordUpdatedCommand,
					commandResult.result
				);
				log.error(message);
				throw new ClientException(message);
			}

			String result = commandResult.result;

			trace(() ->
				log.trace(
					"Executed Remote SSH command:\n- Hostname: {}\n- Username: {}\n- Key-file-path: {}\n" + // NOSONAR
					"- Command: {}\n- Timeout: {} s\n- Local-files: {}\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					username,
					keyFilePath,
					command,
					timeout,
					localFiles,
					result,
					responseTime
				)
			);
			return result;
		} catch (final ClientException e) {
			throw e;
		} catch (final Exception e) {
			final String message = String.format(
				"Failed to run SSH command \"%s\" as %s on %s.",
				noPasswordUpdatedCommand,
				username,
				hostname
			);
			log.error("Hostname {} - {}. Exception : {}.", hostname, message, e.getMessage());
			throw new ClientException(message, (Exception) e.getCause());
		}
	}

	/**
	 * Authenticate SSH with:
	 * <ul>
	 * 	<li>username, privateKey and password first</li>
	 * 	<li>username and password</li>
	 * 	<li>username only</li>
	 * </ul>
	 *
	 * @param sshClient  The SSH client
	 * @param hostname   The hostname
	 * @param username   The username
	 * @param password   The password
	 * @param privateKey The private key file
	 * @throws ClientException If an error occurred.
	 */
	static void authenticateSsh(
		final SshClient sshClient,
		final String hostname,
		final String username,
		final char[] password,
		final File privateKey
	) throws ClientException {
		final boolean authenticated;
		try {
			if (privateKey != null) {
				authenticated = sshClient.authenticate(username, privateKey, password);
			} else if (password != null && password.length > 0) {
				authenticated = sshClient.authenticate(username, password);
			} else {
				authenticated = sshClient.authenticate(username);
			}
		} catch (final Exception e) {
			final String message = String.format(
				"Hostname %s - Authentication as %s has failed with %s.",
				hostname,
				username,
				privateKey != null ? privateKey.getAbsolutePath() : null
			);
			log.error("Hostname {} - {}. Exception : {}.", hostname, message, e.getMessage());
			throw new ClientException(message, e);
		}

		if (!authenticated) {
			final String message = String.format(
				"Hostname %s - Authentication as %s has failed with %s.",
				hostname,
				username,
				privateKey != null ? privateKey.getAbsolutePath() : null
			);
			log.error(message);
			throw new ClientException(message);
		}
	}

	/**
	 * Replace in the SSH command all the local files path with their remote path.
	 *
	 * @param command    The SSH command.
	 * @param localFiles The local files list.
	 * @return The updated command.
	 */
	static String updateCommandWithLocalList(final String command, final List<File> localFiles) {
		return localFiles == null || localFiles.isEmpty()
			? command
			: localFiles
				.stream()
				.reduce(
					command,
					(s, file) ->
						command.replaceAll(
							OsCommandHelper.toCaseInsensitiveRegex(file.getAbsolutePath()),
							SSH_REMOTE_DIRECTORY + file.getName()
						),
					(s1, s2) -> null
				);
	}

	/**
	 * Creates a new instance of the {@link SshClient}.
	 *
	 * @param hostname The hostname.
	 * @return A {@link SshClient} instance.
	 */
	public static SshClient createSshClientInstance(final String hostname) {
		return new SshClient(hostname, StandardCharsets.UTF_8);
	}

	/**
	 * Connect to the SSH terminal. For that:
	 * <ul>
	 * 	<li>Create an SSH Client instance.</li>
	 * 	<li>Connect to SSH.</li>
	 * 	<li>Open a SSH session.</li>
	 * 	<li>Open a terminal.</li>
	 * </ul>
	 *
	 * @param hostname   The hostname (mandatory)
	 * @param username   The username (mandatory)
	 * @param password   The password
	 * @param privateKey The private key file
	 * @param timeout    The timeout (>0) in seconds
	 * @return The SSH client
	 * @throws ClientException If a Client error occurred.
	 */
	public static SshClient connectSshClientTerminal(
		@NonNull final String hostname,
		@NonNull final String username,
		final char[] password,
		final File privateKey,
		final int timeout
	) throws ClientException {
		isTrue(timeout > 0, "timeout must be > 0");

		final SshClient sshClient = createSshClientInstance(hostname);

		try {
			sshClient.connect(timeout * 1000);

			authenticateSsh(sshClient, hostname, username, password, privateKey);

			sshClient.openSession();

			sshClient.openTerminal();

			return sshClient;
		} catch (final IOException e) {
			sshClient.close();
			throw new ClientException(e);
		}
	}

	/**
	 * Runs IPMI detection to determine the Chassis power state.
	 *
	 * @param hostname          The host name or IP address to query.
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance with required fields for IPMI requests.
	 * @return A string value, e.g., "System power state is up."
	 * @throws InterruptedException If the execution is interrupted.
	 * @throws ExecutionException   If the execution encounters an exception.
	 * @throws TimeoutException     If the operation times out.
	 */
	@WithSpan("IPMI Chassis Status")
	public String executeIpmiDetection(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration
	) throws InterruptedException, ExecutionException, TimeoutException {
		trace(() ->
			log.trace(
				"Executing IPMI detection request:\n- Hostname: {}\n- Username: {}\n- SkipAuth: {}\n" + "- Timeout: {} s\n", // NOSONAR
				hostname,
				ipmiConfiguration.getUsername(),
				ipmiConfiguration.isSkipAuth(),
				ipmiConfiguration.getTimeout()
			)
		);

		final long startTime = System.currentTimeMillis();

		final String result = IpmiClient.getChassisStatusAsStringResult(
			buildIpmiConfiguration(hostname, ipmiConfiguration)
		);

		final long responseTime = System.currentTimeMillis() - startTime;

		trace(() ->
			log.trace(
				"Executed IPMI detection request:\n- Hostname: {}\n- Username: {}\n- SkipAuth: {}\n" + // NOSONAR
				"- Timeout: {} s\n- Result:\n{}\n- response-time: {}\n",
				hostname,
				ipmiConfiguration.getUsername(),
				ipmiConfiguration.isSkipAuth(),
				ipmiConfiguration.getTimeout(),
				result,
				responseTime
			)
		);

		return result;
	}

	/**
	 * Build IPMI configuration
	 *
	 * @param hostname          The host we wish to set in the {@link IpmiConfiguration}
	 * @param ipmiConfiguration MetricsHub {@link IpmiConfiguration} instance including all the required fields to perform IPMI requests
	 * @return new instance of {@link IpmiClientConfiguration}
	 */
	private static IpmiClientConfiguration buildIpmiConfiguration(
		@NonNull String hostname,
		@NonNull IpmiConfiguration ipmiConfiguration
	) {
		String username = ipmiConfiguration.getUsername();
		char[] password = ipmiConfiguration.getPassword();
		Long timeout = ipmiConfiguration.getTimeout();

		notNull(username, USERNAME_CANNOT_BE_NULL);
		notNull(password, PASSWORD_CANNOT_BE_NULL);
		notNull(timeout, TIMEOUT_CANNOT_BE_NULL);

		return new IpmiClientConfiguration(
			hostname,
			username,
			password,
			ipmiConfiguration.getBmcKey(),
			ipmiConfiguration.isSkipAuth(),
			timeout
		);
	}

	/**
	 * Executes an IPMI Over-LAN request to retrieve information about all sensors.
	 *
	 * @param hostname          The host for which the {@link IpmiConfiguration} is set.
	 * @param ipmiConfiguration The MetricsHub {@link IpmiConfiguration} instance containing the required fields for IPMI requests.
	 * @return A string containing information about FRUs, sensor states, and readings.
	 * @throws InterruptedException If the execution is interrupted.
	 * @throws ExecutionException   If the execution encounters an exception.
	 * @throws TimeoutException     If the operation times out.
	 */
	@WithSpan("IPMI Sensors")
	public String executeIpmiGetSensors(
		@SpanAttribute("host.hostname") String hostname,
		@SpanAttribute("ipmi.config") @NonNull IpmiConfiguration ipmiConfiguration
	) throws InterruptedException, ExecutionException, TimeoutException {
		trace(() ->
			log.trace(
				"Executing IPMI FRUs and Sensors request:\n- Hostname: {}\n- Username: {}\n- SkipAuth: {}\n" + // NOSONAR
				"- Timeout: {} s\n",
				hostname,
				ipmiConfiguration.getUsername(),
				ipmiConfiguration.isSkipAuth(),
				ipmiConfiguration.getTimeout()
			)
		);

		final long startTime = System.currentTimeMillis();

		String result = IpmiClient.getFrusAndSensorsAsStringResult(buildIpmiConfiguration(hostname, ipmiConfiguration));

		final long responseTime = System.currentTimeMillis() - startTime;

		trace(() ->
			log.trace(
				"Executed IPMI FRUs and Sensors request:\n- Hostname: {}\n- Username: {}\n- SkipAuth: {}\n" + // NOSONAR
				"- Timeout: {} s\n- Result:\n{}\n- response-time: {}\n",
				hostname,
				ipmiConfiguration.getUsername(),
				ipmiConfiguration.isSkipAuth(),
				ipmiConfiguration.getTimeout(),
				result,
				responseTime
			)
		);

		return result;
	}

	/**
	 * Execute a WinRM query
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running (<code>null</code> for localhost)
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout)
	 * @param query              The query to execute
	 * @param namespace          The namespace on which to execute the query
	 * @return The result of the query
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("WQL WinRM")
	public List<List<String>> executeWqlThroughWinRm(
		@SpanAttribute("host.hostname") @NonNull final String hostname,
		@SpanAttribute("wql.config") @NonNull final WinRmConfiguration winRmConfiguration,
		@SpanAttribute("wql.query") @NonNull final String query,
		@SpanAttribute("wql.namespace") @NonNull final String namespace
	) throws ClientException {
		final String username = winRmConfiguration.getUsername();
		final WinRMHttpProtocolEnum httpProtocol = TransportProtocols.HTTP.equals(winRmConfiguration.getProtocol())
			? WinRMHttpProtocolEnum.HTTP
			: WinRMHttpProtocolEnum.HTTPS;
		final Integer port = winRmConfiguration.getPort();
		final List<AuthenticationEnum> authentications = winRmConfiguration.getAuthentications();
		final Long timeout = winRmConfiguration.getTimeout();

		trace(() ->
			log.trace(
				"Executing WinRM WQL request:\n- hostname: {}\n- username: {}\n- query: {}\n" + // NOSONAR
				"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n- namespace: {}\n",
				hostname,
				username,
				query,
				httpProtocol,
				port,
				authentications,
				timeout,
				namespace
			)
		);

		// launching the request
		try {
			final long startTime = System.currentTimeMillis();

			WinRMWqlExecutor result = WinRMWqlExecutor.executeWql(
				httpProtocol,
				hostname,
				port,
				username,
				winRmConfiguration.getPassword(),
				namespace,
				query,
				timeout * 1000L,
				null,
				authentications
			);

			final long responseTime = System.currentTimeMillis() - startTime;

			final List<List<String>> table = result.getRows();

			trace(() ->
				log.trace(
					"Executed WinRM WQL request:\n- hostname: {}\n- username: {}\n- query: {}\n" + // NOSONAR
					"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n- namespace: {}\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					username,
					query,
					httpProtocol,
					port,
					authentications,
					timeout,
					namespace,
					TextTableHelper.generateTextTable(table),
					responseTime
				)
			);

			return table;
		} catch (Exception e) {
			log.error("Hostname {} - WinRM WQL request failed. Errors:\n{}\n", hostname, StringHelper.getStackMessages(e));
			throw new ClientException(String.format("WinRM WQL request failed on %s.", hostname), e);
		}
	}

	/**
	 * Execute a WinRM remote command
	 *
	 * @param hostname           The hostname of the device where the WinRM service is running (<code>null</code> for localhost)
	 * @param winRmConfiguration WinRM Protocol configuration (credentials, timeout)
	 * @param command            The command to execute
	 * @return The result of the query
	 * @throws ClientException when anything goes wrong (details in cause)
	 */
	@WithSpan("Remote Command WinRM")
	public static String executeRemoteWinRmCommand(
		@SpanAttribute("host.hostname") @NonNull final String hostname,
		@SpanAttribute("winrm.config") @NonNull final WinRmConfiguration winRmConfiguration,
		@SpanAttribute("winrm.command") @NonNull final String command
	) throws ClientException {
		final String username = winRmConfiguration.getUsername();
		final WinRMHttpProtocolEnum httpProtocol = TransportProtocols.HTTP.equals(winRmConfiguration.getProtocol())
			? WinRMHttpProtocolEnum.HTTP
			: WinRMHttpProtocolEnum.HTTPS;
		final Integer port = winRmConfiguration.getPort();
		final List<AuthenticationEnum> authentications = winRmConfiguration.getAuthentications();
		final Long timeout = winRmConfiguration.getTimeout();

		trace(() ->
			log.trace(
				"Executing WinRM remote command:\n- hostname: {}\n- username: {}\n- command: {}\n" + // NOSONAR
				"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n",
				hostname,
				username,
				command,
				httpProtocol,
				port,
				authentications,
				timeout
			)
		);

		// launching the command
		try {
			final long startTime = System.currentTimeMillis();

			WindowsRemoteCommandResult result = WinRMCommandExecutor.execute(
				command,
				httpProtocol,
				hostname,
				port,
				username,
				winRmConfiguration.getPassword(),
				null,
				timeout * 1000L,
				null,
				null,
				authentications
			);

			final long responseTime = System.currentTimeMillis() - startTime;

			// If the command returns an error
			if (result.getStatusCode() != 0) {
				throw new ClientException(String.format("WinRM remote command failed on %s: %s", hostname, result.getStderr()));
			}

			String resultStdout = result.getStdout();

			trace(() ->
				log.trace(
					"Executed WinRM remote command:\n- hostname: {}\n- username: {}\n- command: {}\n" + // NOSONAR
					"- protocol: {}\n- port: {}\n- authentications: {}\n- timeout: {}\n- Result:\n{}\n- response-time: {}\n",
					hostname,
					username,
					command,
					httpProtocol,
					port,
					authentications,
					timeout,
					resultStdout,
					responseTime
				)
			);

			return resultStdout;
		} catch (Exception e) {
			log.error("Hostname {} - WinRM remote command failed. Errors:\n{}\n", hostname, StringHelper.getStackMessages(e));
			throw new ClientException(String.format("WinRM remote command failed on %s.", hostname), e);
		}
	}

	/**
	 * Run the given runnable if the tracing mode of the logger is enabled
	 *
	 * @param runnable
	 */
	static void trace(final Runnable runnable) {
		if (log.isTraceEnabled()) {
			runnable.run();
		}
	}
}
