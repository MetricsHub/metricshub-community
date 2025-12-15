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
		return instances.filter((instance) => {
			const id = getInstanceDisplayName(instance);
			const name = instance.attributes?.name || "";
			return (
				id.toLowerCase().includes(searchTerm.toLowerCase()) ||
				name.toLowerCase().includes(searchTerm.toLowerCase())
			);
		});
	}, [instances, searchTerm]);

	return {
		searchTerm,
		setSearchTerm,
		filteredInstances,
	};
};
