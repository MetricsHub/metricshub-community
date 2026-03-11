import * as React from "react";
import { Button, Stack } from "@mui/material";
import RestartAltIcon from "@mui/icons-material/RestartAlt";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import { otelCollectorApi } from "../../api/config/otel-collector-api";
import { useSnackbar } from "../../hooks/use-snackbar";

/**
 * Restart and View logs buttons for the OTEL Collector.
 * Logs are shown in the same panel as the editor (resizable split) when logsOpen is true.
 */
export default function OtelCollectorToolbar({
	isReadOnly = false,
	logsOpen = false,
	onToggleLogs,
	onOpenLogs,
}) {
	const { show: showSnackbar } = useSnackbar();
	const [restarting, setRestarting] = React.useState(false);

	const handleRestart = React.useCallback(async () => {
		if (isReadOnly) return;
		setRestarting(true);
		try {
			await otelCollectorApi.restart();
			showSnackbar("OpenTelemetry Collector restarted successfully", { severity: "success" });
		} catch (e) {
			showSnackbar(e?.message || "Restart failed", { severity: "error" });
		} finally {
			setRestarting(false);
		}
	}, [isReadOnly, showSnackbar]);

	const handleToggleLogs = React.useCallback(() => {
		if (!logsOpen && onOpenLogs) onOpenLogs();
		onToggleLogs?.();
	}, [logsOpen, onToggleLogs, onOpenLogs]);

	return (
		<Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap">
			<Button
				size="small"
				variant="outlined"
				color="inherit"
				startIcon={<RestartAltIcon />}
				onClick={handleRestart}
				disabled={isReadOnly || restarting}
			>
				{restarting ? "Restarting…" : "Restart OTEL Collector"}
			</Button>
			<Button
				size="small"
				variant="outlined"
				color="inherit"
				startIcon={logsOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}
				onClick={handleToggleLogs}
			>
				{logsOpen ? "Hide logs" : "View logs"}
			</Button>
		</Stack>
	);
}
