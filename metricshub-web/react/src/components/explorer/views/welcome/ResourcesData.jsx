import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import DashboardTable from "../common/DashboardTable";
import { emptyStateCellSx, sectionTitleSx } from "../common/table-styles";

/**
 * Render a single resource row.
 * @param {{ resource: { name: string, attributes?: Record<string, unknown> }, onClick?: (resource:any) => void }} props
 * @returns {JSX.Element}
 */
const ResourceRow = React.memo(function ResourceRow({ resource, onClick }) {
	const attrs = resource.attributes ?? {};
	return (
		<TableRow
			hover={Boolean(onClick)}
			sx={{ cursor: onClick ? "pointer" : "default" }}
			onClick={onClick ? () => onClick(resource) : undefined}
		>
			<TableCell>{resource.name}</TableCell>
			<TableCell>{attrs["host.name"] ?? ""}</TableCell>
			<TableCell>{attrs["host.type"] ?? ""}</TableCell>
			<TableCell>{attrs["os.type"] ?? ""}</TableCell>
		</TableRow>
	);
});

/**
 * Table displaying rogue resources (not attached to any resource group).
 * @param {{ resources?: Array<{ name: string, attributes?: Record<string, unknown> }>, onResourceClick?: (resource:any) => void }} props
 * @returns {JSX.Element}
 */
const ResourcesData = ({ resources, onResourceClick }) => {
	const allResources = React.useMemo(
		() => (Array.isArray(resources) ? resources : []),
		[resources],
	);

	return (
		<Box>
			<Typography variant="h6" gutterBottom sx={sectionTitleSx}>
				<NodeTypeIcons type="resource" />
				Resources
			</Typography>
			<DashboardTable>
				<TableHead>
					<TableRow>
						<TableCell>Key</TableCell>
						<TableCell>host.name</TableCell>
						<TableCell>host.type</TableCell>
						<TableCell>os.type</TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{allResources.length === 0 ? (
						<TableRow>
							<TableCell colSpan={4} sx={emptyStateCellSx}>
								No resources
							</TableCell>
						</TableRow>
					) : (
						allResources.map((resource) => (
							<ResourceRow key={resource.name} resource={resource} onClick={onResourceClick} />
						))
					)}
				</TableBody>
			</DashboardTable>
		</Box>
	);
};

export default ResourcesData;
