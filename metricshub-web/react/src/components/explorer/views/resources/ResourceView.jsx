import * as React from "react";
import { useSelector, useDispatch } from "react-redux";
import { Box, CircularProgress, Divider, Typography, Button } from "@mui/material";
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
import HoverInfo from "../monitors/components/HoverInfo";
import WarningIcon from "@mui/icons-material/Warning";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import PauseIcon from "@mui/icons-material/Pause";
import { debounce } from "@mui/material";
import { getMetricValue } from "../../../../utils/metrics-helper";

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
	const [isPaused, setIsPaused] = React.useState(false);
	const rootRef = React.useRef(null);

	const decodedName = resourceName ? decodeURIComponent(resourceName) : null;
	const decodedGroup = resourceGroupName ? decodeURIComponent(resourceGroupName) : null;

	// Reset paused state when resource changes
	React.useEffect(() => {
		setIsPaused(false);
	}, [decodedName, decodedGroup]);

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

		if (isPaused) return;

		fetchData();

		const interval = setInterval(fetchData, 10000);

		return () => clearInterval(interval);
	}, [dispatch, decodedName, decodedGroup, isPaused]);

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

	// Find resource in hierarchy
	const hierarchyResource = React.useMemo(() => {
		if (!hierarchy || !decodedName) return null;
		const resourceGroups = hierarchy.resourceGroups || [];
		const topLevelResources = hierarchy.resources || [];
		if (decodedGroup) {
			const group =
				resourceGroups.find((g) => g.name === decodedGroup || g.id === decodedGroup) || null;
			if (group && Array.isArray(group.resources)) {
				return (
					group.resources.find(
						(r) => r.name === decodedName || r.id === decodedName || r.key === decodedName,
					) || null
				);
			}
		} else {
			return (
				topLevelResources.find(
					(r) => r.name === decodedName || r.id === decodedName || r.key === decodedName,
				) || null
			);
		}
		return null;
	}, [hierarchy, decodedName, decodedGroup]);

	const resource = currentResource || hierarchyResource;

	const connectors = React.useMemo(() => resource?.connectors || [], [resource]);
	const failedConnectors = React.useMemo(() => {
		return connectors.filter((c) => {
			const statusMetric = c.metrics?.["metricshub.connector.status"];
			const statusValue = getMetricValue(statusMetric);
			return statusValue === "failed";
		});
	}, [connectors]);

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

	const resourceTitle = (
		<Box component="span" display="flex" alignItems="center" gap={1}>
			{resource.id || resource.key || resource.name}
			{failedConnectors.length > 0 && (
				<HoverInfo
					title="Warning"
					description={`The following connectors have failed: ${failedConnectors
						.map((c) => c.name || c.id || "Unknown")
						.join(", ")}`}
					sx={{ display: "flex", alignItems: "center" }}
				>
					<WarningIcon color="warning" />
				</HoverInfo>
			)}
		</Box>
	);

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
				title={resourceTitle}
				iconType="resource"
				attributes={resource.attributes}
				action={
					<Button
						size="small"
						variant="contained"
						startIcon={isPaused ? <PlayArrowIcon /> : <PauseIcon />}
						onClick={() => setIsPaused(!isPaused)}
					>
						{isPaused ? "Resume Collect" : "Pause Collect"}
					</Button>
				}
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
