import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import DashboardTable from "../common/DashboardTable";
import { emptyStateCellSx, sectionTitleSx } from "../common/table-styles";

/**
 * Render table rows for a list of resource metrics.
 *
 * @param {Array<{ name: string, iconType?: string, value?: unknown, unit?: string, lastUpdate?: string | null }>} metrics
 * @returns {JSX.Element | JSX.Element[]}
 */
const renderMetricsRows = (metrics) => {
	const list = Array.isArray(metrics) ? metrics : [];
	if (list.length === 0) {
		return (
			<TableRow>
				<TableCell colSpan={4} sx={emptyStateCellSx}>
					No metrics
				</TableCell>
			</TableRow>
		);
	}

	return list.map((m) => (
		<TableRow key={m.name}>
			<TableCell>
				{m.name}
				{m.iconType ? ` (${m.iconType})` : ""}
			</TableCell>
			<TableCell>{m.value}</TableCell>
			<TableCell>{m.unit}</TableCell>
			<TableCell>{m.lastUpdate}</TableCell>
		</TableRow>
	));
};

/**
 * Metrics section for a single resource.
 *
 * @param {{ metrics?: Array<{ name: string, iconType?: string, value?: unknown, unit?: string, lastUpdate?: string | null }> | null }} props
 * @returns {JSX.Element}
 */
const ResourceMetrics = ({ metrics }) => {
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
						<TableCell>Name</TableCell>
						<TableCell>Value</TableCell>
						<TableCell>Unit</TableCell>
						<TableCell>Last Update</TableCell>
					</TableRow>
				</TableHead>
				<TableBody>{renderMetricsRows(rows)}</TableBody>
			</DashboardTable>
		</Box>
	);
};

export default ResourceMetrics;
