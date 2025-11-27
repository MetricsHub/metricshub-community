import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import { renderAttributesRows } from "../common/ExplorerTableHelpers";
import DashboardTable from "../common/DashboardTable";
import { sectionTitleSx } from "../common/table-styles";

/**
 * Header section for a single resource, showing its id and attributes.
 *
 * @param {{ resource?: any }} props
 * @returns {JSX.Element | null}
 */
const ResourceHeader = ({ resource }) => {
	if (!resource) return null;

	const attributes = resource.attributes ?? {};
	const id = resource.id || resource.key || resource.name;
	const hasAttributes = Object.keys(attributes).length > 0;

	return (
		<Box display="flex" flexDirection="column" gap={3}>
			<Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
				<Typography
					variant="h4"
					gutterBottom
					sx={{ display: "flex", alignItems: "center", gap: 0.5 }}
				>
					<NodeTypeIcons type="resource" fontSize="large" />
					{id}
				</Typography>
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
								<TableCell>Value</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>{renderAttributesRows(attributes)}</TableBody>
					</DashboardTable>
				</Box>
			)}
		</Box>
	);
};

export default ResourceHeader;
