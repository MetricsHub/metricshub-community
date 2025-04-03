package org.metricshub.hardware.strategy;

import static org.metricshub.hardware.util.HwConstants.PRESENT_STATUS;

import java.util.Collection;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.sentrysoftware.metricshub.engine.client.ClientsExecutor;
import org.sentrysoftware.metricshub.engine.extension.ExtensionManager;
import org.sentrysoftware.metricshub.engine.strategy.AbstractStrategy;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * Strategy responsible of executing post collect actions for hardware monitors.<br>
 * This strategy is responsible for refreshing the collect time of <code>hw.status{hw.type="&lt;monitor-type&gt;", state="present"}</code> metrics.
 */
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HardwarePostCollectStrategy extends AbstractStrategy {

	/**
	 * Create a new {@link HardwarePostCollectStrategy} instance.<br>
	 * This strategy is responsible for refreshing the collect time of <code>hw.status{hw.type="&lt;monitor-type&gt;", state="present"}</code> metrics.
	 *
	 * @param telemetryManager The {@link TelemetryManager} instance wrapping the connector monitors.
	 * @param strategyTime     The strategy time (Collect time).
	 * @param clientsExecutor  The {@link ClientsExecutor} instance.
	 * @param extensionManager The {@link ExtensionManager} instance.
	 */
	public HardwarePostCollectStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
	}

	@Override
	public void run() {
		telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.filter(telemetryManager::isConnectorStatusOk)
			.forEach(this::refreshPresentCollectTime);
	}

	/**
	 * Refresh the collect time of the {@link Monitor}'s
	 * hw.status{hw.type="&lt;monitor-type&gt;", state="present"} metric
	 * and set it to the current strategy time.
	 *
	 * @param monitor The {@link Monitor} to refresh
	 */
	private void refreshPresentCollectTime(final Monitor monitor) {
		final String presentMetricName = String.format(PRESENT_STATUS, monitor.getType());

		final NumberMetric presentMetric = monitor.getMetric(presentMetricName, NumberMetric.class);

		if (presentMetric != null) {
			presentMetric.setCollectTime(strategyTime);
		}
	}
}
