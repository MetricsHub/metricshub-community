import * as React from "react";
import { useSelector } from "react-redux";
import { Box, CircularProgress, Divider, Typography } from "@mui/material";
import {
	selectExplorerHierarchy,
	selectExplorerLoading,
	selectExplorerError,
} from "../../../../store/slices/explorer-slice";
import ResourceGroupHeader from "./ResourceGroupHeader";
import ResourceGroupMetrics from "./ResourceGroupMetrics";
import ResourceGroupResources from "./ResourceGroupResources";

/**
 * Resource group focused page. Mirrors the welcome page behaviour but
 * focuses on a single resource group.
 *
 * @param {{
 *   resourceGroupName?: string,
 *   onResourceClick?: (resource: unknown) => void,
 * }} props
 * @returns {JSX.Element | null}
 */
const ResourceGroupView = ({ resourceGroupName, onResourceClick }) => {
	const hierarchy = useSelector(selectExplorerHierarchy);
	const loading = useSelector(selectExplorerLoading);
	const error = useSelector(selectExplorerError);

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

	const resourceGroups = hierarchy.resourceGroups || [];
	const decodedName = resourceGroupName ? decodeURIComponent(resourceGroupName) : null;
	const group =
		decodedName && resourceGroups.length
			? resourceGroups.find((g) => g.name === decodedName || g.id === decodedName) || null
			: resourceGroups[0] || null;

	if (!group) {
		return (
			<Box p={2}>
				<Typography>No resource groups available.</Typography>
			</Box>
		);
	}

	const metrics = group.metrics || [];
	const resources = group.resources || [];
	const hasMetrics = metrics.length > 0;

	return (
		<Box p={2} display="flex" flexDirection="column" gap={4}>
			<ResourceGroupHeader group={group} />
			<Divider />
			{hasMetrics && (
				<>
					<ResourceGroupMetrics metrics={metrics} />
					<Divider />
				</>
			)}
			<ResourceGroupResources resources={resources} onResourceClick={onResourceClick} />
		</Box>
	);
};

export default ResourceGroupView;
