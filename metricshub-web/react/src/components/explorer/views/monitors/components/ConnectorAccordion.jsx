import * as React from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import { useTheme } from "@mui/material/styles";
import {
	Box,
	Typography,
	Accordion,
	AccordionSummary,
	AccordionDetails,
	Tooltip,
} from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import SettingsInputHdmiIcon from "@mui/icons-material/SettingsInputHdmi";
import { prettifyKey } from "../../../../../utils/text-prettifier";
import { dataGridSx } from "../../common/table-styles";
import {
	getMetricMetadata,
	getMetricValue,
	compareMetricEntries,
	getBaseMetricKey,
} from "../../../../../utils/metrics-helper";
import PivotGroupSection from "./PivotGroupSection";
import InstanceMetricsTable from "./InstanceMetricsTable";
import HoverInfo from "./HoverInfo";
import CountBadge from "../../common/CountBadge";
import {
	selectResourceUiState,
	setMonitorExpanded,
} from "../../../../../store/slices/explorer-slice";
import TruncatedText from "../../common/TruncatedText";
import MetricNameHighlighter from "../../common/MetricNameHighlighter.jsx";
import MetricValueCell from "../../common/MetricValueCell";
import { paths } from "../../../../../paths";
import { flashBlueAnimation } from "../../../../../utils/animations";
import MonitorTypeIcon from "../icons/MonitorTypeIcon";

/**
 * Decide whether we can pivot a monitor into one or more
 * "instances as rows, metrics as columns" tables, grouped by
 * a common base metric name (e.g. system.cpu.time.*, system.cpu.utilization.*).
 */
const buildPivotGroups = (instances) => {
	if (!Array.isArray(instances) || instances.length <= 1) return [];

	const perInstanceEntries = instances.map((inst) => {
		const metrics = inst?.metrics ?? {};
		return Object.entries(metrics).filter(([name]) => !name.startsWith("__"));
	});

	// Collect all unique metric keys from all instances
	const allKeys = new Set();
	perInstanceEntries.forEach((entries) => {
		entries.forEach(([name]) => allKeys.add(name));
	});

	const sortedKeys = Array.from(allKeys);
	if (sortedKeys.length === 0) return [];

	// Derive groups by base name.
	const groupsMap = new Map();
	for (const key of sortedKeys) {
		const base = getBaseMetricKey(key);

		if (!groupsMap.has(base)) {
			groupsMap.set(base, []);
		}
		groupsMap.get(base).push(key);
	}

	return Array.from(groupsMap.entries()).map(([baseName, metricKeys]) => ({
		baseName,
		metricKeys,
	}));
};

const MonitorAccordion = React.memo(function MonitorAccordion({
	monitor,
	connectorKey,
	resourceId,
	resourceGroupName,
	resourceName,
	connector,
	expandedMonitors,
	handleMonitorToggle,
	navigate,
}) {
	const uniqueMonitorKey = `${connectorKey}-${monitor.name}`;
	const instances = React.useMemo(
		() => (Array.isArray(monitor.instances) ? monitor.instances : []),
		[monitor.instances],
	);
	const sortedInstances = React.useMemo(
		() => [...instances].sort((a, b) => compareMetricEntries([a.name || ""], [b.name || ""])),
		[instances],
	);
	const pivotGroups = React.useMemo(() => buildPivotGroups(sortedInstances), [sortedInstances]);
	const isMonitorExpanded = !!expandedMonitors[uniqueMonitorKey];

	return (
		<Accordion
			expanded={isMonitorExpanded}
			onChange={handleMonitorToggle(uniqueMonitorKey)}
			TransitionProps={{ unmountOnExit: true }}
			disableGutters
			elevation={0}
			square
			sx={{
				bgcolor: "transparent",
				borderTop: "1px solid",
				borderColor: "divider",
			}}
		>
			<AccordionSummary
				expandIcon={<ExpandMoreIcon />}
				sx={{
					minHeight: 40,
					cursor: "pointer",
					bgcolor: "background.default",
					pl: 4, // Indent nested monitors
					transition: "background-color 0.4s ease, color 0.4s ease",
					"&:hover": {
						bgcolor: "action.hover",
					},
					"& .MuiAccordionSummary-content": { my: 0, ml: 0 },
				}}
			>
				<Box sx={{ display: "flex", alignItems: "center", width: "100%", pr: 2 }}>
					<Box
						sx={{
							flexShrink: 0,
							mr: 1,
							display: "flex",
							alignItems: "center",
							gap: 1,
						}}
					>
						<MonitorTypeIcon type={prettifyKey(monitor.name)} />
						<Tooltip title="Open Monitor Type Page" arrow placement="top" disableInteractive>
							<Box component="span" sx={{ display: "inline-block" }}>
								<Typography
									variant="subtitle1"
									component="span"
									onClick={(e) => {
										e.stopPropagation();
										navigate(
											paths.explorerMonitorType(
												resourceGroupName,
												resourceName,
												connector.name,
												monitor.name,
											),
										);
									}}
									sx={{
										fontWeight: 500,
										cursor: "pointer",
										color: "primary.main",
										"&:hover": {
											color: "common.white",
											textDecoration: "underline",
										},
									}}
								>
									{prettifyKey(monitor.name)}
								</Typography>
							</Box>
						</Tooltip>
					</Box>
					<Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
						<CountBadge
							count={instances.length}
							title="Number of instances"
							bgcolor="action.selected"
							sx={{ fontWeight: 500 }}
						/>
					</Box>
				</Box>
			</AccordionSummary>
			<AccordionDetails sx={{ pl: 5, pr: 1.5, py: 0 }}>
				{pivotGroups.length > 0
					? pivotGroups.map((group) => (
							<PivotGroupSection
								key={group.baseName}
								group={group}
								sortedInstances={sortedInstances}
								resourceId={resourceId}
								metaMetrics={connector.metaMetrics}
							/>
						))
					: sortedInstances.map((inst) => {
							return (
								<InstanceMetricsTable
									key={inst?.attributes?.id || inst.name}
									instance={inst}
									naturalMetricCompare={compareMetricEntries}
									metaMetrics={connector.metaMetrics}
								/>
							);
						})}
			</AccordionDetails>
		</Accordion>
	);
});

/**
 * Renders a single connector accordion with its attributes, metrics, and nested monitors.
 *
 * @param {object} props - Component props
 * @param {any} props.connector - The connector object
 * @param {number} props.connectorIndex - The index of the connector
 * @param {string} props.resourceId - The ID of the resource
 * @param {string} props.resourceName - The name of the resource
 * @param {string} props.resourceGroupName - The name of the resource group
 * @param {string} props.highlightedId - The ID of the highlighted element
 * @param {boolean} props.isLast - Whether this is the last connector in the list
 */
const ConnectorAccordion = ({
	connector,
	connectorIndex,
	resourceId,
	resourceName,
	resourceGroupName,
	highlightedId,
	isLast,
}) => {
	const theme = useTheme();
	const isDarkMode = theme.palette.mode === "dark";

	const dispatch = useDispatch();
	const navigate = useNavigate();
	const uiState = useSelector((state) =>
		resourceId ? selectResourceUiState(resourceId)(state) : null,
	);
	const expandedMonitors = uiState?.monitors || {};

	const monitors = React.useMemo(
		() => (Array.isArray(connector.monitors) ? connector.monitors : []),
		[connector.monitors],
	);
	const connectorKey = connector.name || `connector-${connectorIndex}`;
	const isConnectorExpanded = !!expandedMonitors[connectorKey];
	const isHighlighted = highlightedId === connectorKey;

	const statusMetric = connector.metrics?.["metricshub.connector.status"];
	const statusValue = getMetricValue(statusMetric);

	const metricKeys = connector.metrics ? Object.keys(connector.metrics) : [];
	const showMetricsTable =
		metricKeys.length > 0 &&
		!(metricKeys.length === 1 && metricKeys[0] === "metricshub.connector.status");

	const handleConnectorToggle = React.useCallback(
		(e, isExpanded) => {
			if (resourceId) {
				dispatch(
					setMonitorExpanded({
						resourceId,
						monitorName: connectorKey,
						expanded: isExpanded,
					}),
				);
			}
		},
		[dispatch, resourceId, connectorKey],
	);

	const handleMonitorToggle = React.useCallback(
		(uniqueMonitorKey) => (e, isExpanded) => {
			if (resourceId) {
				dispatch(
					setMonitorExpanded({
						resourceId,
						monitorName: uniqueMonitorKey,
						expanded: isExpanded,
					}),
				);
			}
		},
		[dispatch, resourceId],
	);

	return (
		<Accordion
			id={connectorKey}
			expanded={isConnectorExpanded}
			onChange={handleConnectorToggle}
			TransitionProps={{ unmountOnExit: true }}
			disableGutters
			elevation={0}
			square
			sx={{
				bgcolor: "transparent",
				...(isHighlighted && flashBlueAnimation),
				borderTop: "1px solid",
				borderColor: "divider",
				...(isLast ? { borderBottom: "1px solid", borderBottomColor: "divider" } : {}),
			}}
		>
			<AccordionSummary
				expandIcon={<ExpandMoreIcon />}
				sx={{
					minHeight: 48,
					cursor: "pointer",
					transition: "background-color 0.4s ease, color 0.4s ease",
					bgcolor:
						statusValue && statusValue !== "ok"
							? isDarkMode
								? "error.darkest"
								: "error.lightest"
							: "action.hover",
					color:
						statusValue && statusValue !== "ok"
							? isDarkMode
								? "white"
								: "error.darkest"
							: "inherit",
					"&:hover": {
						bgcolor:
							statusValue && statusValue !== "ok"
								? isDarkMode
									? "error.dark"
									: "error.light"
								: "action.selected",
					},
					"& .MuiAccordionSummary-content": { my: 0, ml: 0 },
					"& .MuiAccordionSummary-expandIconWrapper": {
						transition: "color 0.4s ease",
						color:
							statusValue && statusValue !== "ok"
								? isDarkMode
									? "white"
									: "error.darkest"
								: "inherit",
					},
				}}
			>
				<Box sx={{ display: "flex", alignItems: "center", width: "100%", pr: 2 }}>
					<Box sx={{ display: "flex", alignItems: "center" }}>
						<SettingsInputHdmiIcon
							sx={{
								mr: 1,
								transition: "color 0.4s ease",
								color:
									statusValue === "ok"
										? "success.main"
										: statusValue
											? isDarkMode
												? "inherit"
												: "error.darkest"
											: "inherit",
							}}
						/>
						<Tooltip
							title={statusValue && statusValue !== "ok" ? "This connector has failed" : ""}
							placement="top"
						>
							<Typography
								variant="h6"
								sx={{
									fontWeight: 600,
									flexShrink: 0,
									transition: "color 0.4s ease",
								}}
							>
								{prettifyKey(connector.name)}
							</Typography>
						</Tooltip>
						<CountBadge count={monitors.length} title="Number of monitor types" sx={{ ml: 1 }} />
					</Box>
				</Box>
			</AccordionSummary>
			<AccordionDetails sx={{ p: 0 }}>
				{/* Connector Attributes & Metrics Container */}
				{((connector.attributes && Object.keys(connector.attributes).length > 0) ||
					showMetricsTable) && (
					<Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 2 }}>
						{/* Connector Attributes Table */}
						{connector.attributes && Object.keys(connector.attributes).length > 0 && (
							<Box>
								<Typography
									variant="subtitle2"
									gutterBottom
									sx={{ fontWeight: 600, mb: 1, transition: "color 0.4s ease" }}
								>
									Attributes
								</Typography>
								<DataGrid
									rows={Object.entries(connector.attributes).map(([key, value]) => ({
										id: key,
										key,
										value,
									}))}
									columns={[
										{ field: "key", headerName: "Key", flex: 1 },
										{ field: "value", headerName: "Value", flex: 1 },
									]}
									disableRowSelectionOnClick
									hideFooter
									autoHeight
									density="compact"
									sx={dataGridSx}
								/>
							</Box>
						)}

						{/* Connector Metrics Table */}
						{showMetricsTable && (
							<Box>
								<Typography
									variant="subtitle2"
									gutterBottom
									sx={{ fontWeight: 600, mb: 1, transition: "color 0.4s ease" }}
								>
									Metrics
								</Typography>
								<DataGrid
									rows={Object.entries(connector.metrics).map(([name, metric]) => {
										let value = metric;
										let unit = undefined;

										if (metric && typeof metric === "object" && "value" in metric) {
											value = metric.value;
											unit = metric.unit;
										}

										if (!unit) {
											const meta = getMetricMetadata(name, connector.metaMetrics);
											if (meta?.unit) unit = meta.unit;
										}
										return {
											id: name,
											name,
											value,
											unit,
										};
									})}
									columns={[
										{
											field: "name",
											headerName: "Name",
											flex: 1,
											renderCell: (params) => <MetricNameHighlighter name={params.value} />,
										},
										{
											field: "value",
											headerName: "Value",
											flex: 1,
											align: "left",
											headerAlign: "left",
											renderCell: (params) => (
												<MetricValueCell
													value={params.row.value}
													unit={params.row.unit}
													align="left"
												/>
											),
										},
									]}
									disableRowSelectionOnClick
									hideFooter
									autoHeight
									density="compact"
									sx={dataGridSx}
								/>
							</Box>
						)}
					</Box>
				)}

				{/* Monitors Section */}
				<Box sx={{ display: "flex", flexDirection: "column" }}>
					{monitors.map((monitor) => (
						<MonitorAccordion
							key={`${connectorKey}-${monitor.name}`}
							monitor={monitor}
							connectorKey={connectorKey}
							resourceId={resourceId}
							resourceGroupName={resourceGroupName}
							resourceName={resourceName}
							connector={connector}
							expandedMonitors={expandedMonitors}
							handleMonitorToggle={handleMonitorToggle}
							navigate={navigate}
						/>
					))}
				</Box>
			</AccordionDetails>
		</Accordion>
	);
};

export default React.memo(ConnectorAccordion);
