import * as React from "react";
import { useAppDispatch, useAppSelector } from "./store";
import { fetchApplicationStatus } from "../store/thunks/application-status-thunks";

/**
 * Operating system of the machine running the MetricsHub agent, from the
 * {@code /api/status} agentInfo attributes ({@code os.type}).
 *
 * @returns {string} lowercase os.type (e.g. "windows", "linux"), or "" while unknown
 */
export const useAgentOsType = () => {
	const dispatch = useAppDispatch();
	const { data, loading, error } = useAppSelector((s) => s.applicationStatus);

	React.useEffect(() => {
		if (!data && !loading && !error) {
			dispatch(fetchApplicationStatus());
		}
	}, [data, loading, error, dispatch]);

	return String(data?.agentInfo?.["os.type"] ?? "")
		.trim()
		.toLowerCase();
};
