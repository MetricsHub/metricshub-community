import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow, Tooltip } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import DashboardTable from "../../common/DashboardTable";
import HoverInfo from "./HoverInfo";
import { formatMetricValue } from "../../../../../utils/formatters";
import {
	getMetricMetadata,
	getBaseMetricKey,
	isUtilizationUnit,
} from "../../../../../utils/metrics-helper";
import {
	UtilizationStack,
	buildUtilizationParts,
	colorFor,
	colorLabelFromKey,
	compareUtilizationParts,
} from "./Utilization";

/**
 * Displays a table of metrics for a single monitor instance.
 * Groups utilization metrics into a single row with a stacked progress bar.
 *
 * @param {{
 *   instance: any,
 *   metricEntries: Array<[string, any]>,
 *   naturalMetricCompare: (a: string, b: string) => number,
 *   metaMetrics?: Record<string, { unit?: string, description?: string, type?: string }>
 * }} props
 */
const InstanceMetricsTable = ({ instance, metricEntries, naturalMetricCompare, metaMetrics }) => {
	const attrs = instance?.attributes ?? {};
	const id = attrs.id || instance.name;
	const displayName = attrs["system.device"] || attrs.name || attrs["network.interface.name"] || id;

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
		<Box key={id} mb={1}>
			<Box display="flex" alignItems="center" gap={1} mb={1}>
				<Typography variant="subtitle1" sx={{ fontWeight: 500 }}>
					{displayName}
				</Typography>
				{Object.keys(attrs).length > 0 && (
					<Tooltip
						title={
							<Box>
								<Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 0.5 }}>
									Attributes
								</Typography>
								<Box component="ul" sx={{ m: 0, pl: 2, fontSize: "0.75rem", textAlign: "left" }}>
									{Object.entries(attrs).map(([k, v]) => (
										<li key={k}>
											<strong>{k}:</strong> {String(v)}
										</li>
									))}
								</Box>
							</Box>
						}
					>
						<InfoOutlinedIcon
							fontSize="small"
							color="action"
							sx={{ fontSize: 16, cursor: "help", opacity: 0.7 }}
						/>
					</Tooltip>
				)}
			</Box>

			<DashboardTable stickyHeader={false}>
				<TableHead>
					<TableRow>
						<TableCell sx={{ width: "25%" }}>Name</TableCell>
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

								// If unit is "1", render as a progress bar (utilization)
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

export default InstanceMetricsTable;
