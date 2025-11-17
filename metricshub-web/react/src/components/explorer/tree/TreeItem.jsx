import * as React from "react";
import { TreeItem } from "@mui/x-tree-view";
import { Box, Typography } from "@mui/material";
import NodeTypeIcons from "./icons/NodeTypeIcons";

/**
 * Generic explorer tree item label.
 * Displays icon + name and supports custom right adornment.
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
				<NodeTypeIcons type={type} isFolder={isFolder} />
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
 * @param {{ node:{ id:string, name:string, type?:string, children?:any[] }, right?:React.ReactNode }} props
 */
const ExplorerTreeItem = React.memo(function ExplorerTreeItem({ node, right }) {
	return (
		<TreeItem
			itemId={node.id}
			label={
				<ExplorerTreeItemLabel
					name={node.name}
					type={node.type}
					isFolder={Array.isArray(node.children) && node.children.length > 0}
					right={right}
				/>
			}
			slotProps={{ content: { sx: { width: "100%" } } }}
		>
			{Array.isArray(node.children) &&
				node.children.map((c) => <ExplorerTreeItem key={c.id} node={c} />)}
		</TreeItem>
	);
});

export default ExplorerTreeItem;
