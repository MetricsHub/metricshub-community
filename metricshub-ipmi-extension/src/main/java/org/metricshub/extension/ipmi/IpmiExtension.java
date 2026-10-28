package org.metricshub.extension.ipmi;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Ipmi Extension
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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSelSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.deserialization.TimeDeserializer;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.ipmi.IpmiRequestExecutor.SelCursor;
import org.metricshub.extension.ipmi.IpmiRequestExecutor.SelRead;
import org.metricshub.ipmi.client.SelEntry;
import org.metricshub.ipmi.client.SelEntryList;
import org.metricshub.ipmi.core.coding.commands.chassis.PowerCommand;

/**
 * The IpmiExtension class implements the IProtocolExtension interface to provide support for the IPMI (Intelligent Platform Management Interface) protocol in the MetricsHub engine.
 */
@Slf4j
public class IpmiExtension implements IProtocolExtension {

	/**
	 * The identifier for the Ipmi protocol.
	 */
	public static final String IDENTIFIER = "ipmi";

	/**
	 * The power actions of the chassis query, in the wording of the ipmi-java CLI
	 */
	private static final Map<String, PowerCommand> POWER_COMMANDS = Map.of(
		"on",
		PowerCommand.PowerUp,
		"off",
		PowerCommand.PowerDown,
		"cycle",
		PowerCommand.PowerCycle,
		"reset",
		PowerCommand.HardReset,
		"soft",
		PowerCommand.SoftShutdown,
		"diag",
		PowerCommand.DiagnosticInterrupt
	);

	/**
	 * How long the chassis identify stays on when the query gives no duration
	 */
	private static final String DEFAULT_IDENTIFY_SECONDS = "15";

	private IpmiRequestExecutor ipmiRequestExecutor = new IpmiRequestExecutor();

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof IpmiConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(IpmiSource.class, IpmiSelSource.class, IpmiRawSource.class);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.of(IpmiConfiguration.class, getSupportedSources());
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(IpmiCriterion.class);
	}

	@Override
	public Optional<Boolean> checkProtocol(TelemetryManager telemetryManager) {
		// Retrieve the hostname from the IpmiConfiguration, otherwise from the telemetryManager
		String hostname = telemetryManager.getHostname(List.of(IpmiConfiguration.class));

		// Create and set the IPMI result to null
		String ipmiResult = null;

		// Retrieve IPMI Configuration from the telemetry manager host configuration
		final IpmiConfiguration ipmiConfiguration = (IpmiConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(IpmiConfiguration.class);

		// Stop the IPMI health check if there is not an IPMI configuration
		if (ipmiConfiguration == null) {
			return Optional.empty();
		}

		log.info("Hostname {} - Performing {} protocol health check.", hostname, getIdentifier());
		log.info(
			"Hostname {} - Checking IPMI protocol status. Probing the BMC with a sessionless IPMI 'Get Channel Authentication Capabilities' request.",
			hostname
		);

		// Probe the BMC: no credentials, no session
		try {
			ipmiResult = ipmiRequestExecutor.executeIpmiProbe(hostname, ipmiConfiguration);
		} catch (Exception e) {
			log.debug("Hostname {} - Checking IPMI protocol status. IPMI exception when probing the BMC: ", hostname, e);
		}
		return Optional.of(ipmiResult != null);
	}

	@Override
	public SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		final IpmiConfiguration ipmiConfiguration = (IpmiConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(IpmiConfiguration.class);

		if (ipmiConfiguration == null) {
			log.warn(
				"Hostname {} - The IPMI credentials are not configured. Cannot process IPMI-over-LAN source.",
				telemetryManager.getHostname()
			);
			return SourceTable.empty();
		}

		// Retrieve the hostname from the IpmiConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(IpmiConfiguration.class));

		try {
			if (source instanceof IpmiRawSource rawSource) {
				return processRawSource(rawSource, hostname, ipmiConfiguration, telemetryManager);
			}
			if (source instanceof IpmiSelSource selSource) {
				return processSelSource(selSource, connectorId, hostname, ipmiConfiguration, telemetryManager);
			}

			final String result = ipmiRequestExecutor.executeIpmiGetSensors(hostname, ipmiConfiguration);

			if (result != null) {
				record(telemetryManager, IpmiRecorder.GET_SENSORS_REQUEST, null, result);
				return SourceTable.builder().rawData(result).build();
			} else {
				log.error("Hostname {} - IPMI-over-LAN request returned <null> result. Returning an empty table.", hostname);
			}
		} catch (Exception e) {
			LoggingHelper.logSourceError(connectorId, source.getKey(), "IPMI-over-LAN", hostname, e);
		}

		return SourceTable.empty();
	}

	/**
	 * Sends the raw command of the source and returns its one-row table: {@code completionCode;data}.
	 *
	 * @param source            The ipmiRaw source
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The IPMI configuration of the host
	 * @param telemetryManager  The telemetry manager, for the recording directory
	 * @return the one-row table
	 */
	private SourceTable processRawSource(
		final IpmiRawSource source,
		final String hostname,
		final IpmiConfiguration ipmiConfiguration,
		final TelemetryManager telemetryManager
	) {
		final String result = ipmiRequestExecutor.executeIpmiRaw(hostname, ipmiConfiguration, source);
		record(telemetryManager, IpmiRecorder.RAW_REQUEST, IpmiRequestExecutor.rawQuery(source), result);
		return SourceTable.builder().table(List.of(IpmiResultConverter.rawRow(result))).build();
	}

	/**
	 * Returns the SEL entries added since the previous poll of the source, at most {@code maxEventsPerPoll} of
	 * them (the others wait for the next poll), and none on the first poll, which only locates the end of the log.
	 *
	 * @param source            The ipmiSel source
	 * @param connectorId       The connector, whose namespace keeps the cursor
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The IPMI configuration of the host
	 * @param telemetryManager  The telemetry manager, for the cursor and the recording directory
	 * @return the table of new entries
	 */
	private SourceTable processSelSource(
		final IpmiSelSource source,
		final String connectorId,
		final String hostname,
		final IpmiConfiguration ipmiConfiguration,
		final TelemetryManager telemetryManager
	) {
		final ConnectorNamespace namespace = telemetryManager.getHostProperties().getConnectorNamespace(connectorId);
		final String sourceKey = source.getKey();
		final SelCursor cursor = (SelCursor) namespace.getSourceCursor(sourceKey);

		if (cursor == null) {
			// The first poll locates the end of the log without reading it, and reports no backlog
			final SelCursor end = ipmiRequestExecutor.executeIpmiSelEnd(hostname, ipmiConfiguration);
			namespace.setSourceCursor(sourceKey, end);
			log.info(
				"Hostname {} - First IPMI SEL poll of source {}: cursor at record {}",
				hostname,
				sourceKey,
				end.recordId()
			);
			record(telemetryManager, IpmiRecorder.GET_SEL_REQUEST, null, "");
			return SourceTable.empty();
		}

		final SelRead read = ipmiRequestExecutor.executeIpmiSel(hostname, ipmiConfiguration, cursor, true);
		final SelEntryList entries = read.entries();

		if (cursor.recordId() > 0 && !entries.isEmpty() && entries.get(0).id() <= cursor.recordId()) {
			log.info(
				"Hostname {} - The IPMI System Event Log was cleared since the previous poll of source {}: reading it from its first entry.",
				hostname,
				sourceKey
			);
		}

		List<SelEntry> kept = entries;
		final int maxEventsPerPoll = source.getMaxEventsPerPoll();
		if (maxEventsPerPoll > 0 && kept.size() > maxEventsPerPoll) {
			kept = kept.subList(0, maxEventsPerPoll);
		}

		// The cursor stops at the last entry returned: the entries left out are read again by the next poll
		if (!kept.isEmpty()) {
			namespace.setSourceCursor(sourceKey, SelCursor.of(kept.get(kept.size() - 1)));
		}

		log.info(
			"Hostname {} - IPMI SEL poll of source {}: {} entries read, {} returned, cursor at record {}",
			hostname,
			sourceKey,
			entries.size(),
			kept.size(),
			kept.isEmpty() ? cursor.recordId() : kept.get(kept.size() - 1).id()
		);

		final List<List<String>> rows = IpmiResultConverter.convertSel(kept, read.sensors());
		record(telemetryManager, IpmiRecorder.GET_SEL_REQUEST, null, SourceTable.tableToCsv(rows, ";", true));
		return SourceTable.builder().table(rows).build();
	}

	/**
	 * Records a response when a recording session is on.
	 *
	 * @param telemetryManager The telemetry manager, for the recording directory
	 * @param requestType      The request type
	 * @param query            The request bytes of a raw command, {@code null} otherwise
	 * @param response         The response
	 */
	private static void record(
		final TelemetryManager telemetryManager,
		final String requestType,
		final String query,
		final String response
	) {
		final String recordOutputDirectory = telemetryManager.getRecordOutputDirectory();
		if (recordOutputDirectory != null && !recordOutputDirectory.isBlank()) {
			IpmiRecorder.getInstance(recordOutputDirectory).record(requestType, query, response);
		}
	}

	@Override
	public CriterionTestResult processCriterion(
		Criterion criterion,
		String connectorId,
		TelemetryManager telemetryManager,
		boolean logMode
	) {
		final IpmiConfiguration configuration = (IpmiConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(IpmiConfiguration.class);

		if (configuration == null) {
			if (logMode) {
				log.debug(
					"Hostname {} - The IPMI credentials are not configured for this host. Cannot process IPMI-over-LAN detection.",
					telemetryManager.getHostname()
				);
			}
			return CriterionTestResult.empty();
		}

		// Retrieve the hostname from the IpmiConfiguration, otherwise from the telemetryManager
		final String hostname = telemetryManager.getHostname(List.of(IpmiConfiguration.class));

		try {
			final String result = ipmiRequestExecutor.executeIpmiDetection(hostname, configuration);
			if (result == null) {
				return CriterionTestResult.builder()
					.message("Received <null> result after connecting to the IPMI BMC chip with the IPMI-over-LAN interface.")
					.build();
			}

			record(telemetryManager, IpmiRecorder.IPMI_DETECTION_REQUEST, null, result);

			return CriterionTestResult.builder()
				.result(result)
				.message("Successfully connected to the IPMI BMC chip with the IPMI-over-LAN interface.")
				.success(true)
				.build();
		} catch (final Exception e) {
			// NOSONAR on interruption
			final String message = String.format(
				"Hostname %s - Cannot execute IPMI-over-LAN command to get the chassis status. Exception: %s",
				hostname,
				e.getMessage()
			);
			if (logMode) {
				log.error(message);
				log.debug(message, e);
			}
			return CriterionTestResult.builder().message(message).build();
		}
	}

	@Override
	public boolean isSupportedConfigurationType(String configurationType) {
		return IDENTIFIER.equalsIgnoreCase(configurationType);
	}

	@Override
	public IConfiguration buildConfiguration(String configurationType, JsonNode jsonNode, UnaryOperator<char[]> decrypt)
		throws InvalidConfigurationException {
		try {
			final IpmiConfiguration ipmiConfiguration = newObjectMapper().treeToValue(jsonNode, IpmiConfiguration.class);

			if (decrypt != null) {
				final char[] password = ipmiConfiguration.getPassword();
				if (password != null) {
					// Decrypt the password
					ipmiConfiguration.setPassword(decrypt.apply(password));
				}
			}

			return ipmiConfiguration;
		} catch (Exception e) {
			final String errorMessage = String.format("Error while reading IPMI Configuration. Error: %s", e.getMessage());
			log.error(errorMessage);
			log.debug("Error while reading IPMI Configuration. Stack trace:", e);
			throw new InvalidConfigurationException(errorMessage, e);
		}
	}

	/**
	 * Creates and configures a new instance of the Jackson ObjectMapper for handling YAML data.
	 *
	 * @return A configured ObjectMapper instance.
	 */
	public static JsonMapper newObjectMapper() {
		return JsonMapper.builder(new YAMLFactory())
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(SerializationFeature.INDENT_OUTPUT)
			.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
			.build();
	}

	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	/**
	 * Flushes and releases recorder resources at the end of a recording session.
	 *
	 * @param telemetryManager telemetry manager that carries the recording output directory
	 */
	@Override
	public void onRecordingSessionEnd(final TelemetryManager telemetryManager) {
		final String recordOutputDirectory = telemetryManager.getRecordOutputDirectory();
		if (recordOutputDirectory != null && !recordOutputDirectory.isBlank()) {
			IpmiRecorder.flushAndRemoveInstance(recordOutputDirectory);
		}
	}

	/**
	 * Runs the query of the MCP tool or the CLI: {@code sensors} (the default, the chassis status, FRUs and
	 * sensors text), {@code sel} (the System Event Log as CSV with a header line, the entries of the last
	 * {@code since} duration or the whole log), {@code raw} (a raw command with {@code netFn}, {@code command}
	 * and {@code data}) or {@code chassis} (an {@code action}: {@code status}, {@code on}, {@code off},
	 * {@code cycle}, {@code reset}, {@code soft}, {@code diag} or {@code identify} with {@code seconds}).
	 *
	 * @param configuration The IPMI configuration of the host
	 * @param query         The query, {@code null} for the sensors
	 * @return the result text
	 * @throws Exception If the query is unknown or the BMC fails it
	 */
	@Override
	public String executeQuery(final IConfiguration configuration, final JsonNode query) throws Exception {
		final String hostname = configuration.getHostname();
		final IpmiConfiguration ipmiConfiguration = (IpmiConfiguration) configuration;
		final String type = text(query, "query");
		return switch (type == null ? "sensors" : type.trim().toLowerCase()) {
			case "sensors" -> ipmiRequestExecutor.executeIpmiGetSensors(hostname, ipmiConfiguration);
			case "sel" -> selQuery(hostname, ipmiConfiguration, text(query, "since"));
			case "raw" -> ipmiRequestExecutor.executeIpmiRaw(
				hostname,
				ipmiConfiguration,
				IpmiRawSource.builder()
					.netFn(text(query, "netFn"))
					.command(text(query, "command"))
					.data(text(query, "data"))
					.build()
			);
			case "chassis" -> chassisQuery(hostname, ipmiConfiguration, text(query, "action"), text(query, "seconds"));
			default -> throw new IllegalArgumentException(
				"Unknown IPMI query '" + type + "': expected sensors, sel, raw or chassis"
			);
		};
	}

	/**
	 * Reads the System Event Log for a query.
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The IPMI configuration of the host
	 * @param since             A duration ({@code 30m}, {@code 1h}, {@code 2d}, or seconds) to keep only the entries
	 *                          logged since then; {@code null} or blank for the whole log
	 * @return the header line, then one line per entry
	 */
	private String selQuery(final String hostname, final IpmiConfiguration ipmiConfiguration, final String since) {
		final SelRead read = ipmiRequestExecutor.executeIpmiSel(hostname, ipmiConfiguration, null, true);
		List<SelEntry> entries = read.entries();
		if (since != null && !since.isBlank()) {
			// An entry logged before the BMC had the time cannot be dated: it is left out
			final Instant from = Instant.now().minusSeconds(TimeDeserializer.interpretValueOf(since.trim()));
			entries = entries
				.stream()
				.filter(entry -> entry.timestamp().map(from::isBefore).orElse(false))
				.toList();
		}
		final List<List<String>> rows = IpmiResultConverter.convertSel(entries, read.sensors());
		return IpmiResultConverter.SEL_HEADER + NEW_LINE + SourceTable.tableToCsv(rows, TABLE_SEP, true);
	}

	/**
	 * Runs a chassis action for a query.
	 *
	 * @param hostname          The host name or IP address of the BMC
	 * @param ipmiConfiguration The IPMI configuration of the host
	 * @param action            {@code status} (the default), a power command ({@code on}, {@code off},
	 *                          {@code cycle}, {@code reset}, {@code soft}, {@code diag}) or {@code identify}
	 * @param seconds           For {@code identify}: how long the LED stays on (default 15), {@code 0} to turn it
	 *                          off, {@code forever} until turned off
	 * @return the power state after a power command, or what was done
	 */
	private String chassisQuery(
		final String hostname,
		final IpmiConfiguration ipmiConfiguration,
		final String action,
		final String seconds
	) {
		final String resolvedAction = action == null || action.isBlank() ? "status" : action.trim().toLowerCase();
		if ("identify".equals(resolvedAction)) {
			final String duration = seconds == null || seconds.isBlank() ? DEFAULT_IDENTIFY_SECONDS : seconds.trim();
			final boolean forever = "forever".equalsIgnoreCase(duration);
			return ipmiRequestExecutor.executeIpmiChassisIdentify(
				hostname,
				ipmiConfiguration,
				forever ? 0 : Integer.parseInt(duration),
				forever
			);
		}
		final PowerCommand command = POWER_COMMANDS.get(resolvedAction);
		if (command != null) {
			return ipmiRequestExecutor.executeIpmiChassisControl(hostname, ipmiConfiguration, command);
		}
		if ("status".equals(resolvedAction)) {
			return ipmiRequestExecutor.executeIpmiDetection(hostname, ipmiConfiguration);
		}
		throw new IllegalArgumentException(
			"Unknown IPMI chassis action '" + action + "': expected status, on, off, cycle, reset, soft, diag or identify"
		);
	}

	/**
	 * @param query The query node, possibly {@code null}
	 * @param field The field name
	 * @return the text of the field, {@code null} when absent
	 */
	private static String text(final JsonNode query, final String field) {
		return query != null && query.hasNonNull(field) ? query.get(field).asText() : null;
	}
}
