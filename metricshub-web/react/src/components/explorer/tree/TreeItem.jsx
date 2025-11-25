import * as React from "react";
import { TreeItem } from "@mui/x-tree-view";
import { Box, Typography } from "@mui/material";
import NodeTypeIcons from "./icons/NodeTypeIcons";

/**
 * Presentational label used by explorer tree items.
 *
 * @param {Object} props
 * @param {string} props.name Display name of the node.
 * @param {string} [props.type] Backend node type.
 * @param {boolean} props.isFolder Whether the node represents a folder/branch.
 * @param {React.ReactNode} [props.right] Optional right-aligned adornment.
 */
const ExplorerTreeItemLabel = React.memo(function ExplorerTreeItemLabel({
	name,
	type,
	isFolder,
	right,
}) {
	return (
		<Box
			sx={{
				width: "100%",
				display: "flex",
				alignItems: "center",
				justifyContent: "space-between",
				pr: 1,
			}}
		>
			<Box sx={{ display: "flex", alignItems: "center", minWidth: 0 }}>
				<NodeTypeIcons type={type} />
				<Typography
					component="span"
					noWrap
					title={name}
					sx={{ fontWeight: isFolder ? 500 : 470, overflow: "hidden", textOverflow: "ellipsis" }}
				>
					{name}
				</Typography>
			</Box>
			{right || null}
		</Box>
	);
});

/**
 * Renders a single explorer tree item and its children.
 *
 * @param {Object} props
 * @param {{ id:string, name:string, type?:string, children?:any[], isExpandable?:boolean }} props.node Normalized explorer node.
 * @param {React.ReactNode} [props.right] Optional right-aligned adornment.
 */
const ExplorerTreeItem = React.memo(function ExplorerTreeItem({ node, right }) {
	const isFolder = !!node.isExpandable;

	return (
		<TreeItem
			itemId={node.id}
			label={
				<ExplorerTreeItemLabel
					name={node.name}
					type={node.type}
					isFolder={isFolder}
					right={right}
				/>
			}
			slotProps={{ content: { sx: { width: "100%" } } }}
		>
			{isFolder &&
				Array.isArray(node.children) &&
				node.children.map((c) => <ExplorerTreeItem key={c.id} node={c} />)}
		</TreeItem>
	);
});

export default ExplorerTreeItem;
