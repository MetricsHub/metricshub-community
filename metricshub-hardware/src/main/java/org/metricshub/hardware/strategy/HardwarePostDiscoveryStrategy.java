package org.metricshub.hardware.strategy;

import static org.metricshub.hardware.util.HwCollectHelper.connectorHasHardwareTag;
import static org.metricshub.hardware.util.HwConstants.PRESENT_STATUS;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.sentrysoftware.metricshub.engine.client.ClientsExecutor;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.engine.strategy.AbstractStrategy;
import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * Strategy responsible of executing post discovery actions for hardware monitors.<br>
 * This strategy is responsible for checking the presence of hardware monitors in the {@link TelemetryManager} and should be executed after the discovery phase.
 */
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HardwarePostDiscoveryStrategy extends AbstractStrategy {

	/**
	 * Set of monitor types that should be excluded from hardware missing device detection.
	 */
	private static final Set<String> EXCLUDED_MONITOR_TYPES = Stream
		.of(
			KnownMonitorType.HOST.getKey(),
			KnownMonitorType.CONNECTOR.getKey(),
			KnownMonitorType.LUN.getKey(),
			KnownMonitorType.LOGICAL_DISK.getKey(),
			KnownMonitorType.VOLTAGE.getKey(),
			KnownMonitorType.TEMPERATURE.getKey(),
			KnownMonitorType.VM.getKey(),
			KnownMonitorType.LED.getKey()
		)
		.collect(Collectors.toSet());

	/**
	 * Set of monitor types that are candidates for hardware missing device.
	 */
	private static final Set<String> MONITOR_TYPE_CANDIDATES = KnownMonitorType.KEYS
		.stream()
		.filter(type -> !EXCLUDED_MONITOR_TYPES.contains(type))
		.collect(Collectors.toSet());

	/**
	 * Create a new instance of {@link HardwarePostDiscoveryStrategy}.<br>
	 * This strategy is responsible for checking the presence of hardware monitors in the {@link TelemetryManager} and should be executed after the discovery phase.
	 *
	 * @param telemetryManager The {@link TelemetryManager} instance wrapping the connector monitors.
	 * @param strategyTime     The strategy time (Discovery time).
	 * @param clientsExecutor  The {@link ClientsExecutor} instance.
	 * @param extensionManager The {@link ExtensionManager} instance.
	 */
	public HardwarePostDiscoveryStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
	}

	/**
	 * Sets the current monitor as missing.
	 *
	 * @param monitor A given monitor
	 * @param hostname The host's name
	 * @param metricName The collected metric name
	 */
	public void setAsMissing(final Monitor monitor, final String hostname, final String metricName) {
		new MetricFactory(hostname).collectNumberMetric(monitor, metricName, 0.0, strategyTime);
	}

	/**
	 * Sets the current monitor as present
	 * @param monitor A given monitor
	 * @param hostname The host's name
	 * @param metricName The collected metric name
	 */
	public void setAsPresent(final Monitor monitor, final String hostname, final String metricName) {
		new MetricFactory(hostname).collectNumberMetric(monitor, metricName, 1.0, strategyTime);
	}

	/**
	 * Checks whether a monitor is a candidate for hardware missing device detection.
	 *
	 * @param monitorType A given monitor's type
	 * @return boolean Whether the monitor is a candidate for hardware missing device detection.
	 */
	private boolean isCandidateMonitorType(final String monitorType) {
		return MONITOR_TYPE_CANDIDATES.contains(monitorType);
	}

	@Override
	public void run() {
		// Loop over each known monitor from the telemetry manager and
		// set the monitor as missing if strategy time is not equal to monitor's discovery time
		// otherwise set the monitor as present.
		telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.filter(monitor -> isCandidateMonitorType(monitor.getType()))
			.filter(telemetryManager::isConnectorStatusOk)
			.filter(monitor -> connectorHasHardwareTag(monitor, telemetryManager))
			.forEach(monitor -> {
				if (!strategyTime.equals(monitor.getDiscoveryTime())) {
					setAsMissing(monitor, telemetryManager.getHostname(), String.format(PRESENT_STATUS, monitor.getType()));
				} else {
					setAsPresent(monitor, telemetryManager.getHostname(), String.format(PRESENT_STATUS, monitor.getType()));
				}
			});
	}
}
