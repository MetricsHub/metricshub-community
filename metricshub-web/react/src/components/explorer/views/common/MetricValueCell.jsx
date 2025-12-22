import * as React from "react";
import { Box, Typography } from "@mui/material";
import HoverInfo from "../monitors/components/HoverInfo";
import TruncatedText from "./TruncatedText";
import { formatMetricValue } from "../../../../utils/formatters";
import { truncatedCellSx } from "./table-styles";

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
		return (
			<Box sx={{ ...truncatedCellSx, textAlign: align, width: "100%" }}>-</Box>
		);
	}

	const cleanUnit = unit ? unit.replace(/[{}]/g, "") : "";
	const displayUnit = cleanUnit === "1" ? "" : cleanUnit;
	const formattedValue = formatMetricValue(value, unit);
	const rawValue = String(value);
	const showRaw = typeof value === "number" && formattedValue !== rawValue && formattedValue !== "";

	return (
		<Box sx={{ ...truncatedCellSx, textAlign: align, width: "100%" }}>
			{showRaw ? (
				<HoverInfo
					title={
						<Typography variant="body2">
							Raw value : {value} {displayUnit}
						</Typography>
					}
					sx={{ display: "inline-block", maxWidth: "100%", ...truncatedCellSx }}
				>
					{formattedValue}
				</HoverInfo>
			) : (
				<TruncatedText text={formattedValue}>{formattedValue}</TruncatedText>
			)}
		</Box>
	);
};

export default React.memo(MetricValueCell);
