import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import DashboardTable from "./DashboardTable";
import { emptyStateCellSx, sectionTitleSx } from "./table-styles";

/**
 * Render table rows for a list of metrics.
 *
 * @param {Array<{ name: string, value?: unknown, unit?: string, lastUpdate?: string | null }>} metrics
 * @param {boolean} showUnit
 * @param {boolean} showLastUpdate
 * @returns {JSX.Element | JSX.Element[]}
 */
const renderMetricsRows = (metrics, showUnit, showLastUpdate) => {
	const list = Array.isArray(metrics) ? metrics : [];
	if (list.length === 0) {
		const colSpan = 2 + (showUnit ? 1 : 0) + (showLastUpdate ? 1 : 0);
		return (
			<TableRow>
				<TableCell colSpan={colSpan} sx={emptyStateCellSx}>
					No metrics
				</TableCell>
			</TableRow>
		);
	}

	return list.map((m) => (
		<TableRow key={m.name}>
			<TableCell>{m.name}</TableCell>
			<TableCell align={showUnit || showLastUpdate ? "left" : "right"}>
				{String(m.value ?? "")}
			</TableCell>
			{showUnit && <TableCell>{m.unit === "1" ? "%" : (m.unit ?? "")}</TableCell>}
			{showLastUpdate && <TableCell align="right">{m.lastUpdate ?? ""}</TableCell>}
		</TableRow>
	));
};

/**
 * Generic Metrics section.
 *
 * @param {{
 *   metrics?: Array<{ name: string, value?: unknown, unit?: string, lastUpdate?: string | null }> | Record<string, any> | null,
 *   showUnit?: boolean,
 *   showLastUpdate?: boolean
 * }} props
 * @returns {JSX.Element | null}
 */
const MetricsTable = ({ metrics, showUnit = true, showLastUpdate = true }) => {
	const rows = React.useMemo(() => {
		if (!metrics) return [];
		if (Array.isArray(metrics)) {
			// Assume already normalized or close to it
			return metrics.map((m) => ({
				name: m.name || m.key,
				value: m.value,
				unit: m.unit,
				lastUpdate: m.lastUpdate,
			}));
		}
		if (typeof metrics === "object") {
			return Object.entries(metrics).map(([key, val]) => {
				let value = val;
				let unit = undefined;
				let lastUpdate = undefined;

				if (val && typeof val === "object" && ("value" in val || "unit" in val)) {
					value = val.value;
					unit = val.unit;
					lastUpdate = val.lastUpdate;
				}
				return { name: key, value, unit, lastUpdate };
			});
		}
		return [];
	}, [metrics]);

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
						<TableCell align={showUnit || showLastUpdate ? "left" : "right"}>Value</TableCell>
						{showUnit && <TableCell>Unit</TableCell>}
						{showLastUpdate && <TableCell align="right">Last Update</TableCell>}
					</TableRow>
				</TableHead>
				<TableBody>{renderMetricsRows(rows, showUnit, showLastUpdate)}</TableBody>
			</DashboardTable>
		</Box>
	);
};

export default MetricsTable;
