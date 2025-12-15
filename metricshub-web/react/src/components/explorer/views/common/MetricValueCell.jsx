import * as React from "react";
import { TableCell, Typography } from "@mui/material";
import HoverInfo from "../monitors/components/HoverInfo";
import { formatMetricValue, cleanUnit } from "../../../../utils/formatters";

/**
 * Renders a table cell for a metric value.
 * Handles formatting and showing raw value on hover.
 *
 * @param {{
 *   value: any,
 *   unit?: string,
 *   align?: "left" | "right" | "center" | "justify" | "inherit"
 * }} props
 */
const MetricValueCell = ({ value, unit, align = "left" }) => {
	if (value === undefined || value === null) {
		return <TableCell align={align}>-</TableCell>;
	}

	const cleanedUnit = cleanUnit(unit);
	const formattedValue = formatMetricValue(value, unit);
	const rawValue = String(value);
	const showRaw = typeof value === "number" && formattedValue !== rawValue && formattedValue !== "";

	return (
		<TableCell align={align}>
			{showRaw ? (
				<HoverInfo
					title={
						<Typography variant="body2">
							Raw value : {value} {cleanedUnit}
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
	);
};

export default React.memo(MetricValueCell);
