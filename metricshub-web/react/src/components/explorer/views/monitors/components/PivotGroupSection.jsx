import * as React from "react";
import { useDispatch, useSelector } from "react-redux";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import DashboardTable from "../../common/DashboardTable";
import HoverInfo from "./HoverInfo";
import { formatMetricValue } from "../../../../../utils/formatters";
import {
	getMetricMetadata,
	getBaseMetricKey,
	getMetricLabel,
	isUtilizationUnit,
} from "../../../../../utils/metrics-helper";
import {
	UtilizationStack,
	colorFor,
	colorLabelFromKey,
	buildUtilizationParts,
	getPriority,
} from "./utilization";
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
	const displayBaseName = getBaseMetricKey(group.baseName);

	const dispatch = useDispatch();
	const uiState = useSelector((state) =>
		resourceId ? selectResourceUiState(resourceId)(state) : null,
	);
	const open = uiState?.pivotGroups?.[group.baseName] || false;

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
	}, [group, isUtilizationGroup]);

	const renderHeader = () => {
		if (!isUtilizationGroup) {
			return (
				<TableHead>
					<TableRow>
						<TableCell sx={{ width: "25%" }}>Instance</TableCell>
						{group.metricKeys.map((key) => {
							const colLabel = getMetricLabel(key);
							const meta = getMetricMetadata(key, metaMetrics);
							const { description, unit } = meta;
							const cleanUnit = unit ? unit.replace(/[{}]/g, "") : "";

							return (
								<TableCell key={key} align="left">
									<HoverInfo
										title={colLabel}
										description={description}
										unit={cleanUnit}
										sx={{ display: "inline-block" }}
									>
										{colLabel}
										{cleanUnit && (
											<Box
												component="span"
												sx={{ color: "text.secondary", fontSize: "0.75em", ml: 0.5 }}
											>
												({cleanUnit})
											</Box>
										)}
									</HoverInfo>
								</TableCell>
							);
						})}
					</TableRow>
				</TableHead>
			);
		}

		const meta = getMetricMetadata(group.baseName, metaMetrics);
		let { description, unit } = meta;
		let displayUnit = unit === "1" ? "%" : unit ? unit.replace(/[{}]/g, "") : "";

		return (
			<TableHead>
				<TableRow>
					<TableCell sx={{ width: "25%" }}>Instance</TableCell>
					<TableCell>
						<HoverInfo
							title="Utilization"
							description={description}
							unit={displayUnit}
							sx={{ display: "inline-block" }}
						>
							Utilization
							{displayUnit && (
								<Box component="span" sx={{ color: "text.secondary", fontSize: "0.75em", ml: 0.5 }}>
									({displayUnit})
								</Box>
							)}
						</HoverInfo>
					</TableCell>
				</TableRow>
			</TableHead>
		);
	};

	return (
		<Box key={group.baseName} mb={2}>
			<Box
				sx={{
					display: "flex",
					alignItems: "center",
					justifyContent: "space-between",
					cursor: "pointer",
					mb: 0.5,
					px: 0.75,
					py: 0.25,
					borderRadius: 1,
					bgcolor: "action.hover",
					transition: "background-color 0.15s ease-in-out",
					"&:hover": {
						bgcolor: "action.selected",
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
					{open ? "Hide" : "Show"}
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
				<DashboardTable stickyHeader={false}>
					{renderHeader()}
					<TableBody>
						{isUtilizationGroup && sortedInstances.length > 0 && (
							<TableRow>
								<TableCell>
									<Typography variant="body2" sx={{ fontWeight: 500 }}>
										Average {displayBaseName}
									</Typography>
								</TableCell>
								<TableCell>
									<UtilizationStack
										parts={buildUtilizationParts(
											group.metricKeys.map((key) => {
												const sum = sortedInstances.reduce(
													(acc, inst) => acc + (inst.metrics?.[key] || 0),
													0,
												);
												const avg = sum / sortedInstances.length;
												return { key, value: avg };
											}),
										)}
									/>
								</TableCell>
							</TableRow>
						)}
						{sortedInstances.map((inst, rowIndex) => {
							const attrs = inst?.attributes ?? {};
							const id = attrs.id || inst.name;
							const displayName =
								attrs["system.device"] || attrs.name || attrs["network.interface.name"] || id;
							const metrics = inst?.metrics ?? {};

							if (isUtilizationGroup) {
								const entries = group.metricKeys.map((key) => ({ key, value: metrics[key] }));
								const parts = buildUtilizationParts(entries);
								return (
									<TableRow key={id || rowIndex}>
										<TableCell>{displayName}</TableCell>
										<TableCell>
											<UtilizationStack parts={parts} />
										</TableCell>
									</TableRow>
								);
							}

							return (
								<TableRow key={id || rowIndex}>
									<TableCell>{displayName}</TableCell>
									{group.metricKeys.map((key) => {
										const meta = getMetricMetadata(key, metaMetrics);
										const unit = meta?.unit;
										const cleanUnit = unit ? unit.replace(/[{}]/g, "") : "";
										const val = metrics[key];
										const formattedValue = formatMetricValue(val, unit);
										const rawValue = String(val);
										const showRaw =
											typeof val === "number" &&
											formattedValue !== rawValue &&
											formattedValue !== "";

										return (
											<TableCell key={key} align="left">
												{showRaw ? (
													<HoverInfo
														title={
															<Typography variant="body2">
																Raw value : {val} {cleanUnit}
															</Typography>
														}
														sx={{ display: "inline-block" }}
													>
														{formattedValue}
													</HoverInfo>
												) : (
													formattedValue
												)}
											</TableCell>
										);
									})}
								</TableRow>
							);
						})}
					</TableBody>
				</DashboardTable>
			)}
		</Box>
	);
};

export default PivotGroupSection;
