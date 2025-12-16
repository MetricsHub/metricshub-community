import * as React from "react";
import { useDispatch, useSelector } from "react-redux";
import { useNavigate } from "react-router-dom";
import {
	Box,
	Typography,
	Accordion,
	AccordionSummary,
	AccordionDetails,
	TableBody,
	TableCell,
	TableHead,
	TableRow,
	Tooltip,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import MonitorIcon from "@mui/icons-material/Monitor";
import { prettifyKey } from "../../../../../utils/text-prettifier";
import {
	getMetricMetadata,
	getMetricValue,
	compareMetricEntries,
	getBaseMetricKey,
} from "../../../../../utils/metrics-helper";
import PivotGroupSection from "./PivotGroupSection";
import InstanceMetricsTable from "./InstanceMetricsTable";
import HoverInfo from "./HoverInfo";
import {
	selectResourceUiState,
	setMonitorExpanded,
} from "../../../../../store/slices/explorer-slice";
import { renderAttributesRows } from "../../common/ExplorerTableHelpers.jsx";
import DashboardTable from "../../common/DashboardTable";
import TruncatedText from "../../common/TruncatedText";
import MetricValueCell from "../../common/MetricValueCell";
import { truncatedCellSx } from "../../common/table-styles";
import { paths } from "../../../../../paths";
import { flashBlueAnimation } from "../../../../../utils/animations";

/**
 * Natural sort for metric names like cpu0, cpu1, cpu10, ...
 */
const naturalMetricCompare = compareMetricEntries;

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

/**
 * Renders a single connector accordion with its attributes, metrics, and nested monitors.
 *
 * @param {{
 *   connector: any,
 *   connectorIndex: number,
 *   resourceId: string,
 *   resourceName: string,
 *   resourceGroupName: string,
 *   maxNameLength: number,
 *   maxCountLength: number,
 *   highlightedId: string,
 *   isLast: boolean
 * }} props
 */
const ConnectorAccordion = ({
	connector,
	connectorIndex,
	resourceId,
	resourceName,
	resourceGroupName,
	maxNameLength,
	maxCountLength,
	highlightedId,
	isLast,
}) => {
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

	// Calculate max length of monitor names for alignment within this connector
	const maxMonitorNameLength = React.useMemo(() => {
		return monitors.reduce((max, monitor) => {
			const name = prettifyKey(monitor.name || "");
			return Math.max(max, name.length);
		}, 0);
	}, [monitors]);

	// Calculate max length of instance counts for alignment within this connector
	const maxMonitorCountLength = React.useMemo(() => {
		return monitors.reduce((max, monitor) => {
			const count = (monitor.instances || []).length;
			return Math.max(max, count.toString().length);
		}, 0);
	}, [monitors]);

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
					bgcolor: "action.hover",
					"&:hover": {
						bgcolor: "action.selected",
					},
					"& .MuiAccordionSummary-content": { my: 0, ml: 0 },
				}}
			>
				<Box sx={{ display: "flex", alignItems: "center", width: "100%", pr: 2 }}>
					<Typography
						variant="h6"
						sx={{
							fontWeight: 600,
							width: `${maxNameLength + 1}ch`,
							flexShrink: 0,
						}}
					>
						{prettifyKey(connector.name)}
					</Typography>
					<Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
						<HoverInfo
							title="Number of monitor types"
							sx={{ display: "flex", alignItems: "center" }}
						>
							<Box
								component="span"
								sx={{
									width: `${Math.max(24, maxCountLength * 12)}px`,
									display: "flex",
									justifyContent: "center",
									alignItems: "center",
									borderRadius: 999,
									fontSize: 12,
									fontWeight: 500,
									bgcolor: "primary.main",
									color: "primary.contrastText",
								}}
							>
								{monitors.length}
							</Box>
						</HoverInfo>
						{statusValue && (
							<HoverInfo
								title={`Status of ${prettifyKey(connector.name)}`}
								sx={{ display: "flex", alignItems: "center" }}
							>
								<Box
									component="span"
									sx={{
										minWidth: 24,
										px: 1,
										display: "flex",
										justifyContent: "center",
										alignItems: "center",
										borderRadius: 999,
										fontSize: 12,
										fontWeight: 500,
										bgcolor: statusValue === "ok" ? "success.main" : "error.main",
										color: "white",
										textTransform: "uppercase",
									}}
								>
									{statusValue}
								</Box>
							</HoverInfo>
						)}
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
								<Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 600, mb: 1 }}>
									Attributes
								</Typography>
								<DashboardTable>
									<TableHead>
										<TableRow>
											<TableCell sx={{ width: "50%" }}>Key</TableCell>
											<TableCell sx={{ width: "50%" }}>Value</TableCell>
										</TableRow>
									</TableHead>
									<TableBody>{renderAttributesRows(connector.attributes)}</TableBody>
								</DashboardTable>
							</Box>
						)}

						{/* Connector Metrics Table */}
						{showMetricsTable && (
							<Box>
								<Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 600, mb: 1 }}>
									Metrics
								</Typography>
								<DashboardTable>
									<TableHead>
										<TableRow>
											<TableCell sx={{ width: "50%" }}>Name</TableCell>
											<TableCell align="left" sx={{ width: "50%" }}>
												Value
											</TableCell>
										</TableRow>
									</TableHead>
									<TableBody>
										{Object.entries(connector.metrics).map(([name, metric]) => {
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

											return (
												<TableRow key={name}>
													<TableCell sx={truncatedCellSx}>
														<TruncatedText text={name}>{name}</TruncatedText>
													</TableCell>
													<MetricValueCell value={value} unit={unit} align="left" />
												</TableRow>
											);
										})}
									</TableBody>
								</DashboardTable>
							</Box>
						)}
					</Box>
				)}

				{/* Monitors Section */}
				<Box sx={{ display: "flex", flexDirection: "column" }}>
					{monitors.map((monitor) => {
						const uniqueMonitorKey = `${connectorKey}-${monitor.name}`;
						const instances = Array.isArray(monitor.instances) ? monitor.instances : [];
						const sortedInstances = [...instances].sort((a, b) =>
							naturalMetricCompare([a.name || ""], [b.name || ""]),
						);
						const pivotGroups = buildPivotGroups(sortedInstances);
						const isMonitorExpanded = !!expandedMonitors[uniqueMonitorKey];

						return (
							<Accordion
								key={uniqueMonitorKey}
								expanded={isMonitorExpanded}
								onChange={handleMonitorToggle(uniqueMonitorKey)}
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
										"&:hover": {
											bgcolor: "action.hover",
										},
										"& .MuiAccordionSummary-content": { my: 0, ml: 0 },
									}}
								>
									<Box sx={{ display: "flex", alignItems: "center", width: "100%", pr: 2 }}>
										<Typography
											variant="subtitle1"
											sx={{
												fontWeight: 500,
												width: `${maxMonitorNameLength + 1}ch`,
												flexShrink: 0,
											}}
										>
											{prettifyKey(monitor.name)}
										</Typography>
										<Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
											<HoverInfo
												title="Number of instances"
												sx={{ display: "flex", alignItems: "center" }}
											>
												<Box
													component="span"
													sx={{
														width: `${Math.max(24, maxMonitorCountLength * 12)}px`,
														display: "flex",
														justifyContent: "center",
														alignItems: "center",
														borderRadius: 999,
														fontSize: 12,
														fontWeight: 500,
														bgcolor: "action.selected",
														color: "text.primary",
													}}
												>
													{instances.length}
												</Box>
											</HoverInfo>
											<HoverInfo title="Open Monitor Type Page">
												<Box
													component="span"
													onClick={(e) => {
														e.stopPropagation();
														navigate(
															paths.explorerMonitorType(
																resourceGroupName,
																resourceName,
																monitor.name,
															),
														);
													}}
													sx={{
														p: 0.5,
														display: "inline-flex",
														borderRadius: "50%",
														cursor: "pointer",
														"&:hover": {
															bgcolor: "action.hover",
														},
													}}
													role="button"
													tabIndex={0}
												>
													<MonitorIcon fontSize="small" color="action" />
												</Box>
											</HoverInfo>
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
												const metrics = inst?.metrics ?? {};
												const metricEntries = Object.entries(metrics).map(([k, v]) => [
													k,
													getMetricValue(v),
												]);
												return (
													<InstanceMetricsTable
														key={inst?.attributes?.id || inst.name}
														instance={inst}
														metricEntries={metricEntries}
														naturalMetricCompare={naturalMetricCompare}
														metaMetrics={connector.metaMetrics}
													/>
												);
											})}
								</AccordionDetails>
							</Accordion>
						);
					})}
				</Box>
			</AccordionDetails>
		</Accordion>
	);
};

export default React.memo(ConnectorAccordion);
