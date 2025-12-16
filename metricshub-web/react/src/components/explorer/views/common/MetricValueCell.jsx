import * as React from "react";
import { TableCell, Typography } from "@mui/material";
import HoverInfo from "../monitors/components/HoverInfo";
import TruncatedText from "./TruncatedText";
import { formatMetricValue } from "../../../../utils/formatters";

const truncatedCellSx = {
	whiteSpace: "nowrap",
	overflow: "hidden",
	textOverflow: "ellipsis",
};

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

	const cleanUnit = unit ? unit.replace(/[{}]/g, "") : "";
	const formattedValue = formatMetricValue(value, unit);
	const rawValue = String(value);
	const showRaw = typeof value === "number" && formattedValue !== rawValue && formattedValue !== "";

	return (
		<TableCell align={align} sx={truncatedCellSx}>
			{showRaw ? (
				<HoverInfo
					title={
						<Typography variant="body2">
							Raw value : {value} {cleanUnit}
						</Typography>
					}
					sx={{ display: "inline-block", maxWidth: "100%", ...truncatedCellSx }}
				>
					{formattedValue}
				</HoverInfo>
			) : (
				<TruncatedText text={formattedValue}>{formattedValue}</TruncatedText>
			)}
		</TableCell>
	);
};

export default React.memo(MetricValueCell);
