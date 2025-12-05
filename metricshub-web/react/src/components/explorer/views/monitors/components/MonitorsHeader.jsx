import * as React from "react";
import { Box, Typography } from "@mui/material";

/**
 * Header for the Monitors section.
 *
 * @param {{ lastUpdatedLabel: string }} props
 */
const MonitorsHeader = ({ lastUpdatedLabel }) => (
	<Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
		<Typography variant="h5" gutterBottom sx={{ mb: 0 }}>
			Connectors
		</Typography>
		<Typography variant="caption" color="text.secondary">
			Last updated: {lastUpdatedLabel}
		</Typography>
	</Box>
);

export default MonitorsHeader;
