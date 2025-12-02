import * as React from "react";
import { Box } from "@mui/material";
import { useNavigate, useParams } from "react-router-dom";
import { SplitScreen, Left, Right } from "../components/split-screen/SplitScreen";
import ExplorerTree from "../components/explorer/tree/ExplorerTree";
import WelcomeView from "../components/explorer/views/welcome/WelcomeView";
import ResourceGroupsData from "../components/explorer/views/welcome/ResourceGroupsData";
import ResourceGroupView from "../components/explorer/views/resource-groups/ResourceGroupView";
import ResourceView from "../components/explorer/views/resources/ResourceView";
import { paths } from "../paths";

/**
 * Monitor page component
 * @returns JSX.Element
 */
const ExplorerPage = () => {
	const navigate = useNavigate();
	const params = useParams();

	const resourceGroupName = params.name;
	const resourceName = params.resource;
	const groupParam = params.group;

	const isResource = Boolean(resourceName);
	const isResourceGroup = Boolean(resourceGroupName) && !isResource;
	const isWelcome = !isResourceGroup && !isResource;

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

	const handleResourceClick = React.useCallback(
		(resource, parent) => {
			const rName = resource.name || resource.id || resource.key;
			let gName;

			if (parent && parent.type === "resource-group") {
				gName = parent.name || parent.id;
			} else if (parent && parent.type === "agent") {
				// Rogue resource from tree (parent is agent)
				gName = undefined;
			} else {
				// Fallback to context (WelcomeView or ResourceGroupView where parent is undefined)
				gName = resourceGroupName || groupParam;
			}

			navigate(paths.explorerResource(gName, rName));
		},
		[navigate, resourceGroupName, groupParam],
	);

	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
					<Box sx={{ flex: 1, minHeight: 0, overflow: "auto" }}>
						<ExplorerTree
							onResourceGroupFocus={handleResourceGroupFocus}
							onAgentFocus={handleAgentFocus}
							onResourceFocus={handleResourceClick}
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
						onRogueResourceClick={handleResourceClick}
					/>
				)}
				{isResourceGroup && (
					<ResourceGroupView
						resourceGroupName={resourceGroupName}
						onResourceClick={handleResourceClick}
					/>
				)}
				{isResource && <ResourceView resourceName={resourceName} resourceGroupName={groupParam} />}
			</Right>
		</SplitScreen>
	);
};

export default ExplorerPage;
