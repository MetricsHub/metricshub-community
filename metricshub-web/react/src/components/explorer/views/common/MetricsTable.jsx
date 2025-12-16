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
			<TableCell sx={truncatedCellSx}>
				<TruncatedText text={m.name}>{m.name}</TruncatedText>
			</TableCell>
			<MetricValueCell value={m.value} unit={m.unit} align="left" />
			{showUnit && (
				<TableCell sx={truncatedCellSx}>
					<TruncatedText text={m.unit === "1" ? "%" : (m.unit ?? "")}>
						{m.unit === "1" ? "%" : (m.unit ?? "")}
					</TruncatedText>
				</TableCell>
			)}
			{showLastUpdate && (
				<TableCell align="right" sx={truncatedCellSx}>
					<TruncatedText text={m.lastUpdate ?? ""}>{m.lastUpdate ?? ""}</TruncatedText>
				</TableCell>
			)}
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
	const [expanded, setExpanded] = React.useState(false);

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

	const shouldFold = React.useMemo(() => rows.length > 6, [rows.length]);

	const titleSx = React.useMemo(() => ({ ...sectionTitleSx, mb: 0 }), []);

	const valueAlign = "left";

	const colCount = React.useMemo(
		() => 2 + (showUnit ? 1 : 0) + (showLastUpdate ? 1 : 0),
		[showUnit, showLastUpdate],
	);
	const colWidth = React.useMemo(() => `${100 / colCount}%`, [colCount]);

	const handleToggleExpanded = React.useCallback(() => {
		setExpanded((prev) => !prev);
	}, []);

	if (rows.length === 0) {
		return null;
	}

	return (
		<Box>
			<Box display="flex" alignItems="center" gap={1} mb={!shouldFold || expanded ? 1 : 0}>
				<Typography variant="h6" sx={titleSx}>
					Metrics
				</Typography>
				{shouldFold && (
					<Button
						size="small"
						onClick={handleToggleExpanded}
						endIcon={expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
					>
						{expanded ? "Hide" : `Show (${rows.length})`}
					</Button>
				)}
			</Box>
			{(!shouldFold || expanded) && (
				<DashboardTable>
					<TableHead>
						<TableRow>
							<TableCell sx={{ width: colWidth }}>Name</TableCell>
							<TableCell align={valueAlign} sx={{ width: colWidth }}>
								Value
							</TableCell>
							{showUnit && <TableCell sx={{ width: colWidth }}>Unit</TableCell>}
							{showLastUpdate && (
								<TableCell align="right" sx={{ width: colWidth }}>
									Last Update
								</TableCell>
							)}
						</TableRow>
					</TableHead>
					<TableBody>{renderMetricsRows(rows, showUnit, showLastUpdate)}</TableBody>
				</DashboardTable>
			)}
		</Box>
	);
};

export default React.memo(MetricsTable);
