import * as React from "react";
import { Tooltip, Box, Typography } from "@mui/material";

/**
 * Renders a tooltip.
 * - If `description` is provided, shows title/description/unit.
 * - If `value` is provided (number), shows label + percentage (legacy behavior for utilization bars).
 *
 * @param {{
 *   label?: string,
 *   value?: number,
 *   title?: string,
 *   description?: string,
 *   unit?: string,
 *   children: React.ReactNode,
 *   [key: string]: any
 * }} props
 */
const HoverInfo = ({ label, value, title, description, unit, children, ...props }) => {
	let tooltipContent = null;

	if (description) {
		tooltipContent = (
			<Box sx={{ textAlign: "center", maxWidth: 300 }}>
				{title && (
					<Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 0.5 }}>
						{title}
					</Typography>
				)}
				<Typography variant="body2" sx={{ mb: unit ? 0.5 : 0 }}>
					{description}
				</Typography>
				{unit && (
					<Typography variant="caption" color="text.secondary">
						Unit: {unit}
					</Typography>
				)}
			</Box>
		);
	} else if (typeof value === "number") {
		tooltipContent = (
			<Box sx={{ textAlign: "center" }}>
				<Typography variant="body2" sx={{ fontWeight: 600 }}>
					{label}
				</Typography>
				<Typography variant="caption">{(value * 100).toFixed(1)}%</Typography>
			</Box>
		);
	} else if (title) {
		// Fallback if only title is provided
		tooltipContent = title;
	}

	const containerSx = {
		display: "block",
		height: "100%",
		cursor: description ? "help" : "default",
		...(props.sx || {}),
	};

	if (!tooltipContent) {
		return (
			<Box component="span" {...props} sx={containerSx}>
				{children}
			</Box>
		);
	}

	return (
		<Tooltip title={tooltipContent} arrow placement="top">
			<Box component="span" {...props} sx={containerSx}>
				{children}
			</Box>
		</Tooltip>
	);
};

export default HoverInfo;
