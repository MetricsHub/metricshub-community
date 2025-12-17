import * as React from "react";
import { useDispatch } from "react-redux";
import { Box, Typography } from "@mui/material";
import { prettifyKey } from "../../../../utils/text-prettifier";
import { formatRelativeTime } from "../../../../utils/formatters";
import MonitorsHeader from "./components/MonitorsHeader";
import ConnectorAccordion from "./components/ConnectorAccordion";
import { setMonitorExpanded } from "../../../../store/slices/explorer-slice";
import { useScrollToHash } from "../../../../hooks/use-scroll-to-hash";

/**
 * Monitors section displayed inside the Resource page.
 *
 * - Shows H2 "Monitors".
 * - For each monitor type, if instances count <= 20, lists all instances
 *   and their metrics in a table.
 * - If instances count > 20, shows a badge with the count that would
 *   redirect to a dedicated monitor type page (navigation not wired yet).
 * - Shows a "last updated" label based on when the resource
 *   data was last fetched (provided by parent).
 *
 * @param {{ connectors?: any[], lastUpdatedAt?: number | string | Date, resourceId?: string, resourceName?: string, resourceGroupName?: string, maxNameLength?: number }} props
 */
const MonitorsView = ({
	connectors,
	lastUpdatedAt,
	resourceId,
	resourceName,
	resourceGroupName,
	maxNameLength: providedMaxNameLength,
}) => {
	const dispatch = useDispatch();
	// Force re-render every 5 seconds to update "last updated" relative time
	const [_now, setNow] = React.useState(Date.now());

	React.useEffect(() => {
		const interval = setInterval(() => setNow(Date.now()), 5000);
		return () => clearInterval(interval);
	}, []);

	const safeConnectors = React.useMemo(
		() => (Array.isArray(connectors) ? connectors : []),
		[connectors],
	);

	const lastUpdatedLabel = React.useMemo(
		() => (!lastUpdatedAt ? "Never" : formatRelativeTime(lastUpdatedAt)),
		[lastUpdatedAt],
	);

	// Check if there are any monitors across all connectors
	const hasAnyMonitors = React.useMemo(
		() =>
			safeConnectors.some(
				(connector) => Array.isArray(connector?.monitors) && connector.monitors.length > 0,
			),
		[safeConnectors],
	);

	// Calculate max length of connector names for alignment
	const maxNameLength = React.useMemo(() => {
		if (providedMaxNameLength !== undefined) return providedMaxNameLength;
		return safeConnectors.reduce((max, connector) => {
			const name = prettifyKey(connector.name || "");
			return Math.max(max, name.length);
		}, 0);
	}, [safeConnectors, providedMaxNameLength]);

	// Calculate max length of monitor counts for alignment
	const maxCountLength = React.useMemo(() => {
		return safeConnectors.reduce((max, connector) => {
			const count = (connector.monitors || []).length;
			return Math.max(max, count.toString().length);
		}, 0);
	}, [safeConnectors]);

	const highlightedId = useScrollToHash();

	React.useEffect(() => {
		if (highlightedId && resourceId) {
			dispatch(setMonitorExpanded({ resourceId, monitorName: highlightedId, expanded: true }));
		}
	}, [highlightedId, resourceId, dispatch]);

	if (!hasAnyMonitors) {
		return (
			<Box>
				<MonitorsHeader lastUpdatedLabel={lastUpdatedLabel} />
				<Typography variant="body2">No connectors available for this resource.</Typography>
			</Box>
		);
	}

	return (
		<Box display="flex" flexDirection="column">
			<MonitorsHeader lastUpdatedLabel={lastUpdatedLabel} />

			{safeConnectors.map((connector, connectorIndex) => (
				<ConnectorAccordion
					key={connector.name || `connector-${connectorIndex}`}
					connector={connector}
					connectorIndex={connectorIndex}
					resourceId={resourceId}
					resourceName={resourceName}
					resourceGroupName={resourceGroupName}
					maxNameLength={maxNameLength}
					maxCountLength={maxCountLength}
					highlightedId={highlightedId}
					isLast={connectorIndex === safeConnectors.length - 1}
				/>
			))}
		</Box>
	);
};

export default React.memo(MonitorsView);
