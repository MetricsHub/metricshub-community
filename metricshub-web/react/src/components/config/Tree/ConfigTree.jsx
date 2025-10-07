import * as React from "react";
import { SimpleTreeView, TreeItem } from "@mui/x-tree-view";
import FileTypeIcon from "./icons/FileTypeIcons.jsx";
import { Stack, Box } from "@mui/material";
import FileTreeItem from "./FileTreeItem";

/**
 * Props:
 * - files: Array<{ name: string, size: number, lastModificationTime: string }>
 * - selectedName: string | null
 * - onSelect: (name: string) => void
 * - onRename: (oldName: string, newName: string) => void
 * - onDeleteRequest: (name: string) => void
 * - loading: boolean
 */
export default function ConfigTree({ files, selectedName, onSelect, onRename, onDeleteRequest }) {
	const selectedIds = React.useMemo(() => (selectedName ? [selectedName] : []), [selectedName]);

	const folderLabel = (
		<Box sx={{ display: "flex", alignItems: "center" }}>
			<FileTypeIcon type="folder" />
			<span>config</span>
		</Box>
	);

	return (
		<Stack sx={{ p: 0 }}>
			<SimpleTreeView
				defaultExpandedItems={["config"]}
				aria-label="Configuration files"
				multiSelect={false}
				selectedItems={selectedIds}
				onSelectedItemsChange={(_, ids) => {
					const id = Array.isArray(ids) ? ids[0] : ids;
					if (id && id !== "config") onSelect(id);
				}}
				slots={{}}
				slotProps={{}}
				sx={{
					"& .MuiTreeItem-content": { py: 0.25 },
				}}
			>
				<TreeItem
					itemId="config"
					label={folderLabel}
					sx={{
						"& .MuiTreeItem-label": { fontWeight: 400 },
					}}
				>
					{files.map((f) => (
						<FileTreeItem
							key={f.name}
							file={f}
							selected={selectedName === f.name}
							onSelect={onSelect}
							onRename={onRename}
							onDeleteRequest={onDeleteRequest}
						/>
					))}
				</TreeItem>
			</SimpleTreeView>
		</Stack>
	);
}
