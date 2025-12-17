import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow, Button } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import DashboardTable from "./DashboardTable";
import { emptyStateCellSx, sectionTitleSx, truncatedCellSx } from "./table-styles";
import TruncatedText from "./TruncatedText";
import MetricValueCell from "./MetricValueCell";

/**
 * Render table rows for a list of metrics.
 *
 * @param {Array<{ name: string, value?: unknown, unit?: string, lastUpdate?: string | null }>} metrics
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
		<TableRow key={m.name}>
			<TableCell sx={truncatedCellSx}>
				<TruncatedText text={m.name}>{m.name}</TruncatedText>
			</TableCell>
			<MetricValueCell value={m.value} unit={m.unit} align="left" />
		</TableRow>
	));
};

/**
 * Generic Metrics section.
 *
 * @param {{
 *   metrics?: Array<{ name: string, value?: unknown, unit?: string, lastUpdate?: string | null }> | Record<string, any> | null,
 * }} props
 * @returns {JSX.Element | null}
 */
const MetricsTable = ({ metrics }) => {
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

	const valueAlign = "left";

	const colWidth = "50%";

	if (rows.length === 0) {
		return null;
	}

	return (
		<DashboardTable>
			<TableHead>
				<TableRow>
					<TableCell sx={{ width: colWidth }}>Name</TableCell>
					<TableCell align={valueAlign} sx={{ width: colWidth }}>
						Value
					</TableCell>
				</TableRow>
			</TableHead>
			<TableBody>{renderMetricsRows(rows)}</TableBody>
		</DashboardTable>
	);
};

export default React.memo(MetricsTable);
