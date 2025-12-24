import * as React from "react";
import { Typography, Box } from "@mui/material";
import EntityHeader from "../common/EntityHeader";
import MetricsAccordion from "../common/MetricsAccordion";

/**
 * Agent header, attributes and metrics section for the welcome page.
 *
 * @param {object} props - Component props
 * @param {{ attributes?: Record<string, unknown>, metrics?: Record<string, any> } | null} props.agent - The agent object
 * @param {number} [props.totalResources] - Total number of resources
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
				<Box component="img" src="/windows.svg" alt="Windows" sx={{ width: 40, height: 40 }} />
			);
		} else if (lower.includes("linux")) {
			action = <Box component="img" src="/linux.svg" alt="Linux" sx={{ width: 60, height: 60 }} />;
		}
	}

	return (
		<Box display="flex" flexDirection="column" gap={2}>
			<EntityHeader title={title} iconType="agent" attributes={attributes} action={action}>
				{version && <Typography variant="subtitle1">Version: {version}</Typography>}
				{typeof totalResources === "number" && (
					<Typography variant="subtitle1">Total resources: {totalResources}</Typography>
				)}
			</EntityHeader>
			{hasMetrics && <MetricsAccordion metrics={agent.metrics} />}
		</Box>
	);
};

export default React.memo(AgentData);
