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
	Dialog,
	DialogTitle,
	DialogContent,
	DialogActions,
	Button,
	FormGroup,
	FormControlLabel,
	Checkbox,
	Pagination,
} from "@mui/material";
import SettingsIcon from "@mui/icons-material/Settings";
import { DataGrid } from "@mui/x-data-grid";
import {
	selectCurrentResource,
	selectResourceLoading,
	selectResourceError,
} from "../../../../store/slices/explorer-slice";
import EntityHeader from "../common/EntityHeader";
import InstanceMetricsTable from "../monitors/components/InstanceMetricsTable";
import InstanceNameWithAttributes from "../monitors/components/InstanceNameWithAttributes";
import MonitorTypeIcon from "../monitors/icons/MonitorTypeIcon";
import MetricValueCell from "../common/MetricValueCell";
import { renderMetricHeader } from "../common/metric-column-helper";
import {
	getMetricValue,
	getMetricMetadata,
	compareMetricEntries,
	getInstanceDisplayName,
} from "../../../../utils/metrics-helper";
import { cleanUnit } from "../../../../utils/formatters";
import { useResourceFetcher } from "../../../../hooks/use-resource-fetcher";
import { useScrollToHash } from "../../../../hooks/use-scroll-to-hash";
import { useMonitorData } from "../../../../hooks/use-monitor-data";
import { useInstanceSorting } from "../../../../hooks/use-instance-sorting";
import { useInstanceFilter } from "../../../../hooks/use-instance-filter";
import { useMetricSelection } from "../../../../hooks/use-metric-selection";
import { dataGridSx } from "../common/table-styles";
import { useDataGridColumnWidths } from "../common/use-data-grid-column-widths";

const TAB_INSTANCES = 0;
const TAB_METRICS = 1;

/**
 * Monitor Type View component.
 * Displays instances and metrics for a specific monitor type.
 *
 * @param {object} props - Component props
 * @param {string} props.resourceName - The name of the resource
 * @param {string} props.resourceGroupName - The name of the resource group
 * @param {string} props.connectorId - The ID of the connector
 * @param {string} props.monitorType - The type of the monitor
 * @returns {JSX.Element} The rendered component.
 */
const MonitorTypeView = ({ resourceName, resourceGroupName, connectorId, monitorType }) => {
	const [tabValue, setTabValue] = React.useState(TAB_INSTANCES);
	const currentResource = useSelector(selectCurrentResource);
	const loading = useSelector(selectResourceLoading);
	const error = useSelector(selectResourceError);

	const decodedName = resourceName ? decodeURIComponent(resourceName) : null;
	const decodedConnectorId = connectorId ? decodeURIComponent(connectorId) : null;
	const decodedMonitorType = monitorType ? decodeURIComponent(monitorType) : null;
	const monitorTypeStorageKeyBase = React.useMemo(() => {
		const groupKey = resourceGroupName || "unknown";
		const resourceKey = decodedName || "unknown";
		const connectorKey = decodedConnectorId || "unknown";
		const monitorKey = decodedMonitorType || "unknown";
		return `explorer.monitorType.${groupKey}.${resourceKey}.${connectorKey}.${monitorKey}`;
	}, [resourceGroupName, decodedName, decodedConnectorId, decodedMonitorType]);

	useResourceFetcher({ resourceName, resourceGroupName });

	const monitorData = useMonitorData(currentResource, decodedConnectorId, decodedMonitorType);
	const instances = React.useMemo(() => monitorData?.monitor?.instances || [], [monitorData]);

	const { sortedInstances, sortedMetricsInstances } = useInstanceSorting(instances);

	const { searchTerm, setSearchTerm, filteredInstances } = useInstanceFilter(sortedInstances);

	const [page, setPage] = React.useState(1);
	const pageSize = 5;

	React.useEffect(() => {
		setPage(1);
	}, [searchTerm]);

	const paginatedInstances = React.useMemo(() => {
		const startIndex = (page - 1) * pageSize;
		return filteredInstances.slice(startIndex, startIndex + pageSize);
	}, [filteredInstances, page, pageSize]);

	const handlePageChange = React.useCallback((event, value) => {
		setPage(value);
	}, []);

	const highlightedId = useScrollToHash();
	const lastHighlightedIdRef = React.useRef(null);

	React.useEffect(() => {
		if (highlightedId && highlightedId !== lastHighlightedIdRef.current) {
			const index = filteredInstances.findIndex((ins) => ins.name === highlightedId);
			if (index !== -1) {
				const targetPage = Math.floor(index / pageSize) + 1;
				setPage(targetPage);
				lastHighlightedIdRef.current = highlightedId;
			}
		} else if (!highlightedId) {
			lastHighlightedIdRef.current = null;
		}
	}, [highlightedId, filteredInstances, pageSize]);

	const {
		selectedMetrics,
		setSelectedMetrics,
		handleMetricToggle,
		isSettingsOpen,
		setIsSettingsOpen,
		availableMetrics,
	} = useMetricSelection(sortedInstances);

	const handleTabChange = React.useCallback((event, newValue) => {
		setTabValue(newValue);
	}, []);

	// Stable reference for metaMetrics to prevent column re-creation
	const metaMetrics = monitorData?.metaMetrics || {};
	const stableMetaMetrics = React.useMemo(
		() => metaMetrics,
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[JSON.stringify(metaMetrics)],
	);

	const columns = React.useMemo(() => {
		const cols = [
			{
				field: "instanceName",
				headerName: "Instance Name",
				flex: 1,
				renderCell: (params) => (
					<InstanceNameWithAttributes
						displayName={getInstanceDisplayName(params.row)}
						attributes={params.row.attributes}
					/>
				),
				valueGetter: (value, row) => getInstanceDisplayName(row),
			},
		];

		selectedMetrics.forEach((metric) => {
			const meta = getMetricMetadata(metric, stableMetaMetrics);
			const cleanedUnit = cleanUnit(meta?.unit);
			const displayUnit = cleanedUnit === "1" ? "%" : cleanedUnit;

			cols.push({
				field: metric,
				headerName: metric.toLowerCase(),
				headerClassName: "metric-header",
				flex: 1,
				align: "left",
				headerAlign: "left",
				renderHeader: () => renderMetricHeader(metric, meta, displayUnit),
				renderCell: (params) => {
					const val = params.row.metrics?.[metric];
					const value = getMetricValue(val);

					return <MetricValueCell value={value} unit={meta?.unit} />;
				},
				valueGetter: (value, row) => {
					const val = row.metrics?.[metric];
					return getMetricValue(val);
				},
			});
		});

		return cols;
	}, [selectedMetrics, stableMetaMetrics]);

	const {
		columns: columnsWithWidths,
		onColumnWidthChange: handleColumnWidthChange,
	} = useDataGridColumnWidths(columns, {
		storageKey: `${monitorTypeStorageKeyBase}.metrics`,
	});

	const instanceTableStorageKeyPrefix = React.useMemo(() => {
		return `${monitorTypeStorageKeyBase}.instances`;
	}, [monitorTypeStorageKeyBase]);

	const rows = React.useMemo(() => {
		return sortedMetricsInstances.map((instance, index) => ({
			id: instance.name || index,
			...instance,
		}));
	}, [sortedMetricsInstances]);

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
		<Box sx={{ p: 3, display: "flex", flexDirection: "column" }}>
			<EntityHeader
				title={`${decodedName} : ${decodedMonitorType} (${monitorData.connectorName})`}
				icon={<MonitorTypeIcon type={decodedMonitorType} fontSize="large" />}
			/>

			<Box sx={{ borderBottom: 1, borderColor: "divider", mb: 2 }}>
				<Tabs value={tabValue} onChange={handleTabChange}>
					<Tab label={`${decodedMonitorType} instances`} />
					<Tab label={`${decodedMonitorType} Metrics`} />
				</Tabs>
			</Box>

			{tabValue === TAB_INSTANCES && (
				<Box>
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
						{paginatedInstances.map((instance, index) => {
							const isHighlighted = highlightedId === instance.name;
							return (
								<Box key={index} id={instance.name}>
									<InstanceMetricsTable
										instance={instance}
										naturalMetricCompare={compareMetricEntries}
										metaMetrics={monitorData?.metaMetrics}
										highlighted={isHighlighted}
										storageKeyPrefix={instanceTableStorageKeyPrefix}
									/>
								</Box>
							);
						})}
						{filteredInstances.length === 0 && <Typography>No instances found.</Typography>}
						{filteredInstances.length > pageSize && (
							<Box sx={{ display: "flex", justifyContent: "center", mt: 2, mb: 2 }}>
								<Pagination
									count={Math.ceil(filteredInstances.length / pageSize)}
									page={page}
									onChange={handlePageChange}
									color="primary"
								/>
							</Box>
						)}
					</Box>
				</Box>
			)}

			{tabValue === TAB_METRICS && (
				<Box sx={{ display: "flex", flexDirection: "column" }}>
					<Box sx={{ display: "flex", alignItems: "center", mb: 2, gap: 1, flexShrink: 0 }}>
						<IconButton onClick={() => setIsSettingsOpen(true)}>
							<SettingsIcon />
						</IconButton>
						<Typography variant="h6">Configure Metrics</Typography>
					</Box>

					<Dialog open={isSettingsOpen} onClose={() => setIsSettingsOpen(false)}>
						<DialogTitle>Select Metrics</DialogTitle>
						<DialogContent dividers>
							<FormGroup>
								<FormControlLabel
									control={
										<Checkbox
											checked={
												availableMetrics?.length > 0 &&
												selectedMetrics.length === availableMetrics.length
											}
											indeterminate={
												selectedMetrics.length > 0 &&
												selectedMetrics.length < availableMetrics?.length
											}
											onChange={(e) => {
												if (e.target.checked) {
													setSelectedMetrics([...availableMetrics]);
												} else {
													setSelectedMetrics([]);
												}
											}}
										/>
									}
									label="Select All"
								/>
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

					<Box sx={{ width: "100%" }}>
						<DataGrid
							rows={rows}
							columns={columnsWithWidths}
							disableRowSelectionOnClick
							autoHeight
							pageSizeOptions={[5, 10, 20, 50, 100]}
							onColumnWidthChange={handleColumnWidthChange}
							initialState={{
								pagination: {
									paginationModel: { pageSize: 10, page: 0 },
								},
							}}
							sx={dataGridSx}
						/>
					</Box>
				</Box>
			)}
		</Box>
	);
};

export default React.memo(MonitorTypeView);
