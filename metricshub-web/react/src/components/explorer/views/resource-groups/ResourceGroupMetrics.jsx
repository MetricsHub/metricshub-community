import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import DashboardTable from "../common/DashboardTable";
import { emptyStateCellSx, sectionTitleSx } from "../common/table-styles";

/**
 * Render table rows for a list of resource group metrics.
 *
 * @param {Array<{ key: string, value: unknown }> | undefined | null} metrics
 * @returns {JSX.Element | JSX.Element[]}
 */
const renderMetricsRows = (metrics) => {
	const list = Array.isArray(metrics) ? metrics : [];
	if (list.length === 0) {
		return (
			<TableRow>
				<TableCell colSpan={2} sx={emptyStateCellSx}>
					No metrics
				</TableCell>
			</TableRow>
		);
	}

	return list.map((m) => (
		<TableRow key={m.key}>
			<TableCell>{m.key}</TableCell>
			<TableCell>{m.value}</TableCell>
		</TableRow>
	));
};

/**
 * Metrics section for a single resource group.
 *
 * @param {{ metrics?: Array<{ key: string, value: unknown }> | null }} props
 * @returns {JSX.Element}
 */
const ResourceGroupMetrics = ({ metrics }) => {
	const rows = React.useMemo(() => (Array.isArray(metrics) ? metrics : []), [metrics]);

	if (rows.length === 0) {
		return null;
	}

	return (
		<Box>
			<Typography variant="h6" gutterBottom sx={sectionTitleSx}>
				Metrics
			</Typography>
			<DashboardTable>
				<TableHead>
					<TableRow>
						<TableCell>Key</TableCell>
						<TableCell>Value</TableCell>
					</TableRow>
				</TableHead>
				<TableBody>{renderMetricsRows(rows)}</TableBody>
			</DashboardTable>
		</Box>
	);
};

export default ResourceGroupMetrics;
