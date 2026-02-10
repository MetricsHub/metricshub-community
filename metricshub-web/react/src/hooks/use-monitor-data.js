import { useMemo } from "react";

/**
 * Hook to extract monitor data from the current resource.
 *
 * @param {Object} currentResource - The current resource from Redux store.
 * @param {string} connectorId - The decoded connector ID.
 * @param {string} monitorType - The decoded monitor type name.
 * @returns {Object|null} Monitor data object with monitor, metaMetrics, and connectorName, or null if not found.
 */
export const useMonitorData = (currentResource, connectorId, monitorType) => {
	return useMemo(() => {
		if (!currentResource?.connectors || !connectorId || !monitorType) return null;
		const connector = currentResource.connectors.find((c) => c.name === connectorId);
		if (!connector || !connector.monitors) return null;
		const found = connector.monitors.find((m) => m.name === monitorType);
		if (found) {
			return {
				monitor: found,
				metaMetrics: connector.metaMetrics,
				connectorName: connector.name,
			};
		}
		return null;
	}, [currentResource, connectorId, monitorType]);
};
