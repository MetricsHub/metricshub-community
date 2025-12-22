import * as React from "react";
import { DataGrid } from "@mui/x-data-grid";
import { dataGridSx } from "./table-styles";
import TruncatedText from "./TruncatedText";
import MetricValueCell from "./MetricValueCell";

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

	if (rows.length === 0) {
		return null;
	}

	return (
		<DataGrid
			rows={rows.map((r) => ({ id: r.name, ...r }))}
			columns={[
				{
					field: "name",
					headerName: "Name",
					flex: 1,
					renderCell: (params) => <TruncatedText text={params.value}>{params.value}</TruncatedText>,
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
			]}
			disableRowSelectionOnClick
			hideFooter
			autoHeight
			density="compact"
			sx={dataGridSx}
		/>
	);
};

export default React.memo(MetricsTable);
