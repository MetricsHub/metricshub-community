import * as React from "react";
import { Box } from "@mui/material";
import { useNavigate, useParams, useLocation } from "react-router-dom";
import { useDispatch } from "react-redux";
import { SplitScreen, Left, Right } from "../components/split-screen/SplitScreen";
import ExplorerTree from "../components/explorer/tree/ExplorerTree";
import WelcomeView from "../components/explorer/views/welcome/WelcomeView";
import ResourceGroupsData from "../components/explorer/views/welcome/ResourceGroupsData";
import ResourceGroupView from "../components/explorer/views/resource-groups/ResourceGroupView";
import ResourceView from "../components/explorer/views/resources/ResourceView";
import { paths } from "../paths";
import { setLastVisitedPath } from "../store/slices/explorer-slice";

/**
 * Monitor page component
 * @returns JSX.Element
 */
const ExplorerPage = () => {
	const navigate = useNavigate();
	const params = useParams();
	const location = useLocation();
	const dispatch = useDispatch();

	React.useEffect(() => {
		dispatch(setLastVisitedPath(location.pathname));
	}, [location.pathname, dispatch]);

	const resourceGroupName = params.name;
	const resourceName = params.resourceName;
	const isResourceGroup = Boolean(resourceGroupName) && !resourceName;
	const isResource = Boolean(resourceName);
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
		(resource, group) => {
			if (!resource) return;
			const name = resource.name || resource.id || resource.key;
			if (!name) return;
			if (group && (group.name || group.id)) {
				const groupName = group.name || group.id;
				navigate(paths.explorerGroupResource(groupName, name));
			} else {
				navigate(paths.explorerResource(name));
			}
		},
		[navigate],
	);

	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
					<Box sx={{ flex: 1, minHeight: 0, overflow: "auto" }}>
						<ExplorerTree
							onResourceGroupFocus={handleResourceGroupFocus}
							onAgentFocus={handleAgentFocus}
							onResourceFocus={(resource, group) => handleResourceClick(resource, group)}
						/>
					</Box>
				</Box>
			</Left>
			<Right>
				{isWelcome && (
					<WelcomeView
						onRogueResourceClick={(resource) => handleResourceClick(resource, undefined)}
						renderResourceGroups={(props) => (
							<ResourceGroupsData
								{...props}
								onResourceGroupClick={(group) => handleResourceGroupFocus(group.name || group.id)}
							/>
						)}
					/>
				)}
				{isResourceGroup && !isResource && (
					<ResourceGroupView
						resourceGroupName={resourceGroupName}
						onResourceClick={(resource) =>
							handleResourceClick(resource, { name: resourceGroupName })
						}
					/>
				)}
				{isResource && (
					<ResourceView resourceName={resourceName} resourceGroupName={resourceGroupName} />
				)}
			</Right>
		</SplitScreen>
	);
};

export default ExplorerPage;
