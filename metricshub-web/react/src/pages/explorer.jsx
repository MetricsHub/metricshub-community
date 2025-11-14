import * as React from "react";
import { Typography } from "@mui/material";
import { SplitScreen, Left, Right } from "../components/split-screen/split-screen";

/**
 * Monitor page component
 * @returns JSX.Element
 */
const ExplorerPage = () => {
	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Typography>Explorer Tree View</Typography>
			</Left>
			<Right>
				<Typography>Monitored Resources</Typography>
			</Right>
		</SplitScreen>
	);
};

export default ExplorerPage;
