import * as React from "react";
import { Box, Typography, TableBody, TableCell, TableHead, TableRow } from "@mui/material";
import NodeTypeIcons from "../../tree/icons/NodeTypeIcons";
import DashboardTable from "./DashboardTable";
import { emptyStateCellSx, sectionTitleSx, truncatedCellSx } from "./table-styles";
import TruncatedText from "./TruncatedText";

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
			<TableCell sx={truncatedCellSx}>
				<TruncatedText text={resource.name}>{resource.name}</TruncatedText>
			</TableCell>
			<TableCell sx={truncatedCellSx}>
				<TruncatedText text={attrs["host.name"] ?? ""}>{attrs["host.name"] ?? ""}</TruncatedText>
			</TableCell>
			<TableCell sx={truncatedCellSx}>
				<TruncatedText text={attrs["host.type"] ?? ""}>{attrs["host.type"] ?? ""}</TruncatedText>
			</TableCell>
			{showOsType && (
				<TableCell sx={truncatedCellSx}>
					<TruncatedText text={attrs["os.type"] ?? ""}>{attrs["os.type"] ?? ""}</TruncatedText>
				</TableCell>
			)}
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
	const colWidth = React.useMemo(() => (showOsType ? "25%" : "33.33%"), [showOsType]);

	return (
		<Box>
			<Typography variant="h6" gutterBottom sx={sectionTitleSx}>
				<NodeTypeIcons type="resource" />
				Resources
			</Typography>
			<DashboardTable>
				<TableHead>
					<TableRow>
						<TableCell sx={{ width: colWidth }}>Key</TableCell>
						<TableCell sx={{ width: colWidth }}>host.name</TableCell>
						<TableCell sx={{ width: colWidth }}>host.type</TableCell>
						{showOsType && <TableCell sx={{ width: colWidth }}>os.type</TableCell>}
					</TableRow>
				</TableHead>
				<TableBody>
					{!hasResources ? (
						<TableRow>
							<TableCell
								colSpan={emptyStateColSpan}
								sx={{ ...emptyStateCellSx, textAlign: "left" }}
							>
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
