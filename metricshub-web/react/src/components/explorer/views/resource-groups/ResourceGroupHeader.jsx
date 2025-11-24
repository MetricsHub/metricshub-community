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
import { renderAttributesRows } from "../common/ExplorerTableHelpers";

const ResourceGroupHeader = ({ group }) => {
	if (!group) return null;

	const attributes = group.attributes ?? {};

	return (
		<Box display="flex" flexDirection="column" gap={3}>
			<Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
				<Typography
					variant="h4"
					gutterBottom
					sx={{ display: "flex", alignItems: "center", gap: 0.5 }}
				>
					<NodeTypeIcons type="resource-group" />
					{group.name || group.id}
				</Typography>
			</Box>

			<Box>
				<Typography variant="h6" gutterBottom>
					Attributes
				</Typography>
				<Paper variant="outlined">
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Key</TableCell>
								<TableCell>Value</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>{renderAttributesRows(attributes)}</TableBody>
					</Table>
				</Paper>
			</Box>
		</Box>
	);
};

export default ResourceGroupHeader;
