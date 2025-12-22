import * as React from "react";
import { Box, Typography } from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import { sectionTitleSx, dataGridSx } from "./table-styles";

/**
 * Generic header section for an entity (Resource, Resource Group, Agent),
 * showing its title/icon and attributes.
 *
 * @param {{
 *   title: React.ReactNode,
 *   iconType?: string,
 *   attributes?: Record<string, unknown>,
 *   children?: React.ReactNode,
 *   action?: React.ReactNode
 * }} props
 * @returns {JSX.Element | null}
 */
const EntityHeader = ({ title, iconType, attributes, children, action }) => {
	const hasAttributes = React.useMemo(
		() => attributes && Object.keys(attributes).length > 0,
		[attributes],
	);

	const titleSx = React.useMemo(() => ({ display: "flex", alignItems: "center", gap: 0.5 }), []);

	const attributesTitleSx = React.useMemo(() => ({ ...sectionTitleSx, mb: 1 }), []);

	return (
		<Box display="flex" flexDirection="column" gap={3}>
			<Box display="flex" justifyContent="space-between" alignItems="flex-start">
				<Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
					<Typography variant="h4" gutterBottom sx={titleSx}>
						{iconType && <NodeTypeIcons type={iconType} fontSize="large" />}
						{title}
					</Typography>
					{children}
				</Box>
				{action && <Box>{action}</Box>}
			</Box>

			{hasAttributes && (
				<Box>
					<Typography variant="h6" sx={attributesTitleSx}>
						Attributes
					</Typography>
					<DataGrid
						rows={Object.entries(attributes).map(([key, value]) => ({
							id: key,
							key,
							value,
						}))}
						columns={[
							{ field: "key", headerName: "Key", flex: 1 },
							{ field: "value", headerName: "Value", flex: 1 },
						]}
						disableRowSelectionOnClick
						hideFooter
						autoHeight
						density="compact"
						sx={dataGridSx}
					/>
				</Box>
			)}
		</Box>
	);
};

export default React.memo(EntityHeader);
