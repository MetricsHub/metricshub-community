import * as React from "react";
import { Typography } from "@mui/material";
import EntityHeader from "../common/EntityHeader";
import MetricsTable from "../common/MetricsTable";

/**
 * Agent header, attributes and metrics section for the welcome page.
 * @param {{ agent: { attributes?: Record<string, unknown>, metrics?: Record<string, any> } | null, totalResources?: number }} props
 * @returns {JSX.Element | null}
 */
const AgentData = ({ agent, totalResources }) => {
	const attributes = React.useMemo(() => agent?.attributes ?? {}, [agent]);
	const version = attributes.version || attributes.cc_version || "";
	const title = "MetricsHub Community";
	const hasMetrics = agent?.metrics && Object.keys(agent.metrics).length > 0;

	if (!agent) {
		return null;
	}

	return (
		<EntityHeader
			title={title}
			iconType="agent"
			attributes={attributes}
		>
			{version && <Typography variant="subtitle1">Version: {version}</Typography>}
			{typeof totalResources === "number" && (
				<Typography variant="subtitle1">Total resources: {totalResources}</Typography>
			)}
			{hasMetrics && (
				<MetricsTable metrics={agent.metrics} showUnit={true} showLastUpdate={true} />
			)}
		</EntityHeader>
	);
};

export default AgentData;
