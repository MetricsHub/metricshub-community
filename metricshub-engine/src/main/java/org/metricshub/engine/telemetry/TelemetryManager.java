package org.metricshub.engine.telemetry;

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

import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.strategy.ContextExecutor;
import org.metricshub.engine.strategy.IStrategy;

/**
 * The `TelemetryManager` class manages telemetry-related operations, monitors, and strategies.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
@EqualsAndHashCode
public class TelemetryManager {

	@Default
	private Map<String, Map<String, Monitor>> monitors = new HashMap<>();

	@Default
	private HostProperties hostProperties = new HostProperties();

	private HostConfiguration hostConfiguration;
	private ConnectorStore connectorStore;
	private Long strategyTime;
	private boolean isEmulationMode;
	private String emulationModeSourceOutputDirectory;
	private String emulationModeSnmpInputDirectory;
	private boolean isCalledFromMetricsHubCli;

	/**
	 * Executes the given {@link IStrategy} instances.
	 *
	 * @param strategies	The {@link IStrategy} instances to be executed.
	 *
	 */
	@WithSpan
	public synchronized void run(@SpanAttribute("telemetrymanager.strategies") final IStrategy... strategies) {
		final String hostname = hostConfiguration.getHostname();

		log.trace("Hostname {} - Engine called for thread {}.", hostname, Thread.currentThread().getName());

		for (IStrategy strategy : strategies) {
			final String strategyType = strategy.getClass().getSimpleName();

			log.trace("Hostname {} - Calling strategy {}.", hostname, strategyType);
			runStrategy(strategy);
			log.info("Hostname {} - End of strategy {}.", hostname, strategyType);

			if (log.isDebugEnabled()) {
				log.debug("Hostname {} - >>> {} >>>\n{}", hostname, strategy.getClass().getSimpleName(), toJson());
			}
		}
	}

	/**
	 * Run the given strategy and manage triggered exceptions
	 *
	 * @param strategy Any implementation of the {@link IStrategy} interface
	 */
	void runStrategy(final IStrategy strategy) {
		final String hostname = hostConfiguration.getHostname();
		strategyTime = strategy.getStrategyTime();

		try {
			new ContextExecutor(strategy).execute();
		} catch (ExecutionException e) {
			log.error(
				"Hostname {} - {} operation failed: {}: {}.",
				hostname,
				strategy.getClass().getSimpleName(),
				e.getClass().getSimpleName(),
				e.getMessage()
			);
			log.debug("Hostname {} - Operation failed with ExecutionException.", hostname, e);
		} catch (TimeoutException e) {
			log.error("Hostname {} - {} operation timed out.", hostname, strategy.getClass().getSimpleName());
			log.debug("Hostname {} - Operation failed with TimeoutException: ", hostname, e);
		} catch (InterruptedException e) {
			log.error("Hostname {} - {} operation interrupted.", hostname, strategy.getClass().getSimpleName());
			log.debug("Hostname {} - Operation failed with InterruptedException: ", hostname, e);

			Thread.currentThread().interrupt();
		} catch (Throwable e) { // NOSONAR
			log.error(
				"Hostname {} - {} operation failed with {}.",
				hostname,
				strategy.getClass().getSimpleName(),
				e.getClass().getSimpleName()
			);
			log.debug("Hostname {} - Operation failed with exception: ", hostname, e);
		}
	}

	/**
	 * Convert monitors to JSON format
	 * @return {@link String} value
	 */
	public String toJson() {
		final MonitorsVo hostMonitoringVo = getVo();

		return JsonHelper.serialize(hostMonitoringVo);
	}

	/**
	 * Get the current monitors as {@link MonitorsVo}
	 *
	 * @return {@link MonitorsVo} object
	 */
	public MonitorsVo getVo() {
		final MonitorsVo monitorsVo = new MonitorsVo();

		final List<String> monitorTypes = new ArrayList<>(monitors.keySet());

		monitorTypes
			.stream()
			.sorted()
			.filter(monitorType -> {
				final Map<String, Monitor> monitorsMap = monitors.get(monitorType);
				return monitorsMap != null && monitorsMap.values() != null;
			})
			.forEach(monitorType -> {
				final List<Monitor> monitorList = new ArrayList<>(monitors.get(monitorType).values());
				Collections.sort(monitorList, Comparator.comparing(Monitor::getId));
				monitorsVo.addAll(monitorList);
			});
		return monitorsVo;
	}

	/**
	 * Finds a monitor using its type and its id attribute
	 * @param type monitor's type
	 * @param id monitor's id
	 * @return Monitor instance
	 */
	public Monitor findMonitorByTypeAndId(final String type, final String id) {
		final Map<String, Monitor> findMonitorByTypeResult = findMonitorsByType(type);
		if (findMonitorByTypeResult != null) {
			return findMonitorById(id, findMonitorByTypeResult);
		}
		return null;
	}

	/**
	 * Finds a monitor using its id attribute
	 * @param id monitorId
	 * @param monitorsMap a map of monitors having the same type
	 * @return {@link Monitor} instance
	 */
	public Monitor findMonitorById(final String id, final Map<String, Monitor> monitorsMap) {
		return monitorsMap.get(id);
	}

	/**
	 * Finds a monitor using its type
	 *
	 * @param type type of the monitor. E.g. host, enclosure, network, etc.
	 * @return {@link Monitor} instance
	 */
	public Map<String, Monitor> findMonitorsByType(final String type) {
		return this.getMonitors() == null ? null : this.getMonitors().get(type);
	}

	/**
	 * Add a new monitor instance
	 *
	 * @param monitor     Monitor instance we wish to add
	 * @param monitorType The type of the monitor
	 * @param id          The monitor's identifier
	 * @return added {@link Monitor} instance
	 */
	public Monitor addNewMonitor(
		@NonNull final Monitor monitor,
		@NonNull final String monitorType,
		@NonNull final String id
	) {
		synchronized (monitors) {
			monitors.computeIfAbsent(monitorType, t -> new HashMap<>()).put(id, monitor);
			return monitor;
		}
	}

	/**
	 * Return the root host monitor instance
	 *
	 * @return {@link Monitor} object representing the root instance
	 */
	public Monitor getEndpointHostMonitor() {
		// Get host monitors
		final Map<String, Monitor> hostMonitors = findMonitorsByType(HOST.getKey());

		if (hostMonitors == null) {
			return null;
		}

		// Get the endpoint host
		return hostMonitors.values().stream().filter(Monitor::isEndpoint).findFirst().orElse(null);
	}

	/**
	 * Return the configured hostname
	 *
	 * @return {@link String} value
	 */
	public String getHostname() {
		return hostConfiguration.getHostname();
	}

	/**
	 * This method finds the parent of a given monitor
	 * @param monitor a given monitor
	 * @return the parent monitor which is a {@link Monitor} instance
	 */
	public Monitor findParentMonitor(final Monitor monitor) {
		final String hwParentId = monitor.getAttribute("hw.parent.id");
		final String hwParentType = monitor.getAttribute("hw.parent.type");

		if (hwParentType != null && hwParentId != null) {
			Optional<Map<String, Monitor>> sameTypeMonitors = Optional.ofNullable(findMonitorsByType(hwParentType));
			if (sameTypeMonitors.isPresent()) {
				final Optional<Monitor> parentMonitor = sameTypeMonitors
					.get()
					.entrySet()
					.stream()
					.filter(entry -> hwParentId.equals(entry.getValue().getAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_ID)))
					.map(Map.Entry::getValue)
					.findFirst();
				if (parentMonitor.isPresent()) {
					return parentMonitor.get();
				}
			}
		}
		log.warn("Monitor {} does not have a parent on Host {}", monitor.getId(), getHostname());
		return null;
	}

	/**
	 * This method finds a monitor having a given id regardless of its type
	 * @param monitorId monitor id
	 * @return {@link Monitor}
	 */
	public Monitor findMonitorById(final String monitorId) {
		return getMonitors()
			.values()
			.stream()
			.map(monitorsMap -> monitorsMap.get(monitorId))
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	/**
	 * This method checks whether a connector status was set to "ok"
	 * @param currentMonitor the current monitor
	 * @return boolean whether the connector status is ok
	 */
	public boolean isConnectorStatusOk(final Monitor currentMonitor) {
		if (currentMonitor.isEndpointHost()) {
			return true;
		}
		final String connectorId = currentMonitor.getAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID);
		return null != connectorId && hostProperties.getConnectorNamespace(connectorId).isStatusOk();
	}

	/**
	 * Retrieves the hostname from the provided list of configuration classes, considering the order of the list.
	 *
	 * This method searches through the user's configurations, as specified by the list of `IConfiguration` classes,
	 * to find and return the first non-null hostname. The order of the configurations in the list is significant.
	 * For example, calling `getHostname(SshConfiguration.class, OsCommandConfiguration.class)` may yield a different
	 * result compared to `getHostname(OsCommandConfiguration.class, SshConfiguration.class)` if both configurations exist.
	 *
	 * If the list of configurations is empty or none of the configurations provide a non-null hostname, the method
	 * will return the telemetry manager's hostname as a fallback.
	 *
	 * @param configurations A list of `IConfiguration` classes to search for the hostname.
	 * @return the first non-null hostname from the provided configurations, or the telemetry manager's hostname if none are found.
	 */
	public String getHostname(List<Class<? extends IConfiguration>> configurations) {
		return configurations
			.stream()
			.map(config -> hostConfiguration.getConfigurations().get(config)) // Get the configuration from the user's configuration map
			.filter(Objects::nonNull) // Filter out null configurations
			.map(IConfiguration::getHostname) // Map to hostname
			.filter(Objects::nonNull) // Filter out null hostnames
			.findFirst() // Get the first matching hostname
			.orElse(getHostname()); // Fallback to telemetry manager hostname
	}

	/**
	 * Retrieves a map of {@link EmbeddedFile} objects indexed by their unique integer identifiers, associated
	 * with a specific connector identified by {@code connectorId}.
	 * @param connectorId The unique identifier of the connector whose embedded files are to be retrieved.
	 * @return A non-null {@link Map} of integer IDs to {@link EmbeddedFile} instances.
	 */
	public Map<Integer, EmbeddedFile> getEmbeddedFiles(final String connectorId) {
		return connectorStore.getStore().get(connectorId).getEmbeddedFiles();
	}
}
