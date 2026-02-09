import * as React from "react";
import { Paper, Typography, Box, Tooltip } from "@mui/material";
import PropTypes from "prop-types";

/**
 * MetricCard - A professional, reusable card component for displaying metrics
 *
 * Features:
 * - Gradient backgrounds
 * - Hover effects
 * - Optional icons
 * - Responsive design
 * - Accessibility support
 *
 * @param {object} props - Component props
 * @param {string} props.label - The metric label/title
 * @param {React.ReactNode} props.value - The metric value (can be string, number, or JSX)
 * @param {string} [props.gradient] - CSS gradient background
 * @param {React.ReactNode} [props.icon] - Optional icon component
 * @param {string} [props.tooltip] - Optional tooltip text
 * @param {object} [props.sx] - Additional MUI sx props
 * @returns {JSX.Element}
 */
const MetricCard = ({ label, value, gradient, icon, tooltip, sx = {} }) => {
	const content = (
		<Paper
			elevation={0}
			sx={{
				background: gradient || "linear-gradient(135deg, #1565C0 0%, #1976D2 100%)",
				p: 2.5,
				borderRadius: 2,
				color: "white",
				display: "flex",
				flexDirection: "column",
				alignItems: "center",
				flex: 1,
				textAlign: "center",
				minWidth: 150,
				position: "relative",
				overflow: "hidden",
				boxShadow: "0 2px 8px rgba(0, 0, 0, 0.15)",
				"&::before": {
					content: '""',
					position: "absolute",
					top: 0,
					left: 0,
					right: 0,
					bottom: 0,
					background: "linear-gradient(135deg, rgba(255,255,255,0.1) 0%, rgba(255,255,255,0) 100%)",
					pointerEvents: "none",
				},
				...sx,
			}}
		>
			{icon && (
				<Box
					sx={{
						mb: 1,
						opacity: 0.9,
						display: "flex",
						alignItems: "center",
						justifyContent: "center",
						fontSize: "2rem",
					}}
				>
					{icon}
				</Box>
			)}

			<Typography
				variant="subtitle2"
				sx={{
					opacity: 0.95,
					mb: 1,
					textTransform: "uppercase",
					letterSpacing: 1,
					fontSize: "0.75rem",
					fontWeight: 600,
					textShadow: "0 1px 2px rgba(0, 0, 0, 0.1)",
				}}
			>
				{label}
			</Typography>

			<Typography
				variant="h5"
				sx={{
					fontWeight: "bold",
					textShadow: "0 2px 4px rgba(0, 0, 0, 0.2)",
					lineHeight: 1.2,
				}}
			>
				{value}
			</Typography>
		</Paper>
	);

	if (tooltip) {
		return (
			<Tooltip title={tooltip} arrow placement="top">
				{content}
			</Tooltip>
		);
	}

	return content;
};

MetricCard.propTypes = {
	label: PropTypes.string.isRequired,
	value: PropTypes.node.isRequired,
	gradient: PropTypes.string,
	icon: PropTypes.node,
	tooltip: PropTypes.string,
	sx: PropTypes.object,
};

export default React.memo(MetricCard);
