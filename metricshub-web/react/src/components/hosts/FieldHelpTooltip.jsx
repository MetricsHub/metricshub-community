import * as React from "react";
import { Box, IconButton, Tooltip } from "@mui/material";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";

/**
 * Question-mark help icon with a multi-line tooltip.
 *
 * @param {object} props
 * @param {string} props.title
 */
const FieldHelpTooltip = ({ title }) => (
	<Tooltip
		title={
			<Box sx={{ whiteSpace: "pre-wrap", fontSize: "0.75rem", lineHeight: 1.6, maxWidth: 320 }}>
				{title}
			</Box>
		}
		arrow
		placement="top"
	>
		<IconButton
			size="small"
			aria-label="More information"
			sx={{
				p: 0.25,
				ml: 0.25,
				color: "text.secondary",
				"&:hover": { color: "text.primary", bgcolor: "transparent" },
			}}
		>
			<HelpOutlineIcon sx={{ fontSize: 16, display: "block" }} />
		</IconButton>
	</Tooltip>
);

export default FieldHelpTooltip;
