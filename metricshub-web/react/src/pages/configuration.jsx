import * as React from "react";
import { Typography } from "@mui/material";
import { withAuthGuard } from "../hocs/with-auth-guard";
import { SplitScreen, Left, Right } from "../components/split-screen/split-screen";
const YamlEditor = React.lazy(() => import("../components/yaml-editor/YamlEditor"));

/**
 * Configuration page component.
 * @returns The configuration page with a split view: tree on the left, YAML editor on the right.
 */

const ConfigurationPage = () => (
	<SplitScreen initialLeftPct={35}>
		<Left>
			<Typography>Configuration Tree View</Typography>
		</Left>

		<Right>
			<YamlEditor
			//onSave={(text) => {
			// plug into Redux
			//console.log("YAML saved:", text);
			//}}
			//onChange={(text) => {
			// optional: dispatch to Redux for live state
			//}}
			/>
		</Right>
	</SplitScreen>
);

export default withAuthGuard(ConfigurationPage);
