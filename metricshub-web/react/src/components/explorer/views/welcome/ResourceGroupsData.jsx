import * as React from "react";
import { Box, Typography } from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import { sectionTitleSx, dataGridSx } from "../common/table-styles";
import TruncatedText from "../common/TruncatedText";

/**
 * Count resources in a resource group.
 *
 * @param {object} group - The resource group
 * @returns {number}
 */
const countResources = (group) => {
	const resources = group?.resources ?? [];
	return Array.isArray(resources) ? resources.length : 0;
};

/**
 * Build the display label for a resource group using its attributes.
 *
 * @param {object} group - The resource group
 * @returns {string}
 */
const buildGroupLabel = (group) => {
	const attrs = group?.attributes ?? {};
	const parts = [];
	if (attrs.env) {
		parts.push(`(env: ${attrs.env})`);
	}
	if (attrs.owner) {
		parts.push(`(owner: ${attrs.owner})`);
	}
	if (parts.length === 0) {
		return group?.name || "";
	}
	return [group?.name || "", ...parts].join(" ").trim();
};

/**
 * Table displaying resource groups handled by the agent.
 *
 * @param {object} props - Component props
 * @param {Array<{ name: string, attributes?: Record<string, unknown>, resources?: unknown[] }>} [props.resourceGroups] - List of resource groups
 * @param {(group: any) => void} [props.onResourceGroupClick] - Callback for resource group click
 * @returns {JSX.Element}
 */
const ResourceGroupsData = ({ resourceGroups, onResourceGroupClick }) => {
	// Pre-compute labels and counts for all groups to avoid recalculating in render
	const processedGroups = React.useMemo(() => {
		const groups = resourceGroups ?? [];
		return groups.map((group) => ({
			...group,
			displayLabel: buildGroupLabel(group),
			resourceCount: countResources(group),
		}));
	}, [resourceGroups]);

	// Memoized click handler factory to avoid recreating functions in map
	const handleGroupClick = React.useCallback(
		(params) => {
			if (onResourceGroupClick) {
				onResourceGroupClick(params.row);
			}
		},
		[onResourceGroupClick],
	);

	return (
		<Box>
			<Typography variant="h6" gutterBottom sx={sectionTitleSx}>
				<NodeTypeIcons type="resource-group" />
				Resource Groups
			</Typography>
			<DataGrid
				rows={processedGroups.map((g) => ({ id: g.name, ...g }))}
				columns={[
					{
						field: "displayLabel",
						headerName: "Key",
						flex: 1,
						renderCell: (params) => (
							<TruncatedText text={params.value}>{params.value}</TruncatedText>
						),
					},
					{
						field: "resourceCount",
						headerName: "Resources",
						flex: 1,
						renderCell: (params) => (
							<TruncatedText text={String(params.value)}>{params.value}</TruncatedText>
						),
					},
				]}
				onRowClick={handleGroupClick}
				disableRowSelectionOnClick
				hideFooter
				autoHeight
				density="compact"
				sx={{
					...dataGridSx,
					"& .MuiDataGrid-row": {
						cursor: onResourceGroupClick ? "pointer" : "default",
					},
				}}
			/>
		</Box>
	);
};

export default React.memo(ResourceGroupsData);
