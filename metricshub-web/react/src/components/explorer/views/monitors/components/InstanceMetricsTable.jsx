import * as React from "react";
import { Box, TableBody, TableCell, TableHead, TableRow, Typography } from "@mui/material";
import DashboardTable from "../../common/DashboardTable";
import HoverInfo from "./HoverInfo";
import InstanceNameWithAttributes from "./InstanceNameWithAttributes";
import { formatMetricValue } from "../../../../../utils/formatters";
import {
	getMetricMetadata,
	getBaseMetricKey,
	isUtilizationUnit,
	getInstanceDisplayName,
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
 * @param {{
 *   instance: any,
 *   metricEntries: Array<[string, any]>,
 *   naturalMetricCompare: (a: [string, any], b: [string, any]) => number,
 *   metaMetrics?: Record<string, { unit?: string, description?: string, type?: string }>,
 *   highlighted?: boolean
 * }} props
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

	const sortedEntries = React.useMemo(
		() => metricEntries.filter(([name]) => !name.startsWith("__")).sort(naturalMetricCompare),
		[metricEntries, naturalMetricCompare],
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

			<DashboardTable stickyHeader={false}>
				<TableHead>
					<TableRow>
						<TableCell sx={{ width: "50%", minWidth: 300 }}>Name</TableCell>
						<TableCell align="left">Value</TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{groupedEntries.length === 0 ? (
						<TableRow>
							<TableCell colSpan={2}>No metrics</TableCell>
						</TableRow>
					) : (
						groupedEntries.map((group) => {
							if (group.type === "single") {
								const meta = getMetricMetadata(group.key, metaMetrics);
								const { description, unit } = meta;
								const cleanUnit = unit ? unit.replace(/[{}]/g, "") : "";

								// If unit is "1", render as a progress bar
								if (isUtilizationUnit(unit)) {
									const parts = [{ key: group.key, value: group.value, pct: group.value * 100 }];
									return (
										<TableRow key={group.key}>
											<TableCell>
												<HoverInfo
													title={group.key}
													description={description}
													unit={cleanUnit}
													sx={{ display: "inline-block" }}
												>
													{group.key}
												</HoverInfo>
											</TableCell>
											<TableCell align="left">
												<UtilizationStack parts={parts} />
											</TableCell>
										</TableRow>
									);
								}

								const formattedValue = formatMetricValue(group.value, unit);
								const rawValue = String(group.value);
								const showRaw =
									typeof group.value === "number" &&
									formattedValue !== rawValue &&
									formattedValue !== "";

								return (
									<TableRow key={group.key}>
										<TableCell>
											<HoverInfo
												title={group.key}
												description={description}
												unit={cleanUnit}
												sx={{ display: "inline-block" }}
											>
												{group.key}
											</HoverInfo>
										</TableCell>
										<TableCell align="left">
											{showRaw ? (
												<HoverInfo
													title={
														<Typography variant="body2">
															Raw value : {group.value} {cleanUnit}
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
									</TableRow>
								);
							}

							const parts = buildUtilizationParts(group.entries);
							const sortedParts = [...parts].sort(compareUtilizationParts);
							const meta = getMetricMetadata(group.baseName, metaMetrics);
							const { description, unit } = meta;
							const cleanUnit = unit ? unit.replace(/[{}]/g, "") : "";

							return (
								<TableRow key={group.baseName}>
									<TableCell>
										<Box
											sx={{
												display: "flex",
												alignItems: "center",
												flexWrap: "wrap",
												columnGap: 2,
												rowGap: 0.5,
											}}
										>
											<Box component="span">
												<HoverInfo
													title={group.baseName}
													description={description}
													unit={cleanUnit}
													sx={{ display: "inline-block" }}
												>
													{group.baseName}
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
									</TableCell>
									<TableCell align="left">
										<UtilizationStack parts={sortedParts} />
									</TableCell>
								</TableRow>
							);
						})
					)}
				</TableBody>
			</DashboardTable>
		</Box>
	);
};

export default React.memo(InstanceMetricsTable);
