import React, { memo } from "react";
import PropTypes from "prop-types";
import { Paper, Box, Typography } from "@mui/material";

/**
 * Stat card component displaying a metric with icon.
 */
const StatCard = memo(({ icon, label, value, subValue, bgcolor }) => (
	<Paper
		elevation={0}
		sx={{
			bgcolor,
			p: 2.5,
			borderRadius: 2,
			color: "white",
			display: "flex",
			flexDirection: "column",
			gap: 1,
			minWidth: 200,
			flex: "1 1 200px",
		}}
	>
		<Box sx={{ display: "flex", alignItems: "center", gap: 1, opacity: 0.9 }}>
			{icon}
			<Typography variant="subtitle2" sx={{ textTransform: "uppercase", letterSpacing: 0.5 }}>
				{label}
			</Typography>
		</Box>
		<Typography variant="h4" sx={{ fontWeight: "bold" }}>
			{value}
		</Typography>
		{subValue && (
			<Typography variant="body2" sx={{ opacity: 0.8 }}>
				{subValue}
			</Typography>
		)}
	</Paper>
));

StatCard.displayName = "StatCard";

StatCard.propTypes = {
	icon: PropTypes.node.isRequired,
	label: PropTypes.string.isRequired,
	value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
	subValue: PropTypes.string,
	bgcolor: PropTypes.string,
};

export default StatCard;
