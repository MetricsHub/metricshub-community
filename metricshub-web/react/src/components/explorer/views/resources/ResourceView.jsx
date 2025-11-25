import * as React from "react";
import { useSelector, useDispatch } from "react-redux";
import { Box, CircularProgress, Divider, Typography } from "@mui/material";
import {
	selectExplorerHierarchy,
	selectExplorerLoading,
	selectExplorerError,
	selectCurrentResource,
	selectResourceLoading,
	selectResourceError,
} from "../../../../store/slices/explorer-slice";
import {
	fetchTopLevelResource,
	fetchGroupedResource,
} from "../../../../store/thunks/explorer-thunks";
import ResourceHeader from "./ResourceHeader";
import ResourceMetrics from "./ResourceMetrics";
import MonitorsView from "../monitors/MonitorsView";

/**
 * Single resource focused page.
 *
 * @param {{
 *   resourceName?: string,
 *   resourceGroupName?: string,
 * }} props
 * @returns {JSX.Element | null}
 */
const ResourceView = ({ resourceName, resourceGroupName }) => {
	const dispatch = useDispatch();
	const hierarchy = useSelector(selectExplorerHierarchy);
	const loading = useSelector(selectExplorerLoading);
	const error = useSelector(selectExplorerError);
	const currentResource = useSelector(selectCurrentResource);
	const resourceLoading = useSelector(selectResourceLoading);
	const resourceError = useSelector(selectResourceError);

	const decodedName = resourceName ? decodeURIComponent(resourceName) : null;
	const decodedGroup = resourceGroupName ? decodeURIComponent(resourceGroupName) : null;

	React.useEffect(() => {
		if (!decodedName) return;
		if (decodedGroup) {
			dispatch(fetchGroupedResource({ groupName: decodedGroup, resourceName: decodedName }));
		} else {
			dispatch(fetchTopLevelResource({ resourceName: decodedName }));
		}
	}, [dispatch, decodedName, decodedGroup]);

	if ((loading && !hierarchy) || (resourceLoading && !currentResource)) {
		return (
			<Box display="flex" justifyContent="center" alignItems="center" height="100%">
				<CircularProgress />
			</Box>
		);
	}

	if ((error && !hierarchy) || (resourceError && !currentResource)) {
		return (
			<Box p={2}>
				<Typography color="error">{error}</Typography>
			</Box>
		);
	}

	if (!hierarchy && !currentResource) {
		return (
			<Box p={2}>
				<Typography>No data available.</Typography>
			</Box>
		);
	}

	let resource = currentResource;
	if (!resource && hierarchy && decodedName) {
		const resourceGroups = hierarchy.resourceGroups || [];
		const topLevelResources = hierarchy.resources || [];
		if (decodedGroup) {
			const group =
				resourceGroups.find((g) => g.name === decodedGroup || g.id === decodedGroup) || null;
			if (group && Array.isArray(group.resources)) {
				resource =
					group.resources.find(
						(r) => r.name === decodedName || r.id === decodedName || r.key === decodedName,
					) || null;
			}
		} else {
			resource =
				topLevelResources.find(
					(r) => r.name === decodedName || r.id === decodedName || r.key === decodedName,
				) || null;
		}
	}

	if (!resource) {
		return (
			<Box p={2}>
				<Typography>No resource selected.</Typography>
			</Box>
		);
	}

	const metrics = resource.metrics || [];
	const connectors = resource.connectors || [];

	return (
		<Box p={2} display="flex" flexDirection="column" gap={4}>
			<ResourceHeader resource={resource} />
			<Divider />
			<ResourceMetrics metrics={metrics} />
			<Divider />
			<MonitorsView connectors={connectors} />
		</Box>
	);
};

export default ResourceView;
