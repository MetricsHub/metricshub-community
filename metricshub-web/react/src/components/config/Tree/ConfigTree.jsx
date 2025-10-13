// src/components/config/Tree/ConfigTree.jsx
import * as React from "react";
import { SimpleTreeView, TreeItem } from "@mui/x-tree-view";
import FileTypeIcon from "./icons/FileTypeIcons.jsx";
import { Stack, Box } from "@mui/material";
import FileTreeItem from "./FileTreeItem";
import { useAppSelector } from "../../../hooks/store";

const ROOT_ID = "__config_root__";
/**
 * Props:
 * - files: Array<{ name: string, size: number, lastModificationTime: string }>
 * - selectedName: string | null
 * - onSelect: (name: string) => void
 * - onRename: (oldName: string, newName: string) => void
 * - onDelete: (name: string) => void
 * - loading: boolean
 */
export default function ConfigTree({ files, selectedName, onSelect, onRename, onDelete }) {
	const selectedIds = React.useMemo(() => (selectedName ? [selectedName] : []), [selectedName]);

	const folderLabel = (
		<Box sx={{ display: "flex", alignItems: "center" }}>
			<FileTypeIcon type="folder" />
			<span>config</span>
		</Box>
	);

	const dirtyByName = useAppSelector((s) => s.config.dirtyByName) ?? {};
	const filesByName = useAppSelector((s) => s.config.filesByName) ?? {};

	return (
		<Stack sx={{ p: 0 }}>
			<SimpleTreeView
				defaultExpandedItems={[ROOT_ID]}
				aria-label="Configuration files"
				multiSelect={false}
				selectedItems={selectedIds}
				onSelectedItemsChange={(_, id) => {
					if (id && id !== ROOT_ID) onSelect(id);
				}}
				sx={{ "& .MuiTreeItem-content": { py: 0.25 } }}
			>
				<TreeItem
					itemId={ROOT_ID}
					label={folderLabel}
					sx={{ "& .MuiTreeItem-label": { fontWeight: 400 } }}
				>
					{files.map((f) => (
						<FileTreeItem
							key={f.name}
							file={f}
							isDirty={!!dirtyByName?.[f.name]}
							validation={filesByName[f.name]?.validation}
							onSelect={onSelect}
							onRename={onRename}
							onDelete={onDelete}
						/>
					))}
				</TreeItem>
			</SimpleTreeView>
		</Stack>
	);
}
