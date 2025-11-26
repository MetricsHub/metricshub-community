import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import DashboardTable from "../../common/DashboardTable";
import {
	renderMetricValue,
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
 *   naturalMetricCompare: (a: string, b: string) => number
 * }} props
 */
const InstanceMetricsTable = ({ instance, metricEntries, naturalMetricCompare }) => {
	const attrs = instance?.attributes ?? {};
	const id = attrs.id || instance.name;
	const displayName = attrs["system.device"] || attrs.name || attrs["network.interface.name"] || id;
	const extraInfoParts = [];
	if (attrs.name && attrs.name !== displayName) extraInfoParts.push(`name: ${attrs.name}`);
	if (attrs.serial_number) extraInfoParts.push(`serial_number: ${attrs.serial_number}`);
	if (attrs.vendor) extraInfoParts.push(`vendor: ${attrs.vendor}`);
	if (attrs.info) extraInfoParts.push(`info: ${attrs.info}`);

	const sortedEntries = React.useMemo(
		() => metricEntries.filter(([name]) => !name.startsWith("__")).sort(naturalMetricCompare),
		[metricEntries, naturalMetricCompare],
	);

	// Group utilization metrics together
	const groupedEntries = React.useMemo(() => {
		const groups = [];
		let currentGroup = null;

		for (const [name, value] of sortedEntries) {
			if (name.includes(".utilization")) {
				const baseName = name.substring(0, name.indexOf(".utilization") + 12);

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
	}, [sortedEntries]);

	return (
		<Box key={id} mb={3}>
			<Typography variant="subtitle1" sx={{ fontWeight: 500, mb: 1 }}>
				{displayName}
				{extraInfoParts.length > 0 && ` (${extraInfoParts.join("; ")})`}
			</Typography>

			<DashboardTable stickyHeader={false}>
				<TableHead>
					<TableRow>
						<TableCell>Name</TableCell>
						<TableCell>Value</TableCell>
						<TableCell>Unit</TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{groupedEntries.length === 0 ? (
						<TableRow>
							<TableCell colSpan={3}>No metrics</TableCell>
						</TableRow>
					) : (
						groupedEntries.map((group, index) => {
							if (group.type === "single") {
								return (
									<TableRow key={group.key}>
										<TableCell>{group.key}</TableCell>
										<TableCell>{renderMetricValue(group.key, group.value)}</TableCell>
										<TableCell></TableCell>
									</TableRow>
								);
							}

							const parts = buildUtilizationParts(group.entries);
							const sortedParts = [...parts].sort(compareUtilizationParts);

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
											<Box component="span">{group.baseName}</Box>
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
									<TableCell>
										<UtilizationStack parts={sortedParts} />
									</TableCell>
									<TableCell></TableCell>
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
