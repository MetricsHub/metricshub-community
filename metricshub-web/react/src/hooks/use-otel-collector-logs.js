import { useState, useCallback } from "react";
import { otelCollectorApi } from "../api/config/otel-collector-api";

/**
 * Hook for OTEL Collector log tail: state and fetch.
 * @returns {{ logs: string, logsLoading: boolean, logsError: string | null, tailLines: number, setTailLines: (n: number) => void, fetchLogs: () => Promise<void> }}
 */
export function useOtelCollectorLogs() {
	const [logs, setLogs] = useState("");
	const [logsLoading, setLogsLoading] = useState(false);
	const [logsError, setLogsError] = useState(null);
	const [tailLines, setTailLines] = useState(200);

	const fetchLogs = useCallback(async () => {
		setLogsLoading(true);
		setLogsError(null);
		try {
			const text = await otelCollectorApi.getLogs(tailLines);
			setLogs(text ?? "");
		} catch (e) {
			setLogsError(e?.message || "Failed to load logs");
			setLogs("");
		} finally {
			setLogsLoading(false);
		}
	}, [tailLines]);

	return {
		logs,
		logsLoading,
		logsError,
		tailLines,
		setTailLines,
		fetchLogs,
	};
}
