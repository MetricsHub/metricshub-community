import { useState, useMemo, useCallback } from "react";

/**
 * Hook to manage metric selection and settings dialog.
 *
 * @param {Array} instances - Array of instance objects to extract available metrics from.
 * @returns {Object} Object containing selectedMetrics, handleMetricToggle, isSettingsOpen, setIsSettingsOpen, and availableMetrics.
 */
export const useMetricSelection = (instances) => {
	const [selectedMetrics, setSelectedMetrics] = useState([]);
	const [isSettingsOpen, setIsSettingsOpen] = useState(false);

	const availableMetrics = useMemo(() => {
		const metricsSet = new Set();
		// Check first 50 instances to gather available metrics
		for (const instance of instances.slice(0, 50)) {
			if (instance.metrics) {
				Object.keys(instance.metrics).forEach((k) => {
					if (!k.startsWith("__")) {
						metricsSet.add(k);
					}
				});
			}
		}
		return Array.from(metricsSet).sort();
	}, [instances]);

	const handleMetricToggle = useCallback((metric) => {
		setSelectedMetrics((prev) =>
			prev.includes(metric) ? prev.filter((m) => m !== metric) : [...prev, metric],
		);
	}, []);

	return {
		selectedMetrics,
		setSelectedMetrics,
		handleMetricToggle,
		isSettingsOpen,
		setIsSettingsOpen,
		availableMetrics,
	};
};
