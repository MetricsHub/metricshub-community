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

const ResourceGroupResources = ({ resources, onResourceClick }) => {
	const allResources = React.useMemo(() => resources ?? [], [resources]);

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
						</TableRow>
					</TableHead>
					<TableBody>
						{allResources.length === 0 ? (
							<TableRow>
								<TableCell colSpan={3}>No resources</TableCell>
							</TableRow>
						) : (
							allResources.map((resource) => (
								<ResourceRow key={resource.key} resource={resource} onClick={onResourceClick} />
							))
						)}
					</TableBody>
				</Table>
			</Paper>
		</Box>
	);
};

export default ResourceGroupResources;
