import * as React from "react";
import { Box, Typography } from "@mui/material";

const MonitorsHeader = ({ lastUpdatedLabel }) => (
    <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
        <Typography variant="h5" gutterBottom sx={{ mb: 0 }}>
            Monitors
        </Typography>
        <Typography variant="caption" color="text.secondary">
            Last updated: {lastUpdatedLabel}
        </Typography>
    </Box>
);

export default MonitorsHeader;
