import * as React from "react";
import { useSelector } from "react-redux";
import {
	Box,
	Typography,
	Tabs,
	Tab,
	TextField,
	CircularProgress,
	IconButton,
	TableBody,
	TableCell,
	TableHead,
	TableRow,
	Dialog,
	DialogTitle,
	DialogContent,
	DialogActions,
	Button,
	FormGroup,
	FormControlLabel,
	Checkbox,
	TableSortLabel,
} from "@mui/material";
import SettingsIcon from "@mui/icons-material/Settings";
import {
	selectCurrentResource,
	selectResourceLoading,
	selectResourceError,
} from "../../../../store/slices/explorer-slice";
import EntityHeader from "../common/EntityHeader";
import InstanceMetricsTable from "../monitors/components/InstanceMetricsTable";
import InstanceNameWithAttributes from "../monitors/components/InstanceNameWithAttributes";
import DashboardTable from "../common/DashboardTable";
import HoverInfo from "../monitors/components/HoverInfo";
import MetricValueCell from "../common/MetricValueCell";
import {
	getMetricValue,
	getMetricMetadata,
	compareMetricNames,
	compareMetricEntries,
	getInstanceDisplayName,
} from "../../../../utils/metrics-helper";
import { useResourceFetcher } from "../../../../hooks/use-resource-fetcher";

/**
 * Monitor Type View component.
 * Displays instances and metrics for a specific monitor type.
 *
 * @param {Object} props - Component props.
 * @param {string} props.resourceName - The name of the resource.
 * @param {string} props.resourceGroupName - The name of the resource group.
 * @param {string} props.monitorType - The type of the monitor.
 * @returns {JSX.Element} The rendered component.
 */
const MonitorTypeView = ({ resourceName, resourceGroupName, monitorType }) => {
	const [tabValue, setTabValue] = React.useState(0);
	const [searchTerm, setSearchTerm] = React.useState("");
	const [selectedMetrics, setSelectedMetrics] = React.useState([]);
	const [isSettingsOpen, setIsSettingsOpen] = React.useState(false);
	const [sortConfig, setSortConfig] = React.useState({ key: null, direction: "asc" });
	const currentResource = useSelector(selectCurrentResource);
	const loading = useSelector(selectResourceLoading);
	const error = useSelector(selectResourceError);

	const decodedName = resourceName ? decodeURIComponent(resourceName) : null;
	const decodedMonitorType = monitorType ? decodeURIComponent(monitorType) : null;

	useResourceFetcher({ resourceName, resourceGroupName });

	const handleTabChange = React.useCallback((event, newValue) => {
		setTabValue(newValue);
	}, []);

	/**
	 * Handles the sort request for the metrics table.
	 * Toggles direction if the same key is clicked, otherwise sets the new key and defaults to 'asc'.
	 *
	 * @param {string} key - The metric key or identifier to sort by.
	 */
	const handleRequestSort = React.useCallback((key) => {
		setSortConfig((prev) => {
			const isAsc = prev.key === key && prev.direction === "asc";
			return { key, direction: isAsc ? "desc" : "asc" };
		});
	}, []);

	const monitorData = React.useMemo(() => {
		if (!currentResource?.connectors) return null;
		for (const connector of currentResource.connectors) {
			if (connector.monitors) {
				const found = connector.monitors.find((m) => m.name === decodedMonitorType);
				if (found) {
					return {
						monitor: found,
						metaMetrics: connector.metaMetrics,
						connectorName: connector.name,
					};
				}
			}
		}
		return null;
	}, [currentResource, decodedMonitorType]);

	const instances = React.useMemo(() => monitorData?.monitor?.instances || [], [monitorData]);

	/**
	 * Helper to get the instance ID or name.
	 * @param {object} instance
	 * @returns {string}
	 */
	const getInstanceId = React.useCallback(
		(instance) => instance.attributes?.id || instance.name || "",
		[],
	);

	const sortedInstances = React.useMemo(() => {
		return [...instances].sort((a, b) => {
			const nameA = getInstanceId(a);
			const nameB = getInstanceId(b);
			return compareMetricNames(nameA, nameB);
		});
	}, [instances, getInstanceId]);

	const sortedMetricsInstances = React.useMemo(() => {
		// If sorting by instance ID in ascending order (default), or no sort key, use the pre-sorted instances.
		if (
			!sortConfig.key ||
			(sortConfig.key === "__instance_id__" && sortConfig.direction === "asc")
		) {
			return sortedInstances;
		}

		// If sorting by instance ID in descending order, just reverse the pre-sorted instances.
		if (sortConfig.key === "__instance_id__" && sortConfig.direction === "desc") {
			return [...sortedInstances].reverse();
		}

		// Sort by metric value
		return [...sortedInstances].sort((a, b) => {
			const valA = getMetricValue(a.metrics?.[sortConfig.key]);
			const valB = getMetricValue(b.metrics?.[sortConfig.key]);

			if (valA === valB) return 0;
			// Push null/undefined to the end
			if (valA === null || valA === undefined) return 1;
			if (valB === null || valB === undefined) return -1;

			let comparison = 0;
			if (typeof valA === "number" && typeof valB === "number") {
				comparison = valA - valB;
			} else {
				comparison = String(valA).toLowerCase().localeCompare(String(valB).toLowerCase());
			}
			return sortConfig.direction === "asc" ? comparison : -comparison;
		});
	}, [sortedInstances, sortConfig]);

	const availableMetrics = React.useMemo(() => {
		const metricsSet = new Set();
		// Check first 50 instances to gather available metrics
		for (const instance of sortedInstances.slice(0, 50)) {
			if (instance.metrics) {
				Object.keys(instance.metrics).forEach((k) => metricsSet.add(k));
			}
		}
		return Array.from(metricsSet).sort();
	}, [sortedInstances]);

	const handleMetricToggle = React.useCallback((metric) => {
		setSelectedMetrics((prev) =>
			prev.includes(metric) ? prev.filter((m) => m !== metric) : [...prev, metric],
		);
	}, []);

	const filteredInstances = React.useMemo(() => {
		return sortedInstances.filter((instance) => {
			const id = getInstanceId(instance);
			const name = instance.attributes?.name || "";
			return (
				id.toLowerCase().includes(searchTerm.toLowerCase()) ||
				name.toLowerCase().includes(searchTerm.toLowerCase())
			);
		});
	}, [sortedInstances, searchTerm, getInstanceId]);

	// Helper to get metrics for an instance
	const getMetricsForInstance = React.useCallback((instance) => {
		return Object.entries(instance.metrics || {}).map(([key, value]) => [
			key,
			getMetricValue(value),
		]);
	}, []);

	const naturalMetricCompare = React.useCallback(compareMetricEntries, []);

	if (loading && !currentResource) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	if (error) {
		return (
			<Box sx={{ p: 3 }}>
				<Typography color="error">Error loading resource: {error}</Typography>
			</Box>
		);
	}

	if (!monitorData) {
		return (
			<Box sx={{ p: 3 }}>
				<Typography>Monitor type &quot;{decodedMonitorType}&quot; not found.</Typography>
			</Box>
		);
	}

	return (
		<Box sx={{ p: 3, height: "100%", display: "flex", flexDirection: "column" }}>
			<EntityHeader
				title={`${decodedName} : ${decodedMonitorType} (${monitorData.connectorName})`}
			/>

			<Box sx={{ borderBottom: 1, borderColor: "divider", mb: 2 }}>
				<Tabs value={tabValue} onChange={handleTabChange}>
					<Tab label={`${decodedMonitorType} instances`} />
					<Tab label={`${decodedMonitorType} Metrics`} />
				</Tabs>
			</Box>

			{tabValue === 0 && (
				<Box sx={{ flex: 1, overflow: "auto" }}>
					<Box sx={{ mb: 2 }}>
						<TextField
							fullWidth
							variant="outlined"
							placeholder="Search instances..."
							value={searchTerm}
							onChange={(e) => setSearchTerm(e.target.value)}
							size="small"
						/>
					</Box>
					<Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
						{filteredInstances.map((instance, index) => (
							<Box key={index}>
								<InstanceMetricsTable
									instance={instance}
									metricEntries={getMetricsForInstance(instance)}
									naturalMetricCompare={naturalMetricCompare}
									metaMetrics={monitorData?.metaMetrics}
								/>
							</Box>
						))}
						{filteredInstances.length === 0 && <Typography>No instances found.</Typography>}
					</Box>
				</Box>
			)}

			{tabValue === 1 && (
				<Box sx={{ flex: 1, overflow: "auto" }}>
					<Box sx={{ display: "flex", alignItems: "center", mb: 2, gap: 1 }}>
						<IconButton onClick={() => setIsSettingsOpen(true)}>
							<SettingsIcon />
						</IconButton>
						<Typography variant="h6">Configure Metrics</Typography>
					</Box>

					<Dialog open={isSettingsOpen} onClose={() => setIsSettingsOpen(false)}>
						<DialogTitle>Select Metrics</DialogTitle>
						<DialogContent dividers>
							<FormGroup>
								{availableMetrics.map((metric) => (
									<FormControlLabel
										key={metric}
										control={
											<Checkbox
												checked={selectedMetrics.includes(metric)}
												onChange={() => handleMetricToggle(metric)}
											/>
										}
										label={metric}
									/>
								))}
							</FormGroup>
						</DialogContent>
						<DialogActions>
							<Button onClick={() => setIsSettingsOpen(false)}>Close</Button>
						</DialogActions>
					</Dialog>

					<DashboardTable>
						<TableHead>
							<TableRow>
								<TableCell>
									<TableSortLabel
										active={sortConfig.key === "__instance_id__"}
										direction={sortConfig.key === "__instance_id__" ? sortConfig.direction : "asc"}
										onClick={() => handleRequestSort("__instance_id__")}
									>
										Instance Id
									</TableSortLabel>
								</TableCell>
								{selectedMetrics.map((metric) => {
									const meta = getMetricMetadata(metric, monitorData.metaMetrics);
									const cleanUnit = meta?.unit ? meta.unit.replace(/[{}]/g, "") : "";
									return (
										<TableCell key={metric}>
											<TableSortLabel
												active={sortConfig.key === metric}
												direction={sortConfig.key === metric ? sortConfig.direction : "asc"}
												onClick={() => handleRequestSort(metric)}
											>
												<HoverInfo
													title={metric}
													description={meta?.description}
													unit={cleanUnit}
													sx={{ display: "inline-block" }}
												>
													{metric}
												</HoverInfo>
											</TableSortLabel>
										</TableCell>
									);
								})}
							</TableRow>
						</TableHead>
						<TableBody>
							{sortedMetricsInstances.slice(0, 10).map((instance, index) => (
								<TableRow key={index}>
									<TableCell>
										<InstanceNameWithAttributes
											displayName={getInstanceDisplayName(instance)}
											attributes={instance.attributes}
										/>
									</TableCell>
									{selectedMetrics.map((metric) => {
										const val = instance.metrics?.[metric];
										const value = getMetricValue(val);
										const meta = getMetricMetadata(metric, monitorData.metaMetrics);
										return <MetricValueCell key={metric} value={value} unit={meta?.unit} />;
									})}
								</TableRow>
							))}
						</TableBody>
					</DashboardTable>
				</Box>
			)}
		</Box>
	);
};

export default MonitorTypeView;
