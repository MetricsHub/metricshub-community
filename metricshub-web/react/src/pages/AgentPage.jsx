import * as React from "react";
import {
	Box,
	Paper,
	Stack,
	Typography,
	Button,
	CircularProgress,
	Alert,
	Collapse,
	IconButton,
	Divider,
	Card,
	CardContent,
} from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import RefreshIcon from "@mui/icons-material/Autorenew";
import RestartAltIcon from "@mui/icons-material/RestartAlt";
import CloseIcon from "@mui/icons-material/Close";
import MemoryIcon from "@mui/icons-material/Memory";
import SpeedIcon from "@mui/icons-material/Speed";
import MonitorHeartIcon from "@mui/icons-material/MonitorHeart";
import DevicesIcon from "@mui/icons-material/Devices";
import SettingsInputComponentIcon from "@mui/icons-material/SettingsInputComponent";
import { useAppDispatch, useAppSelector } from "../hooks/store";
import { fetchApplicationStatus, restartAgent } from "../store/thunks/application-status-thunks";
import { formatBytes } from "../utils/formatters";
import { useSnackbar } from "../hooks/use-snackbar";
import { useAuth } from "../hooks/use-auth";
import { dataGridSx } from "../components/explorer/views/common/table-styles";

/** Keys to exclude from status details table (already displayed elsewhere) */
const EXCLUDED_STATUS_KEYS = [
	"status",
	"agentInfo",
	"numberOfMonitors",
	"numberOfConfiguredResources",
	"memoryUsageBytes",
	"memoryUsagePercent",
	"cpuUsage",
];

/** Column definitions for key-value DataGrid tables */
const KEY_VALUE_COLUMNS = [
	{ field: "property", headerName: "PROPERTY", flex: 1, minWidth: 200 },
	{ field: "value", headerName: "VALUE", flex: 2, minWidth: 200 },
];

/**
 * Returns a color based on usage percentage thresholds.
 * @param {number | undefined} percentage - The usage percentage
 * @returns {string} Hex color code
 */
const getUsageColor = (percentage) => {
	if (typeof percentage !== "number") return "#1976d2";
	if (percentage < 50) return "#2e7d32";
	if (percentage < 80) return "#ed6c02";
	return "#d32f2f";
};

/**
 * Converts a value to a display string for the table.
 * @param {unknown} value - The value to format
 * @returns {string} Formatted string representation
 */
const formatTableValue = (value) => {
	if (value === null || value === undefined) return "—";
	if (typeof value === "object") return JSON.stringify(value);
	return String(value);
};

/**
 * Transforms an object into DataGrid rows.
 * @param {Record<string, unknown>} obj - The object to transform
 * @param {string[]} [excludeKeys=[]] - Keys to exclude from transformation
 * @returns {Array<{id: string, property: string, value: string}>} DataGrid rows
 */
const objectToRows = (obj, excludeKeys = []) =>
	Object.entries(obj)
		.filter(([key]) => !excludeKeys.includes(key))
		.map(([key, value]) => ({
			id: key,
			property: key,
			value: formatTableValue(value),
		}));

/**
 * Stat card component displaying a metric with icon.
 * @param {object} props - Component props
 * @param {React.ReactNode} props.icon - Icon element
 * @param {string} props.label - Label text
 * @param {string | number} props.value - Main value
 * @param {string} [props.subValue] - Secondary value
 * @param {string} props.bgcolor - Background color
 */
const StatCard = React.memo(({ icon, label, value, subValue, bgcolor }) => (
	<Paper
		elevation={0}
		sx={{
			bgcolor,
			p: 2.5,
			borderRadius: 2,
			color: "white",
			display: "flex",
			flexDirection: "column",
			gap: 1,
			minWidth: 200,
			flex: "1 1 200px",
		}}
	>
		<Box sx={{ display: "flex", alignItems: "center", gap: 1, opacity: 0.9 }}>
			{icon}
			<Typography variant="subtitle2" sx={{ textTransform: "uppercase", letterSpacing: 0.5 }}>
				{label}
			</Typography>
		</Box>
		<Typography variant="h4" sx={{ fontWeight: "bold" }}>
			{value}
		</Typography>
		{subValue && (
			<Typography variant="body2" sx={{ opacity: 0.8 }}>
				{subValue}
			</Typography>
		)}
	</Paper>
));

StatCard.displayName = "StatCard";

/**
 * Reusable key-value table component using DataGrid.
 * @param {object} props - Component props
 * @param {string} props.title - Section title
 * @param {Array<{id: string, property: string, value: string}>} props.rows - Table rows
 */
const KeyValueTable = React.memo(({ title, rows }) => {
	if (!rows || rows.length === 0) return null;

	return (
		<Box>
			<Typography variant="h6" sx={{ mb: 2 }}>
				{title}
			</Typography>
			<DataGrid
				rows={rows}
				columns={KEY_VALUE_COLUMNS}
				disableRowSelectionOnClick
				hideFooter
				autoHeight
				density="compact"
				sx={dataGridSx}
			/>
		</Box>
	);
});

KeyValueTable.displayName = "KeyValueTable";

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

	// Memoized OS icon
	const osIcon = React.useMemo(() => {
		if (typeof osType !== "string") return null;
		const lower = osType.toLowerCase();
		if (lower.includes("windows")) {
			return (
				<Box component="img" src="/windows.svg" alt="Windows" sx={{ width: 48, height: 48 }} />
			);
		}
		if (lower.includes("linux")) {
			return <Box component="img" src="/linux.svg" alt="Linux" sx={{ width: 64, height: 64 }} />;
		}
		return null;
	}, [osType]);

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
				height: "calc(100vh - 64px)",
				overflow: "auto",
				p: { xs: 2, sm: 3 },
			}}
		>
			<Stack spacing={3} sx={{ maxWidth: 1200, mx: "auto" }}>
				{/* Header */}
				<Box
					sx={{
						display: "flex",
						alignItems: "center",
						justifyContent: "space-between",
						flexWrap: "wrap",
						gap: 2,
					}}
				>
					<Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
						{osIcon}
						<Box>
							<Typography variant="h4" sx={{ fontWeight: 600 }}>
								{title}
							</Typography>
							{version && (
								<Typography variant="body1" color="text.secondary">
									Version {version}
								</Typography>
							)}
						</Box>
					</Box>
					<Stack direction="row" spacing={1}>
						<Button
							variant="outlined"
							color="inherit"
							startIcon={loading ? <CircularProgress size={16} /> : <RefreshIcon />}
							onClick={handleRefresh}
							disabled={loading}
						>
							Refresh
						</Button>
						<Button
							variant="contained"
							color="warning"
							startIcon={
								restarting ? <CircularProgress size={16} color="inherit" /> : <RestartAltIcon />
							}
							onClick={handleRestart}
							disabled={restarting || isReadOnly}
						>
							Restart Agent
						</Button>
					</Stack>
				</Box>

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
				<Box>
					<Typography variant="h6" sx={{ mb: 2 }}>
						Metrics
					</Typography>
					<Stack
						direction="row"
						spacing={2}
						sx={{
							flexWrap: "wrap",
							gap: 2,
							"& > *": {
								flex: { xs: "1 1 100%", sm: "1 1 calc(50% - 8px)", md: "1 1 calc(25% - 12px)" },
								minWidth: { xs: "100%", sm: 200 },
							},
						}}
					>
						{typeof numberOfMonitors === "number" && (
							<StatCard
								icon={<MonitorHeartIcon />}
								label="Monitors"
								value={numberOfMonitors.toLocaleString()}
								bgcolor="#1976d2"
							/>
						)}
						{typeof numberOfConfiguredResources === "number" && (
							<StatCard
								icon={<DevicesIcon />}
								label="Resources"
								value={numberOfConfiguredResources.toLocaleString()}
								bgcolor="#7b1fa2"
							/>
						)}
						{typeof memoryUsageBytes === "number" && (
							<StatCard
								icon={<MemoryIcon />}
								label="Memory Usage"
								value={formatBytes(memoryUsageBytes)}
								subValue={
									typeof memoryUsagePercent === "number"
										? `${memoryUsagePercent.toFixed(1)}% of available`
										: undefined
								}
								bgcolor={getUsageColor(memoryUsagePercent)}
							/>
						)}
						{typeof cpuUsage === "number" && (
							<StatCard
								icon={<SpeedIcon />}
								label="CPU Usage"
								value={`${cpuUsage.toFixed(1)}%`}
								bgcolor={getUsageColor(cpuUsage)}
							/>
						)}
					</Stack>
				</Box>

				<Divider />

				{/* Agent Information Table */}
				<KeyValueTable title="Agent Information" rows={agentInfoRows} />

				{agentInfoRows.length > 0 && statusDetailsRows.length > 0 && <Divider />}

				{/* Status Details Table */}
				<KeyValueTable title="Status Details" rows={statusDetailsRows} />

				{/* OpenTelemetry Configuration (Placeholder) */}
				<Box>
					<Typography variant="h6" sx={{ mb: 2 }}>
						OpenTelemetry Configuration
					</Typography>
					<Card variant="outlined">
						<CardContent>
							<Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
								<SettingsInputComponentIcon sx={{ fontSize: 40, color: "text.secondary" }} />
								<Box>
									<Typography variant="body1" color="text.secondary">
										OpenTelemetry destination configuration will be displayed here.
									</Typography>
									<Typography variant="body2" color="text.disabled" sx={{ mt: 0.5 }}>
										This feature is coming soon.
									</Typography>
								</Box>
							</Box>
						</CardContent>
					</Card>
				</Box>

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
