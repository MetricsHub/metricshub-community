import { useState, useMemo, useCallback } from "react";
import {
	getMetricValue,
	compareMetricNames,
	getInstanceDisplayName,
} from "../utils/metrics-helper";

export const SORT_KEY_INSTANCE_ID = "__instance_id__";
export const SORT_DIRECTION_ASC = "asc";
export const SORT_DIRECTION_DESC = "desc";

/**
 * Hook to manage instance sorting.
 *
 * @param {Array} instances - Array of instance objects.
 * @returns {Object} Object containing sortedInstances, sortedMetricsInstances, sortConfig, and handleRequestSort.
 */
export const useInstanceSorting = (instances) => {
	const [sortConfig, setSortConfig] = useState({
		key: null,
		direction: SORT_DIRECTION_ASC,
	});

	const handleRequestSort = useCallback((key) => {
		setSortConfig((prev) => {
			const isAsc = prev.key === key && prev.direction === SORT_DIRECTION_ASC;
			return { key, direction: isAsc ? SORT_DIRECTION_DESC : SORT_DIRECTION_ASC };
		});
	}, []);

	const sortedInstances = useMemo(() => {
		return [...instances].sort((a, b) => {
			const nameA = getInstanceDisplayName(a);
			const nameB = getInstanceDisplayName(b);
			return compareMetricNames(nameA, nameB);
		});
	}, [instances]);

	const sortedMetricsInstances = useMemo(() => {
		// If sorting by instance ID in ascending order (default), or no sort key, use the pre-sorted instances.
		if (
			!sortConfig.key ||
			(sortConfig.key === SORT_KEY_INSTANCE_ID && sortConfig.direction === SORT_DIRECTION_ASC)
		) {
			return sortedInstances;
		}

		// If sorting by instance ID in descending order, just reverse the pre-sorted instances.
		if (sortConfig.key === SORT_KEY_INSTANCE_ID && sortConfig.direction === SORT_DIRECTION_DESC) {
			return [...sortedInstances].reverse();
		}

		// Sort by metric value
		return [...sortedInstances].sort((a, b) => {
			const valA = getMetricValue(a.metrics?.[sortConfig.key]);
			const valB = getMetricValue(b.metrics?.[sortConfig.key]);

			if (valA === valB) return 0;
			// Push null/undefined to the end
			if (valA === null || valA === undefined) return 1;
			if (valB === null || valB === undefined) return -1;

			let comparison = 0;
			if (typeof valA === "number" && typeof valB === "number") {
				comparison = valA - valB;
			} else {
				comparison = String(valA).toLowerCase().localeCompare(String(valB).toLowerCase());
			}
			return sortConfig.direction === SORT_DIRECTION_ASC ? comparison : -comparison;
		});
	}, [sortedInstances, sortConfig]);

	return {
		sortedInstances,
		sortedMetricsInstances,
		sortConfig,
		handleRequestSort,
	};
};
