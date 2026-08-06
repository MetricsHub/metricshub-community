import * as React from "react";
import { Box, Link, Paper, Stack, Typography } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";

const CONNECTORS_DIRECTORY_URL = "https://metricshub.com/docs/latest/connectors-directory";

/**
 * Info card about connectors (stepper rail or inline help).
 */
const AboutConnectorsCard = () => (
	<Paper
		variant="outlined"
		sx={{
			borderRadius: 2,
			p: 2,
			bgcolor: "transparent",
			borderColor: "divider",
		}}
	>
		<Stack direction="row" spacing={1.5} alignItems="flex-start">
			<InfoOutlinedIcon color="primary" sx={{ mt: 0.15, flexShrink: 0 }} />
			<Box sx={{ minWidth: 0 }}>
				<Typography variant="subtitle2" color="primary.main" fontWeight={700}>
					About Connectors
				</Typography>
				<Typography variant="body2" color="text.primary" sx={{ mt: 0.5, opacity: 0.85 }}>
					Connectors collect metrics and data from your resource using the selected protocols.
				</Typography>
				<Link
					href={CONNECTORS_DIRECTORY_URL}
					target="_blank"
					rel="noopener noreferrer"
					variant="body2"
					sx={{
						display: "inline-flex",
						alignItems: "center",
						gap: 0.5,
						mt: 0.75,
						fontWeight: 600,
					}}
				>
					Learn more
					<OpenInNewIcon sx={{ fontSize: 14 }} />
				</Link>
			</Box>
		</Stack>
	</Paper>
);

export default AboutConnectorsCard;
