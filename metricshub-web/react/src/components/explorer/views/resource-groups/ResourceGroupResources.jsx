import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import DashboardTable from "../common/DashboardTable";
import { emptyStateCellSx, sectionTitleSx } from "../common/table-styles";

/**
 * Single clickable resource row.
 *
 * @param {{ resource: { key: string, name: string, attributes?: Record<string, unknown> }, onClick?: (resource: any) => void }} props
 * @returns {JSX.Element}
 */
const ResourceRow = React.memo(function ResourceRow({ resource, onClick }) {
	const attrs = resource.attributes ?? {};
	return (
		<TableRow hover sx={{ cursor: "pointer" }} onClick={() => onClick && onClick(resource)}>
			<TableCell>{resource.name}</TableCell>
			<TableCell>{attrs["host.name"] ?? ""}</TableCell>
			<TableCell>{attrs["host.type"] ?? ""}</TableCell>
		</TableRow>
	);
});

/**
 * Resources table for a single resource group.
 *
 * @param {{ resources?: Array<{ key: string, name: string, attributes?: Record<string, unknown> }> | null, onResourceClick?: (resource: any) => void }} props
 * @returns {JSX.Element}
 */
const ResourceGroupResources = ({ resources, onResourceClick }) => {
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
					</TableRow>
				</TableHead>
				<TableBody>
					{allResources.length === 0 ? (
						<TableRow>
							<TableCell colSpan={3} sx={emptyStateCellSx}>
								No resources
							</TableCell>
						</TableRow>
					) : (
						allResources.map((resource) => (
							<ResourceRow key={resource.key} resource={resource} onClick={onResourceClick} />
						))
					)}
				</TableBody>
			</DashboardTable>
		</Box>
	);
};

export default ResourceGroupResources;
