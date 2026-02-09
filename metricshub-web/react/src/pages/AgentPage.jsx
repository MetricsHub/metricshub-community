import * as React from "react";
import { Box, Stack, Typography, Alert, Collapse, IconButton, Divider } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import { useAppDispatch, useAppSelector } from "../hooks/store";
import { fetchApplicationStatus, restartAgent } from "../store/thunks/application-status-thunks";
import { useSnackbar } from "../hooks/use-snackbar";
import { useAuth } from "../hooks/use-auth";

import AgentHeader from "../components/agent/AgentHeader";
import AgentMetrics from "../components/agent/AgentMetrics";
import KeyValueTable from "../components/agent/KeyValueTable";
import AgentOpenTelemetry from "../components/agent/AgentOpenTelemetry";
import LogFilesViewer from "../components/agent/LogFilesViewer";
import { objectToRows, EXCLUDED_STATUS_KEYS } from "../components/agent/utils";

/**
 * Agent page component showing agent information, metrics, and actions.
 * Displays status metrics, agent info table, and other status details.
 */
function AgentPage() {
	const dispatch = useAppDispatch();
	const snackbar = useSnackbar();
	const { user } = useAuth();
	const isReadOnly = user?.role === "ro";

	const status = useAppSelector((s) => s.applicationStatus?.data);
	const loading = useAppSelector((s) => s.applicationStatus?.loading);
	const error = useAppSelector((s) => s.applicationStatus?.error);
	const lastUpdatedAt = useAppSelector((s) => s.applicationStatus?.lastUpdatedAt);
	const restarting = useAppSelector((s) => s.applicationStatus?.restarting);

	const [showLicenseWarning, setShowLicenseWarning] = React.useState(true);

	// Destructure status values with defaults
	const {
		numberOfConfiguredResources,
		numberOfMonitors,
		memoryUsageBytes,
		memoryUsagePercent,
		cpuUsage,
		licenseDaysRemaining,
		agentInfo,
	} = status || {};

	// Get version and osType from agentInfo
	const version = agentInfo?.version || agentInfo?.cc_version;
	const osType = agentInfo?.["os.type"];
	const serviceName = agentInfo?.["service.name"];

	// Memoized license warning computation
	const licenseWarning = React.useMemo(() => {
		if (licenseDaysRemaining == null) return null;
		if (licenseDaysRemaining < 7) {
			return { severity: "error", message: `License expires in ${licenseDaysRemaining} days!` };
		}
		if (licenseDaysRemaining < 30) {
			return { severity: "warning", message: `License expires in ${licenseDaysRemaining} days.` };
		}
		return null;
	}, [licenseDaysRemaining]);

	// Memoized agent info rows
	const agentInfoRows = React.useMemo(
		() => (agentInfo ? objectToRows(agentInfo) : []),
		[agentInfo],
	);

	// Memoized status details rows (excluding already displayed metrics)
	const statusDetailsRows = React.useMemo(
		() => (status ? objectToRows(status, EXCLUDED_STATUS_KEYS) : []),
		[status],
	);

	// Memoized title
	const title = React.useMemo(
		() =>
			serviceName === "MetricsHub Agent"
				? "MetricsHub Community"
				: serviceName || "MetricsHub Agent",
		[serviceName],
	);

	// Callbacks
	const handleRefresh = React.useCallback(() => {
		dispatch(fetchApplicationStatus());
	}, [dispatch]);

	const handleRestart = React.useCallback(() => {
		dispatch(restartAgent())
			.unwrap()
			.then(() => snackbar.success("Agent restart initiated successfully"))
			.catch((err) => snackbar.error(err || "Failed to restart agent"));
	}, [dispatch, snackbar]);

	const handleCloseLicenseWarning = React.useCallback(() => {
		setShowLicenseWarning(false);
	}, []);

	return (
		<Box
			sx={{
				p: { xs: 2, sm: 3 },
			}}
		>
			<Stack spacing={3} sx={{ maxWidth: 1200, mx: "auto" }}>
				{/* Header */}
				<AgentHeader
					osType={osType}
					title={title}
					version={version}
					loading={loading}
					restarting={restarting}
					isReadOnly={isReadOnly}
					onRefresh={handleRefresh}
					onRestart={handleRestart}
				/>

				{/* License Warning */}
				{licenseWarning && (
					<Collapse in={showLicenseWarning}>
						<Alert
							severity={licenseWarning.severity}
							action={
								<IconButton
									aria-label="close"
									color="inherit"
									size="small"
									onClick={handleCloseLicenseWarning}
								>
									<CloseIcon fontSize="inherit" />
								</IconButton>
							}
						>
							{licenseWarning.message}
						</Alert>
					</Collapse>
				)}

				{/* Error */}
				{error && <Alert severity="error">{error}</Alert>}

				{/* Stats Cards */}
				<AgentMetrics
					numberOfMonitors={numberOfMonitors}
					numberOfConfiguredResources={numberOfConfiguredResources}
					memoryUsageBytes={memoryUsageBytes}
					memoryUsagePercent={memoryUsagePercent}
					cpuUsage={cpuUsage}
				/>

				<Divider />

				{/* Agent Information Table */}
				<KeyValueTable title="Agent Information" rows={agentInfoRows} />

				{agentInfoRows.length > 0 && statusDetailsRows.length > 0 && <Divider />}

				{/* Status Details Table */}
				<KeyValueTable title="Status Details" rows={statusDetailsRows} />

				{/* OpenTelemetry Configuration */}
				<AgentOpenTelemetry />

				<Divider />

				{/* Log Files Viewer */}
				<LogFilesViewer isReadOnly={isReadOnly} />

				{/* Last updated */}
				{lastUpdatedAt && (
					<Typography variant="caption" color="text.secondary" sx={{ textAlign: "right" }}>
						Last updated: {new Date(lastUpdatedAt).toLocaleTimeString()}
					</Typography>
				)}
			</Stack>
		</Box>
	);
}

export default AgentPage;
