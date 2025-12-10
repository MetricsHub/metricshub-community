import * as React from "react";
import { useSelector } from "react-redux";
import { Box, CircularProgress, Typography, Divider } from "@mui/material";
import {
	selectExplorerHierarchy,
	selectExplorerLoading,
	selectExplorerError,
} from "../../../../store/slices/explorer-slice";
import AgentData from "./AgentData";
import ResourceGroupsData from "./ResourceGroupsData";
import ResourcesTable from "../common/ResourcesTable";

/**
 * Default explorer welcome page displaying agent summary, groups and rogue resources.
 *
 * Optionally accepts a render prop to customize the ResourceGroups section,
 * so that parent components can inject navigation behaviour.
 *
 * @param {{ renderResourceGroups?:(props:{ resourceGroups:any[] }) => JSX.Element, onRogueResourceClick?: (resource: any) => void }} props
 * @returns {JSX.Element}
 */
const WelcomeView = ({ renderResourceGroups, onRogueResourceClick }) => {
	const hierarchy = useSelector(selectExplorerHierarchy);
	const loading = useSelector(selectExplorerLoading);
	const error = useSelector(selectExplorerError);

	// Memoize derived data to avoid recalculating on every render
	const resourceGroups = React.useMemo(
		() => hierarchy?.resourceGroups ?? [],
		[hierarchy?.resourceGroups],
	);
	// "Rogue" resources are those not attached to any resource group.
	const rogueResources = React.useMemo(() => hierarchy?.resources ?? [], [hierarchy?.resources]);
	const totalResourcesInGroups = React.useMemo(
		() => resourceGroups.reduce((acc, g) => acc + (g.resources?.length || 0), 0),
		[resourceGroups],
	);
	const totalResources = React.useMemo(
		() => totalResourcesInGroups + rogueResources.length,
		[totalResourcesInGroups, rogueResources.length],
	);

	if (loading && !hierarchy) {
		return (
			<Box display="flex" justifyContent="center" alignItems="center" height="100%">
				<CircularProgress />
			</Box>
		);
	}

	if (error && !hierarchy) {
		return (
			<Box p={2}>
				<Typography color="error">{error}</Typography>
			</Box>
		);
	}

	if (!hierarchy) {
		return (
			<Box p={2}>
				<Typography>No data available.</Typography>
			</Box>
		);
	}

	return (
		<Box p={2} display="flex" flexDirection="column" gap={2}>
			<AgentData agent={hierarchy} totalResources={totalResources} />
			<Divider />
			{renderResourceGroups ? (
				renderResourceGroups({ resourceGroups })
			) : (
				<ResourceGroupsData resourceGroups={resourceGroups} />
			)}
			<Divider />
			<ResourcesTable
				resources={rogueResources}
				onResourceClick={onRogueResourceClick}
				showOsType={true}
			/>
		</Box>
	);
};

export default React.memo(WelcomeView);
