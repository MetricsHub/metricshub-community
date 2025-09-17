import * as React from "react";
import { Typography } from "@mui/material";
import { withAuthGuard } from "../hocs/with-auth-guard";
import { SplitScreen, Left, Right } from "../components/split-screen/split-screen";

/**
 * Monitor page component
 * @returns JSX.Element
 */
// eslint-disable-next-line react-refresh/only-export-components
const MonitorPage = () => {
	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Typography>Tree View</Typography>
			</Left>
			<Right>
				<Typography>Monitored Resources</Typography>
			</Right>
		</SplitScreen>
	);
};

// Wrap with AuthGuard
// eslint-disable-next-line react-refresh/only-export-components
export default withAuthGuard(MonitorPage);
