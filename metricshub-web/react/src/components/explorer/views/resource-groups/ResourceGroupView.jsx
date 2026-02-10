import * as React from "react";
import { useSelector } from "react-redux";
import { Box, CircularProgress, Divider, Typography } from "@mui/material";
import {
	selectExplorerHierarchy,
	selectExplorerLoading,
	selectExplorerError,
} from "../../../../store/slices/explorer-slice";
import EntityHeader from "../common/EntityHeader";
import MetricsAccordion from "../common/MetricsAccordion";
import ResourcesTable from "../common/ResourcesTable";

/**
 * Resource group focused page. Mirrors the welcome page behaviour but
 * focuses on a single resource group.
 *
 * @param {object} props - Component props
 * @param {string} [props.resourceGroupName] - The name of the resource group
 * @param {(resource: unknown) => void} [props.onResourceClick] - Callback for resource click
 * @returns {JSX.Element | null}
 */
const ResourceGroupView = ({ resourceGroupName, onResourceClick }) => {
	const hierarchy = useSelector(selectExplorerHierarchy);
	const loading = useSelector(selectExplorerLoading);
	const error = useSelector(selectExplorerError);

	// Memoize computed values (hooks must be called before any conditional returns)
	const decodedName = React.useMemo(
		() => (resourceGroupName ? decodeURIComponent(resourceGroupName) : null),
		[resourceGroupName],
	);

	const resourceGroups = React.useMemo(
		() => hierarchy?.resourceGroups || [],
		[hierarchy?.resourceGroups],
	);

	const group = React.useMemo(() => {
		if (!hierarchy || resourceGroups.length === 0) return null;
		if (decodedName) {
			return resourceGroups.find((g) => g.name === decodedName || g.id === decodedName) || null;
		}
		return resourceGroups[0] || null;
	}, [hierarchy, decodedName, resourceGroups]);

	const metrics = React.useMemo(() => group?.metrics || [], [group?.metrics]);
	const resources = React.useMemo(() => group?.resources || [], [group?.resources]);

	const hasMetrics = React.useMemo(() => {
		if (!metrics) return false;
		if (Array.isArray(metrics)) {
			return metrics.length > 0;
		}
		if (typeof metrics === "object") {
			return Object.keys(metrics).length > 0;
		}
		return false;
	}, [metrics]);

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

	if (!group) {
		return (
			<Box p={2}>
				<Typography>No resource groups available.</Typography>
			</Box>
		);
	}

	return (
		<Box p={2} display="flex" flexDirection="column" gap={2}>
			<EntityHeader
				title={group.name || group.id}
				iconType="resource-group"
				attributes={group.attributes}
			/>
			{hasMetrics && <MetricsAccordion metrics={metrics} />}
			<Divider />
			<ResourcesTable resources={resources} onResourceClick={onResourceClick} />
		</Box>
	);
};

export default React.memo(ResourceGroupView);
