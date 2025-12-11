import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import DashboardTable from "./DashboardTable";
import { emptyStateCellSx, sectionTitleSx } from "./table-styles";

/**
 * Render a single resource row.
 * @param {{ resource: { name: string, attributes?: Record<string, unknown> }, onClick?: (resource:any) => void, showOsType?: boolean }} props
 * @returns {JSX.Element}
 */
const ResourceRow = React.memo(function ResourceRow({ resource, onClick, showOsType }) {
	const attrs = React.useMemo(() => resource.attributes ?? {}, [resource.attributes]);

	const rowSx = React.useMemo(() => ({ cursor: onClick ? "pointer" : "default" }), [onClick]);

	const handleClick = React.useCallback(() => {
		if (onClick) {
			onClick(resource);
		}
	}, [onClick, resource]);

	return (
		<TableRow hover={Boolean(onClick)} sx={rowSx} onClick={onClick ? handleClick : undefined}>
			<TableCell>{resource.name}</TableCell>
			<TableCell>{attrs["host.name"] ?? ""}</TableCell>
			<TableCell>{attrs["host.type"] ?? ""}</TableCell>
			{showOsType && <TableCell>{attrs["os.type"] ?? ""}</TableCell>}
		</TableRow>
	);
});

/**
 * Table displaying resources.
 * @param {{
 *   resources?: Array<{ name: string, attributes?: Record<string, unknown> }>,
 *   onResourceClick?: (resource:any) => void,
 *   showOsType?: boolean
 * }} props
 * @returns {JSX.Element}
 */
const ResourcesTable = ({ resources, onResourceClick, showOsType = false }) => {
	const allResources = React.useMemo(
		() => (Array.isArray(resources) ? resources : []),
		[resources],
	);

	const hasResources = React.useMemo(() => allResources.length > 0, [allResources.length]);

	const emptyStateColSpan = React.useMemo(() => (showOsType ? 4 : 3), [showOsType]);

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
						{showOsType && <TableCell>os.type</TableCell>}
					</TableRow>
				</TableHead>
				<TableBody>
					{!hasResources ? (
						<TableRow>
							<TableCell colSpan={emptyStateColSpan} sx={emptyStateCellSx}>
								No resources
							</TableCell>
						</TableRow>
					) : (
						allResources.map((resource) => (
							<ResourceRow
								key={resource.name || resource.key}
								resource={resource}
								onClick={onResourceClick}
								showOsType={showOsType}
							/>
						))
					)}
				</TableBody>
			</DashboardTable>
		</Box>
	);
};

export default React.memo(ResourcesTable);
