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
import EntityHeader from "../common/EntityHeader";
import MetricsAccordion from "../common/MetricsAccordion";
import MonitorsView from "../monitors/MonitorsView";
import HoverInfo from "../monitors/components/HoverInfo";
import WarningIcon from "@mui/icons-material/Warning";
import { debounce } from "@mui/material";
import { getMetricValue } from "../../../../utils/metrics-helper";
import { prettifyKey } from "../../../../utils/text-prettifier";
import { useResourceFetcher } from "../../../../hooks/use-resource-fetcher";

/**
 * Single resource focused page.
 *
 * @param {{
 *   resourceName?: string,
 *   resourceGroupName?: string,
 *   isPaused?: boolean
 * }} props
 * @returns {JSX.Element | null}
 */
const ResourceView = ({ resourceName, resourceGroupName, isPaused }) => {
	const dispatch = useDispatch();
	const hierarchy = useSelector(selectExplorerHierarchy);
	const loading = useSelector(selectExplorerLoading);
	const error = useSelector(selectExplorerError);
	const currentResource = useSelector(selectCurrentResource);
	const resourceLoading = useSelector(selectResourceLoading);
	const resourceError = useSelector(selectResourceError);
	const [lastUpdatedAt, setLastUpdatedAt] = React.useState(null);
	const rootRef = React.useRef(null);

	const decodedName = React.useMemo(
		() => (resourceName ? decodeURIComponent(resourceName) : null),
		[resourceName],
	);
	const decodedGroup = React.useMemo(
		() => (resourceGroupName ? decodeURIComponent(resourceGroupName) : null),
		[resourceGroupName],
	);

	const handleFetch = React.useCallback(() => {
		setLastUpdatedAt(Date.now());
	}, []);

	useResourceFetcher({
		resourceName,
		resourceGroupName,
		isPaused,
		onFetch: handleFetch,
	});

	// Scroll restoration logic
	const resourceId = React.useMemo(
		() => currentResource?.id || currentResource?.name,
		[currentResource?.id, currentResource?.name],
	);
	const uiState = useSelector((state) =>
		resourceId ? selectResourceUiState(resourceId)(state) : null,
	);
	const savedScrollTop = uiState?.scrollTop || 0;

	/**
	 * Restore scroll position when resource changes.
	 * Debounce scroll event to save position to Redux.
	 */
	const handleScroll = React.useCallback(
		(e) => {
			if (resourceId) {
				dispatch(setResourceScrollTop({ resourceId, scrollTop: e.target.scrollTop }));
			}
		},
		[dispatch, resourceId],
	);

	const debouncedHandleScroll = React.useMemo(() => debounce(handleScroll, 300), [handleScroll]);

	React.useLayoutEffect(() => {
		if (!resourceId || !rootRef.current) return;
		const scrollParent = rootRef.current.parentElement;
		if (!scrollParent) return;

		if (savedScrollTop > 0) {
			scrollParent.scrollTop = savedScrollTop;
		}

		scrollParent.addEventListener("scroll", debouncedHandleScroll);
		return () => {
			scrollParent.removeEventListener("scroll", debouncedHandleScroll);
			debouncedHandleScroll.clear();
		};
	}, [resourceId, savedScrollTop, debouncedHandleScroll]);

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

	const resource = React.useMemo(
		() => currentResource || hierarchyResource,
		[currentResource, hierarchyResource],
	);

	const connectors = React.useMemo(() => resource?.connectors || [], [resource?.connectors]);
	const failedConnectors = React.useMemo(() => {
		if (!connectors || connectors.length === 0) return [];
		return connectors.filter((c) => {
			const statusMetric = c.metrics?.["metricshub.connector.status"];
			const statusValue = getMetricValue(statusMetric);
			return statusValue === "failed";
		});
	}, [connectors]);

	const metrics = React.useMemo(() => {
		if (!resource) return [];
		let resourceMetrics = resource.metrics;
		// If current resource has no metrics, try to use metrics from hierarchy
		if (
			(!resourceMetrics ||
				(Array.isArray(resourceMetrics) && resourceMetrics.length === 0) ||
				(typeof resourceMetrics === "object" && Object.keys(resourceMetrics).length === 0)) &&
			hierarchyResource?.metrics
		) {
			resourceMetrics = hierarchyResource.metrics;
		}
		return resourceMetrics || [];
	}, [resource, hierarchyResource?.metrics]);

	const failedConnectorsDescription = React.useMemo(() => {
		if (failedConnectors.length === 0) return "";
		return failedConnectors.map((c) => c.name || c.id || "Unknown").join(", ");
	}, [failedConnectors]);

	const resourceTitle = React.useMemo(
		() => (
			<Box component="span" display="flex" alignItems="center" gap={1}>
				{resource?.id || resource?.key || resource?.name || ""}
				{failedConnectors.length > 0 && (
					<HoverInfo
						title="Warning"
						description={`The following connectors have failed: ${failedConnectorsDescription}`}
						sx={{ display: "flex", alignItems: "center" }}
					>
						<WarningIcon color="warning" />
					</HoverInfo>
				)}
			</Box>
		),
		[
			resource?.id,
			resource?.key,
			resource?.name,
			failedConnectors.length,
			failedConnectorsDescription,
		],
	);

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

	// Calculate max length for alignment between Metrics and Connectors
	const maxNameLength = React.useMemo(() => {
		let max = 0;
		if (hasMetrics) {
			max = "Metrics".length;
		}
		if (connectors && connectors.length > 0) {
			const connectorsMax = connectors.reduce((m, c) => {
				const name = prettifyKey(c.name || "");
				return Math.max(m, name.length);
			}, 0);
			max = Math.max(max, connectorsMax);
		}
		return max;
	}, [hasMetrics, connectors]);

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

	return (
		<Box ref={rootRef} p={2} display="flex" flexDirection="column" gap={2}>
			<EntityHeader title={resourceTitle} iconType="resource" attributes={resource.attributes} />
			{hasMetrics && <MetricsAccordion metrics={metrics} maxNameLength={maxNameLength} />}
			<Divider />
			<MonitorsView
				connectors={connectors}
				lastUpdatedAt={lastUpdatedAt}
				resourceId={resourceId}
				resourceName={decodedName}
				resourceGroupName={decodedGroup}
				maxNameLength={maxNameLength}
			/>
		</Box>
	);
};

export default ResourceView;
