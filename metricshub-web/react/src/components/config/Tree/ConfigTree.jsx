// src/components/config/Tree/ConfigTree.jsx
import * as React from "react";
import { SimpleTreeView, TreeItem } from "@mui/x-tree-view";
import FileTypeIcon from "./icons/FileTypeIcons.jsx";
import { Stack, Box, IconButton, Menu, MenuItem } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import BackupIcon from "@mui/icons-material/Backup";
import DownloadIcon from "@mui/icons-material/Download";
import FileTreeItem from "./FileTreeItem";
import BackupSetNode from "./BackupSetNode";
import { parseBackupFileName } from "../../../utils/backupNames";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { createConfigBackup } from "../../../store/thunks/configThunks";
import { downloadAllConfigs } from "../../../utils/downloadAllConfigs";

const ROOT_ID = "__config_root__";
const BACKUP_ROOT_ID = "__backup_root__";

// Memoized label for folder-like tree items (left aligned icon + name, optional kebab on the right)
const FolderLabel = React.memo(function FolderLabel({ name, onMenuClick }) {
	return (
		<Box
			sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%" }}
		>
			<Box sx={{ display: "flex", alignItems: "center" }}>
				<FileTypeIcon type="folder" />
				<span>{name}</span>
			</Box>
			{onMenuClick && (
				<IconButton
					size="small"
					aria-label="More actions"
					onClick={onMenuClick}
					onMouseDown={(e) => e.preventDefault()}
				>
					<MoreVertIcon fontSize="small" />
				</IconButton>
			)}
		</Box>
	);
});

/**
 * Configuration tree component.
 *
 * @param {{files:{name:string,size:number,lastModificationTime:string,localOnly?:boolean}[],selectedName:string,onSelect:(name:string)=>void,onRename?:(oldName:string,newName:string)=>void,onDelete?:(name:string)=>void}} props The component props.
 * @returns {JSX.Element} The configuration tree component.
 */
export default function ConfigTree({ files, selectedName, onSelect, onRename, onDelete }) {
	const dispatch = useAppDispatch();
	const list = useAppSelector((s) => s.config.list);
	const selectedIds = React.useMemo(() => (selectedName ? [selectedName] : []), [selectedName]);

	// config root kebab
	const [rootMenuAnchor, setRootMenuAnchor] = React.useState(null);
	const openRootMenu = React.useCallback((e) => {
		e.stopPropagation();
		setRootMenuAnchor(e.currentTarget);
	}, []);
	const closeRootMenu = React.useCallback(() => setRootMenuAnchor(null), []);
	const handleBackupAll = React.useCallback(async () => {
		document.activeElement?.blur?.();
		closeRootMenu();
		try {
			await dispatch(createConfigBackup({ kind: "all" })).unwrap();
		} catch (e) {
			console.error("Backup all failed:", e);
		}
	}, [closeRootMenu, dispatch]);

	const handleDownloadAll = React.useCallback(async () => {
		closeRootMenu();
		await downloadAllConfigs(list);
	}, [closeRootMenu, list]);

	// Deprecated inline labels replaced by <FolderLabel /> to avoid duplication and re-renders

	const dirtyByName = useAppSelector((s) => s.config.dirtyByName) ?? {};
	const filesByName = useAppSelector((s) => s.config.filesByName) ?? {};

	// helpers to detect non-file (folder) ids

	// Split & group: normal files vs. backups (folder-style only).
	const { configFiles, backupsBySet, backupSetItemIds } = React.useMemo(() => {
		const cfg = [];
		const groups = {}; // id -> [{ ...meta, displayName }]
		const setItemIds = [];

		for (const f of files || []) {
			const n = f?.name ?? "";
			const parsed = parseBackupFileName(n);
			if (parsed) {
				const { id, originalName } = parsed;
				const displayName = originalName;
				(groups[id] ||= []).push({ ...f, displayName });
				continue;
			}
			cfg.push(f);
		}

		// Sort groups and files
		for (const id of Object.keys(groups)) {
			groups[id].sort((a, b) => a.displayName.localeCompare(b.displayName));
			setItemIds.push(`__backup_set__/${id}`);
		}
		cfg.sort((a, b) => a.name.localeCompare(b.name));

		return { configFiles: cfg, backupsBySet: groups, backupSetItemIds: setItemIds };
	}, [files]);

	const onTreeSelectionChange = React.useCallback(
		(_, id) => {
			// ignore folder nodes entirely; only leaf (file) items should select
			if (
				!id ||
				id === ROOT_ID ||
				id === BACKUP_ROOT_ID ||
				(typeof id === "string" && id.startsWith("__backup_set__/"))
			)
				return;
			onSelect(id);
		},
		[onSelect],
	);

	return (
		<Stack sx={{ p: 0 }}>
			<SimpleTreeView
				defaultExpandedItems={[ROOT_ID, BACKUP_ROOT_ID, ...backupSetItemIds]}
				aria-label="Configuration files"
				multiSelect={false}
				selectedItems={selectedIds}
				onSelectedItemsChange={onTreeSelectionChange}
				sx={{ "& .MuiTreeItem-content": { py: 0.25 } }}
			>
				<TreeItem
					itemId={ROOT_ID}
					label={<FolderLabel name="config" onMenuClick={openRootMenu} />}
					sx={{ "& .MuiTreeItem-label": { fontWeight: 400 } }}
				>
					{configFiles.map((f) => (
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

				<TreeItem
					itemId={BACKUP_ROOT_ID}
					label={<FolderLabel name="backup" />}
					sx={{ "& .MuiTreeItem-label": { fontWeight: 400 } }}
				>
					{Object.keys(backupsBySet)
						.sort((a, b) => b.localeCompare(a))
						.map((id) => (
							<BackupSetNode
								key={id}
								id={id}
								files={backupsBySet[id]}
								dirtyByName={dirtyByName}
								filesByName={filesByName}
								onSelect={onSelect}
								onRename={onRename}
								onDelete={onDelete}
							/>
						))}
				</TreeItem>
			</SimpleTreeView>

			{/* Root "config" kebab menu */}
			<Menu
				anchorEl={rootMenuAnchor}
				open={Boolean(rootMenuAnchor)}
				onClose={closeRootMenu}
				disableRestoreFocus
				disableAutoFocusItem
				anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
				transformOrigin={{ vertical: "top", horizontal: "right" }}
			>
				<MenuItem onClick={handleBackupAll}>
					<BackupIcon fontSize="small" sx={{ mr: 1 }} />
					Backup all
				</MenuItem>
				<MenuItem onClick={handleDownloadAll}>
					<DownloadIcon fontSize="small" sx={{ mr: 1 }} />
					Download all
				</MenuItem>
			</Menu>
		</Stack>
	);
}
