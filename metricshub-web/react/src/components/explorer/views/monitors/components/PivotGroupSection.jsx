import * as React from "react";
import { useDispatch, useSelector } from "react-redux";
import { Box, Typography } from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import TruncatedText from "../../common/TruncatedText";
import { renderMetricHeader } from "../../common/metric-column-helper";
import InstanceNameWithAttributes from "./InstanceNameWithAttributes";
import MetricValueCell from "../../common/MetricValueCell";
import { dataGridSx } from "../../common/table-styles";

import {
	getMetricMetadata,
	getBaseMetricKey,
	isUtilizationUnit,
	getInstanceDisplayName,
	getMetricValue,
} from "../../../../../utils/metrics-helper";
import {
	UtilizationStack,
	colorFor,
	colorLabelFromKey,
	buildUtilizationParts,
	getPriority,
} from "./Utilization";
import {
	selectResourceUiState,
	setPivotGroupExpanded,
} from "../../../../../store/slices/explorer-slice";

/**
 * Renders a pivoted table for a group of metrics (e.g. all cpu.utilization metrics).
 * Allows expanding/collapsing the table.
 *
 * @param {object} props - Component props
 * @param {object} props.group - The metric group to render
 * @param {string} props.group.baseName - The base name of the metric group
 * @param {string[]} props.group.metricKeys - The keys of the metrics in the group
 * @param {any[]} props.sortedInstances - Sorted list of monitor instances
 * @param {string} props.resourceId - The ID of the resource
 * @param {Record<string, { unit?: string, description?: string, type?: string }>} [props.metaMetrics] - Metadata for metrics
 */
const PivotGroupSection = ({ group, sortedInstances, resourceId, metaMetrics }) => {
	const displayBaseName = React.useMemo(() => getBaseMetricKey(group.baseName), [group.baseName]);

	const dispatch = useDispatch();
	const uiState = useSelector((state) =>
		resourceId ? selectResourceUiState(resourceId)(state) : null,
	);
	const open = React.useMemo(
		() => uiState?.pivotGroups?.[group.baseName] || false,
		[uiState?.pivotGroups, group.baseName],
	);

	// Handler for toggling the pivot group expanded state
	const handleToggle = React.useCallback(() => {
		if (resourceId) {
			dispatch(
				setPivotGroupExpanded({
					resourceId,
					groupKey: group.baseName,
					expanded: !open,
				}),
			);
		}
	}, [dispatch, resourceId, group.baseName, open]);

	// Check if this group should be rendered as utilization bars.
	const isUtilizationGroup = React.useMemo(() => {
		if (metaMetrics) {
			return group.metricKeys.some((key) => {
				const meta = getMetricMetadata(key, metaMetrics);
				return isUtilizationUnit(meta?.unit);
			});
		}
		return false;
	}, [group.metricKeys, metaMetrics]);

	// Build the legend items for the utilization group
	const legendItems = React.useMemo(() => {
		if (!isUtilizationGroup) return [];
		const seen = new Set();
		const items = [];

		group.metricKeys.forEach((key) => {
			const label = colorLabelFromKey(key);
			if (seen.has(label)) return;
			seen.add(label);
			items.push(label);
		});

		return items.sort((a, b) => getPriority(a) - getPriority(b));
	}, [group.metricKeys, isUtilizationGroup]);

	// Calculate average utilization parts when group is open and utilization type
	const averageParts = React.useMemo(() => {
		if (!open || !isUtilizationGroup || sortedInstances.length === 0) return null;

		// Count how many instances actually have data for any of the keys in this group
		const instancesWithDataCount = sortedInstances.filter((inst) =>
			group.metricKeys.some(
				(key) => inst.metrics?.[key] !== undefined && inst.metrics?.[key] !== null,
			),
		).length;

		// If only 1 (or 0) instance has data, showing an average is redundant/misleading
		if (instancesWithDataCount <= 1) return null;

		return buildUtilizationParts(
			group.metricKeys.map((key) => {
				const sum = sortedInstances.reduce((acc, inst) => acc + (inst.metrics?.[key] || 0), 0);
				const avg = sum / sortedInstances.length;
				return { key, value: avg };
			}),
		);
	}, [open, isUtilizationGroup, sortedInstances, group.metricKeys]);

	const columns = React.useMemo(() => {
		const cols = [
			{
				field: "instanceName",
				headerName: "Instance Name",
				flex: 1,
				renderCell: (params) => {
					if (params.row.isAverage) {
						return (
							<Typography variant="body2" sx={{ fontWeight: 500 }}>
								Average {displayBaseName}
							</Typography>
						);
					}
					return (
						<InstanceNameWithAttributes
							displayName={getInstanceDisplayName(params.row)}
							attributes={params.row.attributes}
						/>
					);
				},
				valueGetter: (value, row) => {
					if (row.isAverage) return `Average ${displayBaseName}`;
					return getInstanceDisplayName(row);
				},
			},
		];

		if (isUtilizationGroup) {
			cols.push({
				field: "utilization",
				headerName: "Value",
				flex: 1,
				renderCell: (params) => {
					if (params.row.isAverage) {
						return <UtilizationStack parts={params.row.averageParts} />;
					}
					const metrics = params.row.metrics ?? {};
					const entries = group.metricKeys.map((key) => ({ key, value: metrics[key] }));
					const hasData = entries.some((e) => e.value !== undefined && e.value !== null);
					const parts = hasData ? buildUtilizationParts(entries) : [];
					return hasData ? <UtilizationStack parts={parts} /> : "-";
				},
			});
		} else {
			group.metricKeys.forEach((key) => {
				const meta = getMetricMetadata(key, metaMetrics);
				cols.push({
					field: key,
					headerName: key.toLowerCase(),
					headerClassName: "metric-header",
					flex: 1,
					align: "left",
					headerAlign: "left",
					renderHeader: () => renderMetricHeader(key, meta),
					renderCell: (params) => {
						if (params.row.isAverage) return null;
						const val = getMetricValue(params.row.metrics?.[key]);
						return <MetricValueCell value={val} unit={meta?.unit} align="left" />;
					},
					valueGetter: (value, row) => {
						if (row.isAverage) return null;
						return getMetricValue(row.metrics?.[key]);
					},
				});
			});
		}

		return cols;
	}, [isUtilizationGroup, displayBaseName, group.metricKeys, metaMetrics]);

	const rows = React.useMemo(() => {
		const r = sortedInstances.map((inst, index) => ({
			id: inst.name || index,
			...inst,
		}));

		if (isUtilizationGroup && averageParts) {
			r.unshift({
				id: "average-row",
				isAverage: true,
				averageParts,
			});
		}
		return r;
	}, [sortedInstances, isUtilizationGroup, averageParts]);

	return (
		<Box>
			<Box
				sx={{
					display: "flex",
					alignItems: "center",
					justifyContent: "space-between",
					cursor: "pointer",
					px: 0.75,
					py: 0.5,
					bgcolor: "transparent",
					transition: "background-color 0.4s ease",
					"&:hover": {
						bgcolor: "action.hover",
					},
				}}
				onClick={handleToggle}
			>
				<Box sx={{ display: "flex", alignItems: "center", columnGap: 1, flexWrap: "wrap" }}>
					<Typography
						variant="subtitle2"
						sx={{
							fontWeight: 500,
							display: "flex",
							alignItems: "center",
							columnGap: 1,
						}}
					>
						{displayBaseName}
					</Typography>

					{isUtilizationGroup && legendItems.length > 0 && (
						<Box
							sx={{
								display: "flex",
								flexWrap: "wrap",
								alignItems: "center",
								gap: 0.5,
							}}
						>
							{legendItems.map((label) => (
								<Box
									key={label}
									sx={{ display: "flex", alignItems: "center", gap: 0.5, fontSize: 10 }}
								>
									<Box
										sx={{
											width: 10,
											height: 10,
											borderRadius: 0.5,
											bgcolor: colorFor(label),
											transition: "background-color 0.4s ease",
										}}
									/>
									<Box component="span">{label}</Box>
								</Box>
							))}
						</Box>
					)}
				</Box>

				<Typography
					variant="caption"
					color="text.secondary"
					sx={{ display: "flex", alignItems: "center", columnGap: 0.5 }}
				>
					<Box
						component="span"
						sx={{
							display: "inline-block",
							transform: open ? "rotate(90deg)" : "rotate(0deg)",
							transition: "transform 0.15s ease-in-out",
							fontSize: 14,
						}}
					>
						▶
					</Box>
				</Typography>
			</Box>

			{open && (
				<Box sx={{ mt: 1, mb: 2 }}>
					<DataGrid
						rows={rows}
						columns={columns}
						disableRowSelectionOnClick
						hideFooter
						autoHeight
						density="compact"
						sx={{
							...dataGridSx,
							"& .MuiDataGrid-cell": {
								alignItems: "center",
								display: "flex",
								...dataGridSx["& .MuiDataGrid-cell"],
							},
						}}
					/>
				</Box>
			)}
		</Box>
	);
};

export default React.memo(PivotGroupSection);
