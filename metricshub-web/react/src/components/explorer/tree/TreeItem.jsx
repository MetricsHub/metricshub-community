import * as React from "react";
import { TreeItem } from "@mui/x-tree-view";
import { Box, Typography } from "@mui/material";
import NodeTypeIcons from "./icons/NodeTypeIcons";

/**
 * Generic explorer tree item label.
 * Displays icon + name.
 *
 * NOTE:
 * - Clicks on this label intentionally do NOT toggle expand/collapse.
 * - We stop propagation so only the built-in arrow icon controls unfolding.
 * - An optional `onLabelClick` makes it easy to later plug in
 *   "focus this node on the right side" behavior.
 */
const ExplorerTreeItemLabel = React.memo(function ExplorerTreeItemLabel({
	name,
	type,
	isFolder,
	right,
	onLabelClick,
	node,
}) {
	const handleClick = React.useCallback(
		(event) => {
			// Prevent TreeItem's default toggle-on-content-click behavior.
			// This ensures only the arrow icon expands/collapses the node.
			event.stopPropagation();
			if (onLabelClick) {
				onLabelClick(node);
			}
		},
		[onLabelClick, node],
	);

	return (
		<Box
			sx={{
				width: "100%",
				display: "flex",
				alignItems: "center",
				justifyContent: "space-between",
				pr: 1,
			}}
			onClick={handleClick}
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
 * Generic explorer tree item wrapper.
 * @param {{ node:{ id:string, name:string, type?:string, children?:any[] }, right?:React.ReactNode, onLabelClick?:(node:any) => void }} props
 */
const ExplorerTreeItem = React.memo(function ExplorerTreeItem({ node, right, onLabelClick }) {
	const isFolder = Array.isArray(node.children) && node.children.length > 0;

	// Keep the object passed into the label small & stable
	// so memoization stays effective as the tree grows.
	const labelNode = React.useMemo(
		() => ({ id: node.id, name: node.name, type: node.type }),
		[node.id, node.name, node.type],
	);

	return (
		<TreeItem
			itemId={node.id}
			label={
				<ExplorerTreeItemLabel
					name={node.name}
					type={node.type}
					isFolder={isFolder}
					right={right}
					onLabelClick={onLabelClick}
					node={labelNode}
				/>
			}
			slotProps={{ content: { sx: { width: "100%" } } }}
		>
			{isFolder &&
				node.children.map((c) => (
					<ExplorerTreeItem key={c.id} node={c} onLabelClick={onLabelClick} />
				))}
		</TreeItem>
	);
});

export default ExplorerTreeItem;
