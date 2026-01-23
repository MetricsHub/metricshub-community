import { useEffect, useCallback } from "react";
import { useDispatch } from "react-redux";
import { fetchTopLevelResource, fetchGroupedResource } from "../store/thunks/explorer-thunks";

/**
 * Hook to fetch resource data (top-level or grouped) and poll periodically.
 *
 * @param {{
 *   resourceName?: string,
 *   resourceGroupName?: string,
 *   isPaused?: boolean,
 *   intervalMs?: number,
 *   onFetch?: () => void
 * }} params
 */
export const useResourceFetcher = ({
	resourceName,
	resourceGroupName,
	isPaused = false,
	intervalMs = 10000,
	onFetch,
}) => {
	const dispatch = useDispatch();
	const decodedName = resourceName ? decodeURIComponent(resourceName) : null;
	const decodedGroup = resourceGroupName ? decodeURIComponent(resourceGroupName) : null;

	const fetchData = useCallback(() => {
		if (!decodedName) return;
		if (decodedGroup) {
			dispatch(fetchGroupedResource({ groupName: decodedGroup, resourceName: decodedName }));
		} else {
			dispatch(fetchTopLevelResource({ resourceName: decodedName }));
		}
		if (onFetch) {
			onFetch();
		}
	}, [dispatch, decodedName, decodedGroup, onFetch]);

	useEffect(() => {
		if (!decodedName || isPaused) return;

		fetchData();
		const interval = setInterval(fetchData, intervalMs);

		return () => clearInterval(interval);
	}, [decodedName, isPaused, intervalMs, fetchData]);

	return { fetchData };
};
