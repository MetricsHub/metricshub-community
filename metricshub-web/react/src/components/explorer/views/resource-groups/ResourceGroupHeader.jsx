import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import DashboardTable from "../common/DashboardTable";
import { renderAttributesRows } from "../common/ExplorerTableHelpers";
import { sectionTitleSx } from "../common/table-styles";

/**
 * Header section for a single resource group, showing its name and attributes.
 *
 * @param {{ group?: any }} props
 * @returns {JSX.Element | null}
 */
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
				<Typography variant="h6" gutterBottom sx={sectionTitleSx}>
					Attributes
				</Typography>
				<DashboardTable>
					<TableHead>
						<TableRow>
							<TableCell>Key</TableCell>
							<TableCell>Value</TableCell>
						</TableRow>
					</TableHead>
					<TableBody>{renderAttributesRows(attributes)}</TableBody>
				</DashboardTable>
			</Box>
		</Box>
	);
};

export default ResourceGroupHeader;
