import * as React from "react";
import { useDispatch, useSelector } from "react-redux";
import {
	Box,
	Typography,
	Tabs,
	Tab,
	TextField,
	CircularProgress,
	IconButton,
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableRow,
	Paper,
	TableContainer,
	Dialog,
	DialogTitle,
	DialogContent,
	DialogActions,
	Button,
	FormGroup,
	FormControlLabel,
	Checkbox,
} from "@mui/material";
import SettingsIcon from "@mui/icons-material/Settings";
import {
	selectCurrentResource,
	selectResourceLoading,
	selectResourceError,
} from "../../../../store/slices/explorer-slice";
import {
	fetchTopLevelResource,
	fetchGroupedResource,
} from "../../../../store/thunks/explorer-thunks";
import EntityHeader from "../common/EntityHeader";
import InstanceMetricsTable from "../monitors/components/InstanceMetricsTable";
import DashboardTable from "../common/DashboardTable";
import HoverInfo from "../monitors/components/HoverInfo";
import {
	getMetricValue,
	getMetricMetadata,
	compareMetricNames,
} from "../../../../utils/metrics-helper";

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
	const dispatch = useDispatch();
	const [tabValue, setTabValue] = React.useState(0);
	const [searchTerm, setSearchTerm] = React.useState("");
	const [selectedMetrics, setSelectedMetrics] = React.useState([]);
	const [isSettingsOpen, setIsSettingsOpen] = React.useState(false);
	const currentResource = useSelector(selectCurrentResource);
	const loading = useSelector(selectResourceLoading);
	const error = useSelector(selectResourceError);

	const decodedName = resourceName ? decodeURIComponent(resourceName) : null;
	const decodedGroup = resourceGroupName ? decodeURIComponent(resourceGroupName) : null;
	const decodedMonitorType = monitorType ? decodeURIComponent(monitorType) : null;

	React.useEffect(() => {
		if (!decodedName) return;

		const fetchData = () => {
			if (decodedGroup) {
				dispatch(fetchGroupedResource({ groupName: decodedGroup, resourceName: decodedName }));
			} else {
				dispatch(fetchTopLevelResource({ resourceName: decodedName }));
			}
		};

		fetchData();
		const interval = setInterval(fetchData, 10000);
		return () => clearInterval(interval);
	}, [dispatch, decodedName, decodedGroup]);

	const handleTabChange = React.useCallback((event, newValue) => {
		setTabValue(newValue);
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
					};
				}
			}
		}
		return null;
	}, [currentResource, decodedMonitorType]);

	const instances = React.useMemo(() => monitorData?.monitor?.instances || [], [monitorData]);

	const sortedInstances = React.useMemo(() => {
		return [...instances].sort((a, b) => {
			const nameA = a.attributes?.id || a.name || "";
			const nameB = b.attributes?.id || b.name || "";
			return compareMetricNames(nameA, nameB);
		});
	}, [instances]);

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
			const id = instance.attributes?.id || instance.name || "";
			const name = instance.attributes?.name || "";
			return (
				id.toLowerCase().includes(searchTerm.toLowerCase()) ||
				name.toLowerCase().includes(searchTerm.toLowerCase())
			);
		});
	}, [sortedInstances, searchTerm]);

	// Helper to get metrics for an instance
	const getMetricsForInstance = React.useCallback((instance) => {
		return Object.entries(instance.metrics || {}).map(([key, value]) => [
			key,
			getMetricValue(value),
		]);
	}, []);

	const naturalMetricCompare = React.useCallback((a, b) => compareMetricNames(a[0], b[0]), []);

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
			<EntityHeader title={decodedMonitorType} subtitle={`Resource: ${decodedName}`} />

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
								<Typography variant="h6" gutterBottom>
									{instance.attributes?.id || instance.name}
								</Typography>
								<Box sx={{ mb: 2 }}>
									{Object.entries(instance.attributes || {}).map(([k, v]) => (
										<Typography key={k} variant="body2" color="text.secondary">
											{k}: {v}
										</Typography>
									))}
								</Box>
								<InstanceMetricsTable
									instance={instance}
									metricEntries={getMetricsForInstance(instance)}
									naturalMetricCompare={naturalMetricCompare}
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
								<TableCell>Instance Id</TableCell>
								{selectedMetrics.map((metric) => {
									const meta = getMetricMetadata(metric, monitorData.metaMetrics);
									const cleanUnit = meta?.unit ? meta.unit.replace(/[{}]/g, "") : "";
									return (
										<TableCell key={metric}>
											<HoverInfo
												title={metric}
												description={meta?.description}
												unit={cleanUnit}
												sx={{ display: "inline-block" }}
											>
												{metric}
											</HoverInfo>
										</TableCell>
									);
								})}
							</TableRow>
						</TableHead>
						<TableBody>
							{sortedInstances.slice(0, 10).map((instance, index) => (
								<TableRow key={index}>
									<TableCell>{instance.attributes?.id || instance.name}</TableCell>
									{selectedMetrics.map((metric) => {
										const val = instance.metrics?.[metric];
										return <TableCell key={metric}>{val ? getMetricValue(val) : "-"}</TableCell>;
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
