import * as React from "react";
import { Tooltip, Box, Typography } from "@mui/material";

/**
 * Renders a tooltip with a label and percentage value on hover.
 *
 * @param {{
 *   label: string,
 *   value: number,
 *   children: React.ReactNode,
 *   [key: string]: any
 * }} props
 */
const HoverInfo = ({ label, value, children, ...props }) => {
	return (
		<Tooltip
			title={
				<Box sx={{ textAlign: "center" }}>
					<Typography variant="body2" sx={{ fontWeight: 600 }}>
						{label}
					</Typography>
					<Typography variant="caption">{(value * 100).toFixed(1)}%</Typography>
				</Box>
			}
			arrow
			placement="top"
		>
			<Box component="span" sx={{ display: "block", height: "100%" }} {...props}>
				{children}
			</Box>
		</Tooltip>
	);
};

export default HoverInfo;
