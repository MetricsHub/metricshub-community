import * as React from "react";
import { Typography, Box } from "@mui/material";
import EntityHeader from "../common/EntityHeader";
import MetricsTable from "../common/MetricsTable";

/**
 * Agent header, attributes and metrics section for the welcome page.
 * @param {{ agent: { attributes?: Record<string, unknown>, metrics?: Record<string, any> } | null, totalResources?: number }} props
 * @returns {JSX.Element | null}
 */
const AgentData = ({ agent, totalResources }) => {
	const attributes = React.useMemo(() => agent?.attributes ?? {}, [agent?.attributes]);
	const version = React.useMemo(
		() => attributes.version || attributes.cc_version || "",
		[attributes.version, attributes.cc_version],
	);
	const hasMetrics = React.useMemo(
		() => !!(agent?.metrics && Object.keys(agent.metrics).length > 0),
		[agent?.metrics],
	);

	if (!agent) {
		return null;
	}

	const title = "MetricsHub Community";

	const osType = attributes["os.type"];
	let action = null;
	if (typeof osType === "string") {
		const lower = osType.toLowerCase();
		if (lower.includes("windows")) {
			action = (
				<Box component="img" src="/windows.svg" alt="Windows" sx={{ width: 60, height: 60 }} />
			);
		} else if (lower.includes("linux")) {
			action = <Box component="img" src="/linux.svg" alt="Linux" sx={{ width: 80, height: 80 }} />;
		}
	}

	return (
		<EntityHeader title={title} iconType="agent" attributes={attributes} action={action}>
			{version && <Typography variant="subtitle1">Version: {version}</Typography>}
			{typeof totalResources === "number" && (
				<Typography variant="subtitle1">Total resources: {totalResources}</Typography>
			)}
			{hasMetrics && <MetricsTable metrics={agent.metrics} showUnit={true} showLastUpdate={true} />}
		</EntityHeader>
	);
};

export default React.memo(AgentData);
