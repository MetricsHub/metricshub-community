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
 * @param {object} props - Component props
 * @param {any} props.value - The metric value
 * @param {string} [props.unit] - The unit of the metric
 * @param {"left" | "right" | "center" | "justify" | "inherit"} [props.align="left"] - Text alignment
 */
const MetricValueCell = ({ value, unit, align = "left" }) => {
	if (value === undefined || value === null) {
		return <Box sx={{ ...truncatedCellSx, textAlign: align, width: "100%" }}>-</Box>;
	}

	const cleanUnit = unit ? unit.replace(/[{}]/g, "") : "";
	const displayUnit = cleanUnit === "1" ? "" : cleanUnit;
	const formattedValue = formatMetricValue(value, unit);
	const rawValue = String(value);
	const showRaw = typeof value === "number" && formattedValue !== rawValue && formattedValue !== "";

	return (
		<Box
			sx={{
				...truncatedCellSx,
				textAlign: align,
				width: "100%",
				display: "flex",
				alignItems: "center",
				justifyContent:
					align === "right" ? "flex-end" : align === "center" ? "center" : "flex-start",
			}}
		>
			{showRaw ? (
				<HoverInfo
					title={
						<Typography variant="body2">
							Raw value : {value} {displayUnit}
						</Typography>
					}
					sx={{
						display: "block",
						maxWidth: "100%",
						...truncatedCellSx,
					}}
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
