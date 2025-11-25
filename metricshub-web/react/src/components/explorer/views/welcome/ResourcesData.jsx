import * as React from "react";
import {
	Box,
	Typography,
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableRow,
	Paper,
} from "@mui/material";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";

/**
 * Render a single resource row.
 * @param {{ resource: { name: string, attributes?: Record<string, unknown> }, onClick?: (resource:any) => void }} props
 * @returns {JSX.Element}
 */
const ResourceRow = React.memo(function ResourceRow({ resource, onClick }) {
	const attrs = resource.attributes ?? {};
	return (
		<TableRow
			hover
			sx={{ cursor: onClick ? "pointer" : "default" }}
			onClick={() => onClick && onClick(resource)}
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
			<Typography
				variant="h6"
				gutterBottom
				sx={{ display: "flex", alignItems: "center", gap: 0.5 }}
			>
				<NodeTypeIcons type="resource" />
				Resources
			</Typography>
			<Paper variant="outlined">
				<Table size="small">
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
								<TableCell colSpan={4}>No resources</TableCell>
							</TableRow>
						) : (
							allResources.map((resource) => (
								<ResourceRow key={resource.name} resource={resource} onClick={onResourceClick} />
							))
						)}
					</TableBody>
				</Table>
			</Paper>
		</Box>
	);
};

export default ResourcesData;
