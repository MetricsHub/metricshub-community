import * as React from "react";
import { useDispatch, useSelector } from "react-redux";
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
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { prettifyKey } from "../../../../utils/text-prettifier";
import { formatRelativeTime, formatMetricValue } from "../../../../utils/formatters";
import {
	getMetricMetadata,
	getMetricValue,
	getBaseMetricKey,
} from "../../../../utils/metrics-helper";
import MonitorsHeader from "./components/MonitorsHeader";
import PivotGroupSection from "./components/PivotGroupSection";
import InstanceMetricsTable from "./components/InstanceMetricsTable";
import HoverInfo from "./components/HoverInfo";
import { selectResourceUiState, setMonitorExpanded } from "../../../../store/slices/explorer-slice";
import { renderAttributesRows } from "../common/ExplorerTableHelpers.jsx";
import DashboardTable from "../common/DashboardTable";

/**
 * Monitors section displayed inside the Resource page.
 *
 * - Shows H2 "Monitors".
 * - For each monitor type, if instances count <= 20, lists all instances
 *   and their metrics in a table.
 * - If instances count > 20, shows a badge with the count that would
 *   redirect to a dedicated monitor type page (navigation not wired yet).
 * - Shows a "last updated" label based on when the resource
 *   data was last fetched (provided by parent).
 *
 * @param {{ connectors?: any[], lastUpdatedAt?: number | string | Date, resourceId?: string }} props
 */
const MonitorsView = ({ connectors, lastUpdatedAt, resourceId }) => {
	const dispatch = useDispatch();
	const uiState = useSelector((state) =>
		resourceId ? selectResourceUiState(resourceId)(state) : null,
	);
	const expandedMonitors = uiState?.monitors || {};
	// Force re-render every 5 seconds to update "last updated" relative time
	const [_now, setNow] = React.useState(Date.now());

	React.useEffect(() => {
		const interval = setInterval(() => setNow(Date.now()), 5000);
		return () => clearInterval(interval);
	}, []);

	/**
	 * Natural sort for metric names like cpu0, cpu1, cpu10, ...
	 */
	const naturalMetricCompare = React.useCallback((a, b) => {
		const nameA = a[0];
		const nameB = b[0];
		const re = /(.*?)(\d+)$/;
		const ma = nameA.match(re);
		const mb = nameB.match(re);
		if (!ma || !mb || ma[1] !== mb[1]) {
			// Fallback to plain string compare when no common prefix+index
			return nameA.localeCompare(nameB);
		}
		const idxA = parseInt(ma[2], 10);
		const idxB = parseInt(mb[2], 10);
		if (Number.isNaN(idxA) || Number.isNaN(idxB)) {
			return nameA.localeCompare(nameB);
		}
		if (idxA === idxB) return 0;
		return idxA < idxB ? -1 : 1;
	}, []);

	/**
	 * Decide whether we can pivot a monitor into one or more
	 * "instances as rows, metrics as columns" tables, grouped by
	 * a common base metric name (e.g. system.cpu.time.*, system.cpu.utilization.*).
	 */
	const buildPivotGroups = React.useCallback(
		(instances) => {
			if (!Array.isArray(instances) || instances.length <= 1) return [];

			const perInstanceEntries = instances.map((inst) => {
				const metrics = inst?.metrics ?? {};
				return Object.entries(metrics)
					.filter(([name]) => !name.startsWith("__"))
					.sort(naturalMetricCompare);
			});

			// Collect all unique metric keys from all instances
			const allKeys = new Set();
			perInstanceEntries.forEach((entries) => {
				entries.forEach(([name]) => allKeys.add(name));
			});

			const sortedKeys = Array.from(allKeys).sort((a, b) => naturalMetricCompare([a], [b]));
			if (sortedKeys.length === 0) return [];

			// Derive groups by base name.
			// Heuristic:
			// 1. If a metric has tags (e.g. "system.disk.io{...}"), it likely represents a multi-dimensional metric
			//    that deserves its own table. We group by its full clean name (e.g. "system.disk.io").
			// 2. If a metric has no tags (e.g. "system.cpu.utilization"), it is likely a scalar.
			//    We group these by their parent namespace (e.g. "system.cpu") to aggregate related scalars.
			const groupsMap = new Map();
			for (const key of sortedKeys) {
				const cleanKey = getBaseMetricKey(key);
				const hasTags = key.includes("{");

				let base;
				if (hasTags) {
					base = cleanKey;
				} else {
					const lastDot = cleanKey.lastIndexOf(".");
					base = lastDot > 0 ? cleanKey.slice(0, lastDot) : cleanKey;
				}

				if (!groupsMap.has(base)) {
					groupsMap.set(base, []);
				}
				groupsMap.get(base).push(key);
			}

			return Array.from(groupsMap.entries()).map(([baseName, metricKeys]) => ({
				baseName,
				metricKeys,
			}));
		},
		[naturalMetricCompare],
	);

	const safeConnectors = React.useMemo(
		() => (Array.isArray(connectors) ? connectors : []),
		[connectors],
	);

	const lastUpdatedLabel = !lastUpdatedAt ? "Never" : formatRelativeTime(lastUpdatedAt);

	// Check if there are any monitors across all connectors
	const hasAnyMonitors = safeConnectors.some(
		(connector) => Array.isArray(connector?.monitors) && connector.monitors.length > 0,
	);

	if (!hasAnyMonitors) {
		return (
			<Box>
				<MonitorsHeader lastUpdatedLabel={lastUpdatedLabel} />
				<Typography variant="body2">No connectors available for this resource.</Typography>
			</Box>
		);
	}

	return (
		<Box display="flex" flexDirection="column">
			<MonitorsHeader lastUpdatedLabel={lastUpdatedLabel} />

			{safeConnectors.map((connector, connectorIndex) => {
				const monitors = Array.isArray(connector.monitors) ? connector.monitors : [];

				const connectorKey = connector.name || `connector-${connectorIndex}`;
				const isConnectorExpanded = !!expandedMonitors[connectorKey];

				const statusMetric = connector.metrics?.["metricshub.connector.status"];
				const statusValue = getMetricValue(statusMetric);

				const metricKeys = connector.metrics ? Object.keys(connector.metrics) : [];
				const showMetricsTable =
					metricKeys.length > 0 &&
					!(metricKeys.length === 1 && metricKeys[0] === "metricshub.connector.status");

				return (
					<Accordion
						key={connectorKey}
						expanded={isConnectorExpanded}
						onChange={(e, isExpanded) => {
							if (resourceId) {
								dispatch(
									setMonitorExpanded({
										resourceId,
										monitorName: connectorKey,
										expanded: isExpanded,
									}),
								);
							}
						}}
						disableGutters
						elevation={0}
						square
						sx={{
							bgcolor: "transparent",
							borderTop: "1px solid",
							borderColor: "divider",
							...(connectorIndex === safeConnectors.length - 1
								? { borderBottom: "1px solid", borderBottomColor: "divider" }
								: {}),
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
							<Typography
								variant="h6"
								sx={{
									fontWeight: 600,
									display: "flex",
									alignItems: "center",
									columnGap: 1,
								}}
							>
								{prettifyKey(connector.name)}
								<HoverInfo
									title="Number of monitor types"
									sx={{ display: "flex", alignItems: "center" }}
								>
									<Box
										component="span"
										sx={{
											ml: 1,
											px: 1,
											minWidth: 24,
											textAlign: "center",
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
												ml: 1,
												px: 1,
												minWidth: 24,
												textAlign: "center",
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
							</Typography>
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
															<TableCell>Key</TableCell>
															<TableCell>Value</TableCell>
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
															<TableCell sx={{ width: "25%" }}>Name</TableCell>
															<TableCell align="left">Value</TableCell>
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

															const formattedValue = formatMetricValue(value, unit);

															return (
																<TableRow key={name}>
																	<TableCell>{name}</TableCell>
																	<TableCell align="left">{formattedValue}</TableCell>
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
											onChange={(e, isExpanded) => {
												if (resourceId) {
													dispatch(
														setMonitorExpanded({
															resourceId,
															monitorName: uniqueMonitorKey,
															expanded: isExpanded,
														}),
													);
												}
											}}
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
												<Typography
													variant="subtitle1"
													sx={{
														fontWeight: 500,
														display: "flex",
														alignItems: "center",
														columnGap: 1,
													}}
												>
													{prettifyKey(monitor.name)}
													<Box
														component="span"
														sx={{
															ml: 1,
															px: 1,
															minWidth: 24,
															textAlign: "center",
															borderRadius: 999,
															fontSize: 12,
															fontWeight: 500,
															bgcolor: "action.selected",
															color: "text.primary",
														}}
													>
														{instances.length}
													</Box>
												</Typography>
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
														const metricEntries = Object.entries(metrics);
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
			})}
		</Box>
	);
};

export default MonitorsView;
