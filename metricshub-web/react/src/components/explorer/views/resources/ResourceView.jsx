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
	selectResourceUiState,
	setResourceScrollTop,
} from "../../../../store/slices/explorer-slice";
import {
	fetchTopLevelResource,
	fetchGroupedResource,
} from "../../../../store/thunks/explorer-thunks";
import EntityHeader from "../common/EntityHeader";
import MetricsTable from "../common/MetricsTable";
import MonitorsView from "../monitors/MonitorsView";
import { debounce } from "@mui/material";

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
	const [lastUpdatedAt, setLastUpdatedAt] = React.useState(null);
	const rootRef = React.useRef(null);

	const decodedName = resourceName ? decodeURIComponent(resourceName) : null;
	const decodedGroup = resourceGroupName ? decodeURIComponent(resourceGroupName) : null;

	React.useEffect(() => {
		if (!decodedName) return;

		const fetchData = () => {
			if (decodedGroup) {
				dispatch(fetchGroupedResource({ groupName: decodedGroup, resourceName: decodedName }));
			} else {
				dispatch(fetchTopLevelResource({ resourceName: decodedName }));
			}
			// Update "last updated" every time we trigger a resource fetch
			setLastUpdatedAt(Date.now());
		};

		fetchData();
		const interval = setInterval(fetchData, 10000);

		return () => clearInterval(interval);
	}, [dispatch, decodedName, decodedGroup]);

	// Scroll restoration logic
	const resourceId = currentResource?.id || currentResource?.name;
	const uiState = useSelector((state) =>
		resourceId ? selectResourceUiState(resourceId)(state) : null,
	);
	const savedScrollTop = uiState?.scrollTop || 0;

	/**
	 * Restore scroll position when resource changes.
	 * Debounce scroll event to save position to Redux.
	 */
	React.useLayoutEffect(() => {
		if (!resourceId || !rootRef.current) return;
		const scrollParent = rootRef.current.parentElement;
		if (!scrollParent) return;

		if (savedScrollTop > 0) {
			scrollParent.scrollTop = savedScrollTop;
		}

		const handleScroll = debounce((e) => {
			dispatch(setResourceScrollTop({ resourceId, scrollTop: e.target.scrollTop }));
		}, 300);

		scrollParent.addEventListener("scroll", handleScroll);
		return () => {
			scrollParent.removeEventListener("scroll", handleScroll);
			handleScroll.clear();
		};
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [resourceId]); // Only re-run when resource changes

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

	// Fallback: try to find resource in hierarchy if currentResource is not yet set
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
	const hasMetrics = metrics.length > 0;

	return (
		<Box ref={rootRef} p={2} display="flex" flexDirection="column" gap={4}>
			<EntityHeader
				title={resource.id || resource.key || resource.name}
				iconType="resource"
				attributes={resource.attributes}
			/>
			<Divider />
			{hasMetrics && (
				<>
					<MetricsTable metrics={metrics} />
					<Divider />
				</>
			)}
			<MonitorsView connectors={connectors} lastUpdatedAt={lastUpdatedAt} resourceId={resourceId} />
		</Box>
	);
};

export default ResourceView;
