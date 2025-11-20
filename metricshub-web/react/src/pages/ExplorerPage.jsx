import * as React from "react";
import { Box } from "@mui/material";
import { SplitScreen, Left, Right } from "../components/split-screen/SplitScreen";
import ExplorerTree from "../components/explorer/tree/ExplorerTree";
import WelcomeView from "../components/explorer/views/welcome/WelcomeView";
// Removed header and updated date UI

/**
 * Monitor page component
 * @returns JSX.Element
 */
const ExplorerPage = () => {
	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
					<Box sx={{ flex: 1, minHeight: 0, overflow: "auto" }}>
						<ExplorerTree />
					</Box>
				</Box>
			</Left>
			<Right>
				<WelcomeView />
			</Right>
		</SplitScreen>
	);
};

export default ExplorerPage;
