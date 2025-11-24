import * as React from "react";
import { Box } from "@mui/material";
import { useNavigate, useParams } from "react-router-dom";
import { SplitScreen, Left, Right } from "../components/split-screen/SplitScreen";
import ExplorerTree from "../components/explorer/tree/ExplorerTree";
import WelcomeView from "../components/explorer/views/welcome/WelcomeView";
import ResourceGroupsData from "../components/explorer/views/welcome/ResourceGroupsData";
import ResourceGroupView from "../components/explorer/views/resource-groups/ResourceGroupView";
import { paths } from "../paths";

/**
 * Monitor page component
 * @returns JSX.Element
 */
const ExplorerPage = () => {
	const navigate = useNavigate();
	const params = useParams();

	const resourceGroupName = params.name;
	const isResourceGroup = Boolean(resourceGroupName);
	const isWelcome = !isResourceGroup;

	const handleResourceGroupFocus = React.useCallback(
		(name) => {
			if (!name) return;
			navigate(paths.explorerResourceGroup(name));
		},
		[navigate],
	);

	const handleAgentFocus = React.useCallback(() => {
		navigate(paths.explorerWelcome);
	}, [navigate]);

	const handleResourceClick = React.useCallback((resource) => {
		// TODO: integrate with routing when resource page exists.
		void resource;
	}, []);

	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
					<Box sx={{ flex: 1, minHeight: 0, overflow: "auto" }}>
						<ExplorerTree
							onResourceGroupFocus={handleResourceGroupFocus}
							onAgentFocus={handleAgentFocus}
						/>
					</Box>
				</Box>
			</Left>
			<Right>
				{isWelcome && (
					<WelcomeView
						renderResourceGroups={(props) => (
							<ResourceGroupsData
								{...props}
								onResourceGroupClick={(group) => handleResourceGroupFocus(group.name || group.id)}
							/>
						)}
					/>
				)}
				{isResourceGroup && (
					<ResourceGroupView
						resourceGroupName={resourceGroupName}
						onResourceClick={handleResourceClick}
					/>
				)}
			</Right>
		</SplitScreen>
	);
};

export default ExplorerPage;
