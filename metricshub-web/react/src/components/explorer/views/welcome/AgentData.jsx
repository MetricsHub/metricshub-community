import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import DashboardTable from "../common/DashboardTable";
import { renderAttributesRows } from "../common/ExplorerTableHelpers";
import { emptyStateCellSx, sectionTitleSx } from "../common/table-styles";

/**
 * Render rows for agent metrics.
 * @param {Record<string, { value?: unknown, unit?: string, lastUpdate?: string }>} metrics
 * @returns {JSX.Element[] | JSX.Element}
 */
const renderMetricsRows = (metrics) => {
	const entries = Object.entries(metrics ?? {});
	if (entries.length === 0) {
		return (
			<TableRow>
				<TableCell colSpan={4} sx={emptyStateCellSx}>
					No metrics
				</TableCell>
			</TableRow>
		);
	}

	return entries.map(([name, m]) => {
		const metric = m || {};
		return (
			<TableRow key={name} hover>
				<TableCell>{name}</TableCell>
				<TableCell align="right">{metric.value ?? ""}</TableCell>
				<TableCell>{metric.unit === "1" ? "%" : (metric.unit ?? "")}</TableCell>
				<TableCell align="right">{metric.lastUpdate ?? ""}</TableCell>
			</TableRow>
		);
	});
};

/**
 * Agent header, attributes and metrics section for the welcome page.
 * @param {{ agent: { attributes?: Record<string, unknown>, metrics?: Record<string, any> } | null, totalResources?: number }} props
 * @returns {JSX.Element | null}
 */
const AgentData = ({ agent, totalResources }) => {
	const attributes = React.useMemo(() => agent?.attributes ?? {}, [agent]);
	const version = attributes.version || attributes.cc_version || "";
	const title = "MetricsHub Community";
	const hasAttributes = Object.keys(attributes).length > 0;
	const hasMetrics = agent?.metrics && Object.keys(agent.metrics).length > 0;

	if (!agent) {
		return null;
	}

	return (
		<Box display="flex" flexDirection="column" gap={3}>
			<Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
				<Typography
					variant="h4"
					gutterBottom
					sx={{ display: "flex", alignItems: "center", gap: 0.75 }}
				>
					<NodeTypeIcons type="agent" fontSize="large" />
					{title}
				</Typography>
				{version && <Typography variant="subtitle1">Version: {version}</Typography>}
				{typeof totalResources === "number" && (
					<Typography variant="subtitle1">Total resources: {totalResources}</Typography>
				)}
			</Box>

			{hasAttributes && (
				<Box>
					<Typography variant="h6" gutterBottom sx={sectionTitleSx}>
						Attributes
					</Typography>
					<DashboardTable>
						<TableHead>
							<TableRow>
								<TableCell>Key</TableCell>
								<TableCell align="left">Value</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>{renderAttributesRows(attributes)}</TableBody>
					</DashboardTable>
				</Box>
			)}

			{hasMetrics && (
				<Box>
					<Typography variant="h6" gutterBottom sx={sectionTitleSx}>
						Metrics
					</Typography>
					<DashboardTable>
						<TableHead>
							<TableRow>
								<TableCell>Name</TableCell>
								<TableCell align="right">Value</TableCell>
								<TableCell>Unit</TableCell>
								<TableCell align="right">Last Update</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>{renderMetricsRows(agent.metrics)}</TableBody>
					</DashboardTable>
				</Box>
			)}
		</Box>
	);
};

export default AgentData;
