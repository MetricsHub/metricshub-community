import * as React from "react";
import { Box, Typography } from "@mui/material";
import { withAuthGuard } from "../hocs/with-auth-guard";
import { SplitScreen, Left, Right } from "../components/split-screen/split-screen";

const ConfigurationPage = () => (
	<SplitScreen initialLeftPct={35}>
		<Left>
			<Typography>Configuration Tree View</Typography>
		</Left>
		<Right>
			<Typography>YAML Editor</Typography>
		</Right>
	</SplitScreen>
);

export default withAuthGuard(ConfigurationPage);
