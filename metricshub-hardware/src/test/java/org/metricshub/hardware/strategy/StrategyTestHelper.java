package org.metricshub.hardware.strategy;

import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * Helper class for the strategy tests
 */
class StrategyTestHelper {

	/**
	 * Set the connector status in the namespace
	 *
	 * @param isSuccessCriteria Whether the connector's criteria are successfully executed or not
	 * @param connectorId       Connector ID
	 * @param monitor           Monitor instance
	 */
	static void setConnectorStatusInNamespace(
		final boolean isSuccessCriteria,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		// Get the connector's namespace containing related settings and set the status to isScuccessCriteria
		telemetryManager.getHostProperties().getConnectorNamespace(connectorId).setStatusOk(isSuccessCriteria);
	}
}
