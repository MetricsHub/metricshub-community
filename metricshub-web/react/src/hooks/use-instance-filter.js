import { useState, useMemo } from "react";
import { getInstanceDisplayName } from "../utils/metrics-helper";

/**
 * Hook to filter instances by search term.
 *
 * @param {Array} instances - Array of instance objects to filter.
 * @returns {Object} Object containing searchTerm, setSearchTerm, and filteredInstances.
 */
export const useInstanceFilter = (instances) => {
	const [searchTerm, setSearchTerm] = useState("");

	const filteredInstances = useMemo(() => {
		const normalizedSearch = searchTerm.toLowerCase().replace(/\s+/g, "");
		return instances.filter((instance) => {
			const id = getInstanceDisplayName(instance).toLowerCase().replace(/\s+/g, "");
			const name = (instance.attributes?.name || "").toLowerCase().replace(/\s+/g, "");
			return id.includes(normalizedSearch) || name.includes(normalizedSearch);
		});
	}, [instances, searchTerm]);

	return {
		searchTerm,
		setSearchTerm,
		filteredInstances,
	};
};
