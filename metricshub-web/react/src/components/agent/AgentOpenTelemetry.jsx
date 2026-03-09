import React, { memo } from "react";
import { Box, Typography, Card, CardContent } from "@mui/material";
import SettingsInputComponentIcon from "@mui/icons-material/SettingsInputComponent";

const AgentOpenTelemetry = memo(() => {
	return (
		<Box>
			<Typography variant="h6" sx={{ mb: 2 }}>
				OpenTelemetry Configuration
			</Typography>
			<Card variant="outlined">
				<CardContent>
					<Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
						<SettingsInputComponentIcon sx={{ fontSize: 40, color: "text.secondary" }} />
						<Box>
							<Typography variant="body1" color="text.secondary">
								OpenTelemetry Collector configuration will be displayed here.
							</Typography>
							<Typography variant="body2" color="text.disabled" sx={{ mt: 0.5 }}>
								This feature is coming soon.
							</Typography>
						</Box>
					</Box>
				</CardContent>
			</Card>
		</Box>
	);
});

AgentOpenTelemetry.displayName = "AgentOpenTelemetry";

export default AgentOpenTelemetry;
