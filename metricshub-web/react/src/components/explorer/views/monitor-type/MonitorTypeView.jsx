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
	compareMetricEntries,
	getInstanceDisplayName,
} from "../../../../utils/metrics-helper";
import { cleanUnit } from "../../../../utils/formatters";
import { useResourceFetcher } from "../../../../hooks/use-resource-fetcher";
import { useMonitorData } from "../../../../hooks/use-monitor-data";
import {
	useInstanceSorting,
	SORT_KEY_INSTANCE_ID,
	SORT_DIRECTION_ASC,
} from "../../../../hooks/use-instance-sorting";
import { useInstanceFilter } from "../../../../hooks/use-instance-filter";
import { useMetricSelection } from "../../../../hooks/use-metric-selection";

const TAB_INSTANCES = 0;
const TAB_METRICS = 1;

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
	const [tabValue, setTabValue] = React.useState(TAB_INSTANCES);
	const currentResource = useSelector(selectCurrentResource);
	const loading = useSelector(selectResourceLoading);
	const error = useSelector(selectResourceError);

	const decodedName = resourceName ? decodeURIComponent(resourceName) : null;
	const decodedMonitorType = monitorType ? decodeURIComponent(monitorType) : null;

	useResourceFetcher({ resourceName, resourceGroupName });

	const monitorData = useMonitorData(currentResource, decodedMonitorType);
	const instances = React.useMemo(() => monitorData?.monitor?.instances || [], [monitorData]);

	const { sortedInstances, sortedMetricsInstances, sortConfig, handleRequestSort } =
		useInstanceSorting(instances);

	const { searchTerm, setSearchTerm, filteredInstances } = useInstanceFilter(sortedInstances);

	const {
		selectedMetrics,
		handleMetricToggle,
		isSettingsOpen,
		setIsSettingsOpen,
		availableMetrics,
	} = useMetricSelection(sortedInstances);

	const handleTabChange = React.useCallback((event, newValue) => {
		setTabValue(newValue);
	}, []);

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

			{tabValue === TAB_INSTANCES && (
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
						{filteredInstances?.map((instance, index) => (
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

			{tabValue === TAB_METRICS && (
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
								{availableMetrics?.map((metric) => (
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

					<Box sx={{ width: "100%", overflowX: "auto" }}>
						<DashboardTable containerProps={{ sx: { minWidth: "max-content" } }}>
							<TableHead>
								<TableRow>
									<TableCell>
										<TableSortLabel
											active={sortConfig.key === SORT_KEY_INSTANCE_ID}
											direction={
												sortConfig.key === SORT_KEY_INSTANCE_ID
													? sortConfig.direction
													: SORT_DIRECTION_ASC
											}
											onClick={() => handleRequestSort(SORT_KEY_INSTANCE_ID)}
										>
											Instance Id
										</TableSortLabel>
									</TableCell>
									{selectedMetrics?.map((metric) => {
										const meta = getMetricMetadata(metric, monitorData.metaMetrics);
										const cleanedUnit = cleanUnit(meta?.unit);
										return (
											<TableCell key={metric}>
												<TableSortLabel
													active={sortConfig.key === metric}
													direction={
														sortConfig.key === metric ? sortConfig.direction : SORT_DIRECTION_ASC
													}
													onClick={() => handleRequestSort(metric)}
												>
													<HoverInfo
														title={metric}
														description={meta?.description}
														unit={cleanedUnit}
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
								{sortedMetricsInstances?.map((instance, index) => (
									<TableRow key={index}>
										<TableCell>
											<InstanceNameWithAttributes
												displayName={getInstanceDisplayName(instance)}
												attributes={instance.attributes}
											/>
										</TableCell>
										{selectedMetrics?.map((metric) => {
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
				</Box>
			)}
		</Box>
	);
};

export default MonitorTypeView;
