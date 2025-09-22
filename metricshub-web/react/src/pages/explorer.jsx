import * as React from "react";
import { Typography, Button, Box } from "@mui/material";
import { withAuthGuard } from "../hocs/with-auth-guard";
import { SplitScreen, Left, Right } from "../components/split-screen/split-screen";

/**
 * Monitor page component
 * @returns JSX.Element
 */
// eslint-disable-next-line react-refresh/only-export-components
const ExplorerPage = () => {
	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Typography>Explorer Tree View</Typography>
			</Left>
			<Right>
				<Typography>Monitored Resources</Typography>
				<Box sx={{ display: "flex", gap: 2, mt: 2, width: "20%", flexDirection: "column" }}>
					<Button type="submit" variant="contained" size="small">
						Try it Free!
					</Button>
					<Button type="submit" variant="contained" size="medium">
						Try it Free!
					</Button>
					<Button type="submit" variant="contained" size="large">
						Try it Free!
					</Button>
				</Box>
			</Right>
		</SplitScreen>
	);
};

// Wrap with AuthGuard
// eslint-disable-next-line react-refresh/only-export-components
export default withAuthGuard(ExplorerPage);
