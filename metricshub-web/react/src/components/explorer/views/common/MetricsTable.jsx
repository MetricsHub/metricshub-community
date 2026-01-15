import * as React from "react";
import { DataGrid } from "@mui/x-data-grid";
import { dataGridSx } from "./table-styles";
import MetricNameHighlighter from "./MetricNameHighlighter.jsx";
import MetricValueCell from "./MetricValueCell";

const METRICS_COLUMNS = [
	{
		field: "name",
		headerName: "Name",
		flex: 1,
		renderCell: (params) => <MetricNameHighlighter name={params.value} />,
	},
	{
		field: "value",
		headerName: "Value",
		flex: 1,
		align: "left",
		headerAlign: "left",
		renderCell: (params) => (
			<MetricValueCell value={params.row.value} unit={params.row.unit} align="left" />
		),
	},
];

/**
 * Generic Metrics section.
 *
 * @param {object} props - Component props
 * @param {Array<{ name: string, value?: unknown, unit?: string, lastUpdate?: string | null }> | Record<string, any> | null} [props.metrics] - Metrics to display
 * @returns {JSX.Element | null}
 */
const MetricsTable = ({ metrics }) => {
	const rows = React.useMemo(() => {
		if (!metrics) return [];
		let normalized = [];
		if (Array.isArray(metrics)) {
			// Assume already normalized or close to it
			normalized = metrics.map((m) => ({
				name: m.name || m.key,
				value: m.value,
				unit: m.unit,
				lastUpdate: m.lastUpdate,
			}));
		} else if (typeof metrics === "object") {
			normalized = Object.entries(metrics).map(([key, val]) => {
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

		return normalized.map((r) => ({ id: r.name, ...r }));
	}, [metrics]);

	if (rows.length === 0) {
		return null;
	}

	return (
		<DataGrid
			rows={rows}
			columns={METRICS_COLUMNS}
			disableRowSelectionOnClick
			hideFooter
			autoHeight
			density="compact"
			sx={dataGridSx}
		/>
	);
};

export default React.memo(MetricsTable);
