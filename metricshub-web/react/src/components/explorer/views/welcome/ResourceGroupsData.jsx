import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import DashboardTable from "../common/DashboardTable";
import { emptyStateCellSx, sectionTitleSx } from "../common/table-styles";

/**
 * Count resources in a resource group.
 * @param {{ resources?: unknown[] }} group
 * @returns {number}
 */
const countResources = (group) => {
	const resources = group?.resources ?? [];
	return Array.isArray(resources) ? resources.length : 0;
};

/**
 * Build the display label for a resource group using its attributes.
 * @param {{ name?: string, attributes?: Record<string, unknown> }} group
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
 * @param {{ resourceGroups?: Array<{ name: string, attributes?: Record<string, unknown>, resources?: unknown[] }>, onResourceGroupClick?:(group:any) => void }} props
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

	const hasGroups = React.useMemo(() => processedGroups.length > 0, [processedGroups.length]);
	const rowCursorSx = React.useMemo(
		() => ({ cursor: onResourceGroupClick ? "pointer" : "default" }),
		[onResourceGroupClick],
	);

	// Memoized click handler factory to avoid recreating functions in map
	const handleGroupClick = React.useCallback(
		(group) => {
			if (onResourceGroupClick) {
				onResourceGroupClick(group);
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
			<DashboardTable>
				<TableHead>
					<TableRow>
						<TableCell>Key</TableCell>
						<TableCell align="right">Resources</TableCell>
					</TableRow>
				</TableHead>
				<TableBody>
					{!hasGroups ? (
						<TableRow>
							<TableCell colSpan={2} sx={emptyStateCellSx}>
								No resource groups
							</TableCell>
						</TableRow>
					) : (
						processedGroups.map((g) => (
							<TableRow key={g.name} hover sx={rowCursorSx} onClick={() => handleGroupClick(g)}>
								<TableCell>{g.displayLabel}</TableCell>
								<TableCell align="right">{g.resourceCount}</TableCell>
							</TableRow>
						))
					)}
				</TableBody>
			</DashboardTable>
		</Box>
	);
};

export default React.memo(ResourceGroupsData);
