import * as React from "react";
import { useDispatch, useSelector } from "react-redux";
import { Box, Typography, TableBody, TableCell, TableRow } from "@mui/material";
import DashboardTable from "../../common/DashboardTable";
import HoverInfo from "./HoverInfo";
import TruncatedText from "../../common/TruncatedText";
import PivotGroupHeader from "./PivotGroupHeader";
import InstanceNameWithAttributes from "./InstanceNameWithAttributes";
import MetricValueCell from "../../common/MetricValueCell";
import { truncatedCellSx } from "../../common/table-styles";

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
 * @param {{
 *   group: { baseName: string, metricKeys: string[] },
 *   sortedInstances: any[],
 *   resourceId: string,
 *   metaMetrics?: Record<string, { unit?: string, description?: string, type?: string }>
 * }} props
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
					transition: "background-color 0.15s ease-in-out",
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
					<DashboardTable stickyHeader={false}>
						<PivotGroupHeader
							group={group}
							isUtilizationGroup={isUtilizationGroup}
							metaMetrics={metaMetrics}
						/>
						<TableBody>
							{isUtilizationGroup && averageParts && (
								<TableRow>
									<TableCell sx={truncatedCellSx}>
										<Typography variant="body2" sx={{ fontWeight: 500 }}>
											Average {displayBaseName}
										</Typography>
									</TableCell>
									<TableCell>
										<UtilizationStack parts={averageParts} />
									</TableCell>
								</TableRow>
							)}
							{sortedInstances.map((inst, rowIndex) => {
								const attrs = inst?.attributes ?? {};
								const id = attrs.id || inst.name;
								const displayName = getInstanceDisplayName(inst, id);
								const metrics = inst?.metrics ?? {};

								if (isUtilizationGroup) {
									const entries = group.metricKeys.map((key) => ({ key, value: metrics[key] }));
									const hasData = entries.some((e) => e.value !== undefined && e.value !== null);
									const parts = hasData ? buildUtilizationParts(entries) : [];

									return (
										<TableRow key={id || rowIndex}>
											<TableCell sx={truncatedCellSx}>
												<Box sx={{ overflow: "hidden" }}>
													<InstanceNameWithAttributes
														displayName={displayName}
														attributes={attrs}
													/>
												</Box>
											</TableCell>
											<TableCell>{hasData ? <UtilizationStack parts={parts} /> : "-"}</TableCell>
										</TableRow>
									);
								}

								return (
									<TableRow key={id || rowIndex}>
										<TableCell sx={truncatedCellSx}>
											<Box sx={{ overflow: "hidden" }}>
												<InstanceNameWithAttributes displayName={displayName} attributes={attrs} />
											</Box>
										</TableCell>
										{group.metricKeys.map((key) => {
											const meta = getMetricMetadata(key, metaMetrics);
											const val = getMetricValue(metrics[key]);

											return (
												<MetricValueCell key={key} value={val} unit={meta?.unit} align="left" />
											);
										})}
									</TableRow>
								);
							})}
						</TableBody>
					</DashboardTable>
				</Box>
			)}
		</Box>
	);
};

export default React.memo(PivotGroupSection);
