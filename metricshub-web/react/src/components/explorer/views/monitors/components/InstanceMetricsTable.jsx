import * as React from "react";
import { Box, Typography } from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import HoverInfo from "./HoverInfo";
import TruncatedText from "../../common/TruncatedText";
import MetricNameHighlighter from "../../common/MetricNameHighlighter.jsx";
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
	buildUtilizationParts,
	colorFor,
	colorLabelFromKey,
	compareUtilizationParts,
} from "./Utilization";
import { flashBlueAnimation } from "../../../../../utils/animations";

/**
 * Displays a table of metrics for a single monitor instance.
 * Groups utilization metrics into a single row with a stacked progress bar.
 *
 * @param {object} props - Component props
 * @param {any} props.instance - The monitor instance object
 * @param {Array<[string, any]>} [props.metricEntries] - Optional pre-sorted metric entries
 * @param {(a: [string, any], b: [string, any]) => number} props.naturalMetricCompare - Comparator for sorting metrics
 * @param {Record<string, { unit?: string, description?: string, type?: string }>} [props.metaMetrics] - Metadata for metrics
 * @param {boolean} [props.highlighted] - Whether the table should be highlighted
 */
const InstanceMetricsTable = ({
	instance,
	metricEntries,
	naturalMetricCompare,
	metaMetrics,
	highlighted,
}) => {
	const attrs = React.useMemo(() => instance?.attributes ?? {}, [instance?.attributes]);
	const displayName = React.useMemo(() => getInstanceDisplayName(instance), [instance]);

	const effectiveMetricEntries = React.useMemo(() => {
		if (metricEntries) return metricEntries;
		const metrics = instance?.metrics ?? {};
		return Object.entries(metrics).map(([k, v]) => [k, getMetricValue(v)]);
	}, [metricEntries, instance?.metrics]);

	const sortedEntries = React.useMemo(
		() =>
			effectiveMetricEntries.filter(([name]) => !name.startsWith("__")).sort(naturalMetricCompare),
		[effectiveMetricEntries, naturalMetricCompare],
	);

	// Group utilization metrics together
	const groupedEntries = React.useMemo(() => {
		const groups = [];
		let currentGroup = null;

		for (const [name, value] of sortedEntries) {
			const meta = getMetricMetadata(name, metaMetrics);
			const isUtilization = isUtilizationUnit(meta?.unit);

			if (isUtilization) {
				const baseName = getBaseMetricKey(name);

				if (currentGroup && currentGroup.baseName === baseName) {
					currentGroup.entries.push({ key: name, value });
				} else {
					currentGroup = {
						type: "utilization",
						baseName,
						entries: [{ key: name, value }],
					};
					groups.push(currentGroup);
				}
			} else {
				currentGroup = null;
				groups.push({ type: "single", key: name, value });
			}
		}
		return groups;
	}, [sortedEntries, metaMetrics]);

	const columns = React.useMemo(
		() => [
			{
				field: "name",
				headerName: "Name",
				flex: 1,
				renderCell: (params) => {
					const group = params.row;
					if (group.type === "single") {
						const meta = getMetricMetadata(group.key, metaMetrics);
						return (
							<Box
								sx={{
									display: "flex",
									alignItems: "center",
									flexWrap: "wrap",
									columnGap: 2,
									rowGap: 0.5,
									height: "100%",
								}}
							>
								<Box component="span" sx={{ maxWidth: "100%", overflow: "hidden" }}>
									<HoverInfo
										title={group.key}
										description={meta?.description}
										unit={meta?.unit}
										sx={{ display: "block", width: "fit-content", maxWidth: "100%" }}
									>
										<MetricNameHighlighter name={group.key} />
									</HoverInfo>
								</Box>
							</Box>
						);
					}
					const baseMeta = getMetricMetadata(group.baseName, metaMetrics);
					const parts = buildUtilizationParts(group.entries);
					const sortedParts = [...parts].sort(compareUtilizationParts);
					return (
						<Box
							sx={{
								display: "flex",
								alignItems: "center",
								flexWrap: "wrap",
								columnGap: 2,
								rowGap: 0.5,
								height: "100%",
							}}
						>
							<Box component="span" sx={{ maxWidth: "100%", overflow: "hidden" }}>
								<HoverInfo
									title={group.baseName}
									description={baseMeta?.description}
									unit={baseMeta?.unit}
									sx={{ display: "block", width: "fit-content", maxWidth: "100%" }}
								>
									<MetricNameHighlighter name={group.baseName} />
								</HoverInfo>
							</Box>
							<Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
								{sortedParts.map((p) => {
									const label = colorLabelFromKey(p.key);
									return (
										<Box
											key={p.key}
											sx={{
												display: "flex",
												alignItems: "center",
												gap: 0.5,
												fontSize: 10,
											}}
										>
											<Box
												sx={{
													width: 8,
													height: 8,
													borderRadius: 0.5,
													bgcolor: colorFor(label),
												}}
											/>
											<Box component="span">{label}</Box>
										</Box>
									);
								})}
							</Box>
						</Box>
					);
				},
			},
			{
				field: "value",
				headerName: "Value",
				flex: 1,
				align: "left",
				headerAlign: "left",
				renderCell: (params) => {
					const group = params.row;
					if (group.type === "single") {
						const meta = getMetricMetadata(group.key, metaMetrics);
						const { unit } = meta;
						if (isUtilizationUnit(unit)) {
							const parts = [{ key: group.key, value: group.value, pct: group.value * 100 }];
							return <UtilizationStack parts={parts} />;
						}
						return <MetricValueCell value={group.value} unit={unit} align="left" />;
					}
					const parts = buildUtilizationParts(group.entries);
					const sortedParts = [...parts].sort(compareUtilizationParts);
					return <UtilizationStack parts={sortedParts} />;
				},
			},
		],
		[metaMetrics],
	);

	const rows = React.useMemo(
		() =>
			groupedEntries.map((group, index) => ({
				id: group.key || group.baseName || index,
				...group,
			})),
		[groupedEntries],
	);

	return (
		<Box mb={1}>
			<Box
				mb={1}
				sx={{
					p: 0.5,
					...(highlighted && { ...flashBlueAnimation, borderRadius: 1 }),
				}}
			>
				<InstanceNameWithAttributes
					displayName={displayName}
					attributes={attrs}
					variant="subtitle1"
				/>
			</Box>

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
	);
};

export default React.memo(InstanceMetricsTable);
