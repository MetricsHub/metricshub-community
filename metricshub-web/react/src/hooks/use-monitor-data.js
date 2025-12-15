import { useMemo } from "react";

/**
 * Hook to extract monitor data from the current resource.
 *
 * @param {Object} currentResource - The current resource from Redux store.
 * @param {string} monitorType - The decoded monitor type name.
 * @returns {Object|null} Monitor data object with monitor, metaMetrics, and connectorName, or null if not found.
 */
export const useMonitorData = (currentResource, monitorType) => {
	return useMemo(() => {
		if (!currentResource?.connectors || !monitorType) return null;
		for (const connector of currentResource.connectors) {
			if (connector.monitors) {
				const found = connector.monitors.find((m) => m.name === monitorType);
				if (found) {
					return {
						monitor: found,
						metaMetrics: connector.metaMetrics,
						connectorName: connector.name,
					};
				}
			}
		}
		return null;
	}, [currentResource, monitorType]);
};
