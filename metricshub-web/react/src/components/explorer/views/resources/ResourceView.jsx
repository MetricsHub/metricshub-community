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

	// Find resource in hierarchy
	let hierarchyResource = null;
	if (hierarchy && decodedName) {
		const resourceGroups = hierarchy.resourceGroups || [];
		const topLevelResources = hierarchy.resources || [];
		if (decodedGroup) {
			const group =
				resourceGroups.find((g) => g.name === decodedGroup || g.id === decodedGroup) || null;
			if (group && Array.isArray(group.resources)) {
				hierarchyResource =
					group.resources.find(
						(r) => r.name === decodedName || r.id === decodedName || r.key === decodedName,
					) || null;
			}
		} else {
			hierarchyResource =
				topLevelResources.find(
					(r) => r.name === decodedName || r.id === decodedName || r.key === decodedName,
				) || null;
		}
	}

	const resource = currentResource || hierarchyResource;

	if (!resource) {
		return (
			<Box p={2}>
				<Typography>No resource selected.</Typography>
			</Box>
		);
	}

	let metrics = resource.metrics;
	// If current resource has no metrics, try to use metrics from hierarchy
	if (
		(!metrics ||
			(Array.isArray(metrics) && metrics.length === 0) ||
			(typeof metrics === "object" && Object.keys(metrics).length === 0)) &&
		hierarchyResource?.metrics
	) {
		metrics = hierarchyResource.metrics;
	}
	metrics = metrics || [];

	const connectors = resource.connectors || [];

	let hasMetrics = false;
	if (metrics) {
		if (Array.isArray(metrics)) {
			hasMetrics = metrics.length > 0;
		} else if (typeof metrics === "object") {
			hasMetrics = Object.keys(metrics).length > 0;
		}
	}

	return (
		<Box ref={rootRef} p={2} display="flex" flexDirection="column" gap={2}>
			<EntityHeader
				title={resource.id || resource.key || resource.name}
				iconType="resource"
				attributes={resource.attributes}
			/>
			<Divider />
			{hasMetrics && (
				<>
					<MetricsTable metrics={metrics} showUnit={false} showLastUpdate={false} />
					<Divider />
				</>
			)}
			<MonitorsView connectors={connectors} lastUpdatedAt={lastUpdatedAt} resourceId={resourceId} />
		</Box>
	);
};

export default ResourceView;
