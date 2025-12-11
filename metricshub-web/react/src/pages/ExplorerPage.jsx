import * as React from "react";
import { Box } from "@mui/material";
import { useNavigate, useParams, useLocation } from "react-router-dom";
import { useAppDispatch, useAppSelector } from "../hooks/store";
import { setLastVisitedPath } from "../store/slices/explorer-slice";
import { selectExplorerHierarchy } from "../store/slices/explorer-slice";
import { SplitScreen, Left, Right } from "../components/split-screen/SplitScreen";
import ExplorerTree from "../components/explorer/tree/ExplorerTree";
import WelcomeView from "../components/explorer/views/welcome/WelcomeView";
import ResourceGroupsData from "../components/explorer/views/welcome/ResourceGroupsData";
import ResourceGroupView from "../components/explorer/views/resource-groups/ResourceGroupView";
import ResourceView from "../components/explorer/views/resources/ResourceView";
import AppBreadcrumbs from "../components/common/AppBreadcrumbs";
import { paths } from "../paths";

/**
 * Monitor page component
 * @returns JSX.Element
 */
/**
 * Builds the node ID for tree selection based on URL params and hierarchy.
 * @param {any} hierarchyRaw - Raw hierarchy from the store
 * @param {string|null} resourceGroupName - Resource group name from URL
 * @param {string|null} resourceName - Resource name from URL
 * @param {string|null} groupParam - Group param from URL (for resources)
 * @param {boolean} isWelcome - Whether we're on the welcome view
 * @returns {string|null} The node ID to select, or null if none
 */
const buildSelectedNodeId = (
	hierarchyRaw,
	resourceGroupName,
	resourceName,
	groupParam,
	isWelcome,
) => {
	if (!hierarchyRaw) return null;

	// Get agent name from hierarchy root
	// The root node itself represents the agent (id: "root/{agentName}")
	const agentName = hierarchyRaw.name;
	if (!agentName) return null;

	// For welcome view, select the agent/root node
	if (isWelcome) {
		return `root/${agentName}`;
	}

	const parts = ["root", agentName];

	// Determine which resource group to use
	const effectiveGroupName = resourceGroupName || groupParam;

	if (effectiveGroupName) {
		parts.push(effectiveGroupName);
	}

	if (resourceName) {
		parts.push(resourceName);
	}

	return parts.join("/");
};

const ExplorerPage = () => {
	const navigate = useNavigate();
	const params = useParams();
	const location = useLocation();
	const dispatch = useAppDispatch();
	const hierarchyRaw = useAppSelector(selectExplorerHierarchy);

	React.useEffect(() => {
		dispatch(setLastVisitedPath(location.pathname));
	}, [location.pathname, dispatch]);

	const resourceGroupName = params.name;
	const resourceName = params.resource;
	const groupParam = params.group;

	const isResource = Boolean(resourceName);
	const isResourceGroup = Boolean(resourceGroupName) && !isResource;
	const isWelcome = !isResourceGroup && !isResource;

	// Compute selected node ID based on URL params
	const selectedNodeId = React.useMemo(
		() => buildSelectedNodeId(hierarchyRaw, resourceGroupName, resourceName, groupParam, isWelcome),
		[hierarchyRaw, resourceGroupName, resourceName, groupParam, isWelcome],
	);

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
							selectedNodeId={selectedNodeId}
							onResourceGroupFocus={handleResourceGroupFocus}
							onAgentFocus={handleAgentFocus}
							onResourceFocus={handleResourceClick}
						/>
					</Box>
				</Box>
			</Left>
			<Right>
				<AppBreadcrumbs />
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
