import * as React from "react";
import { Tooltip, Box, Typography } from "@mui/material";

/**
 * Renders a tooltip.
 * - If `description` is provided, shows title/description/unit.
 * - If `value` is provided (number), shows label + percentage (legacy behavior for utilization bars).
 *
 * @param {object} props - Component props
 * @param {string} [props.label] - Label for the value
 * @param {number} [props.value] - Value to display as percentage
 * @param {string} [props.title] - Tooltip title
 * @param {string} [props.description] - Tooltip description
 * @param {string} [props.unit] - Unit of the value
 * @param {React.ReactNode} props.children - Child elements
 * @param {import("@mui/material").SxProps} [props.sx] - Custom styles
 */
const HoverInfo = ({ label, value, title, description, unit, children, sx, ...props }) => {
	const displayUnit = React.useMemo(() => (unit === "1" ? "%" : unit), [unit]);

	const tooltipContent = React.useMemo(() => {
		if (description) {
			return (
				<Box sx={{ textAlign: "center", maxWidth: 400, px: 1 }}>
					{title && (
						<Typography
							variant="subtitle2"
							sx={{
								fontWeight: 600,
								mb: 0.5,
								wordBreak: "break-word",
								overflowWrap: "break-word",
								whiteSpace: "normal",
							}}
						>
							{title}
						</Typography>
					)}
					<Typography variant="body2" sx={{ mb: displayUnit ? 0.5 : 0 }}>
						{description}
					</Typography>
					{displayUnit && (
						<Typography variant="caption" color="text.secondary">
							Unit: {displayUnit}
						</Typography>
					)}
				</Box>
			);
		}
		if (typeof value === "number") {
			return (
				<Box sx={{ textAlign: "center" }}>
					<Typography variant="body2" sx={{ fontWeight: 600 }}>
						{label}
					</Typography>
					<Typography variant="caption">{(value * 100).toFixed(1)}%</Typography>
				</Box>
			);
		}
		if (title) {
			// Fallback if only title is provided
			return title;
		}
		return null;
	}, [description, title, displayUnit, value, label]);

	const containerSx = React.useMemo(
		() => ({
			display: "block",
			height: "100%",
			cursor: description ? "help" : "default",
			...(sx || {}),
		}),
		[description, sx],
	);

	if (!tooltipContent) {
		return (
			<Box component="span" {...props} sx={containerSx}>
				{children}
			</Box>
		);
	}

	return (
		<Tooltip
			title={tooltipContent}
			arrow
			placement="top"
			slotProps={{
				tooltip: {
					sx: {
						maxWidth: "none",
						"& .MuiTooltip-arrow": {
							color: "background.paper",
						},
					},
				},
			}}
		>
			<Box component="span" {...props} sx={containerSx}>
				{children}
			</Box>
		</Tooltip>
	);
};

export default React.memo(HoverInfo);
