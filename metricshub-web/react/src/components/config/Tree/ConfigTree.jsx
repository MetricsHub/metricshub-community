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
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { createConfigBackup } from "../../../store/thunks/configThunks";
import { downloadAllConfigs } from "../../../utils/downloadAllConfigs";

const ROOT_ID = "__config_root__";
const BACKUP_ROOT_ID = "__backup_root__";

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
	const openRootMenu = (e) => {
		e.stopPropagation();
		setRootMenuAnchor(e.currentTarget);
	};
	const closeRootMenu = () => setRootMenuAnchor(null);
	const handleBackupAll = async () => {
		document.activeElement?.blur?.();
		closeRootMenu();
		try {
			await dispatch(createConfigBackup({ kind: "all" })).unwrap();
		} catch (e) {
			console.error("Backup all failed:", e);
		}
	};

	const folderLabel = (
		<Box
			sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%" }}
		>
			<Box sx={{ display: "flex", alignItems: "center" }}>
				<FileTypeIcon type="folder" />
				<span>config</span>
			</Box>
			<IconButton
				size="small"
				aria-label="More actions"
				onClick={openRootMenu}
				onMouseDown={(e) => e.preventDefault()}
			>
				<MoreVertIcon fontSize="small" />
			</IconButton>
		</Box>
	);

	const backupFolderLabel = (
		<Box sx={{ display: "flex", alignItems: "center" }}>
			<FileTypeIcon type="folder" />
			<span>backup</span>
		</Box>
	);

	const dirtyByName = useAppSelector((s) => s.config.dirtyByName) ?? {};
	const filesByName = useAppSelector((s) => s.config.filesByName) ?? {};

	// helpers to detect non-file (folder) ids
	const isFolderId = (id) =>
		id === ROOT_ID ||
		id === BACKUP_ROOT_ID ||
		(typeof id === "string" && id.startsWith("__backup_set__/"));

	// Split & group: normal files vs. "backup-<id>__<name>"
	const { configFiles, backupsBySet, backupSetItemIds } = React.useMemo(() => {
		const cfg = [];
		const groups = {}; // id -> [{ ...meta, displayName }]
		const setItemIds = [];
		const rx = /^backup-(\d{8}-\d{6})__(.+)$/; // matches timestamp id and the original/backup filename

		for (const f of files || []) {
			const n = f?.name ?? "";
			const m = rx.exec(n);
			if (!m) {
				cfg.push(f);
			} else {
				const id = m[1]; // e.g. 20251016-143917
				const displayName = m[2]; // rest of the name shown as the file inside the backup set
				(groups[id] ||= []).push({ ...f, displayName });
			}
		}

		// Sort groups and files
		for (const id of Object.keys(groups)) {
			groups[id].sort((a, b) => a.displayName.localeCompare(b.displayName));
			setItemIds.push(`__backup_set__/${id}`);
		}
		cfg.sort((a, b) => a.name.localeCompare(b.name));

		return { configFiles: cfg, backupsBySet: groups, backupSetItemIds: setItemIds };
	}, [files]);

	return (
		<Stack sx={{ p: 0 }}>
			<SimpleTreeView
				defaultExpandedItems={[ROOT_ID, BACKUP_ROOT_ID, ...backupSetItemIds]}
				aria-label="Configuration files"
				multiSelect={false}
				selectedItems={selectedIds}
				onSelectedItemsChange={(_, id) => {
					// ignore folder nodes entirely; only leaf (file) items should select
					if (!id || isFolderId(id)) return;
					onSelect(id);
				}}
				sx={{ "& .MuiTreeItem-content": { py: 0.25 } }}
			>
				<TreeItem
					itemId={ROOT_ID}
					label={folderLabel}
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
					label={backupFolderLabel}
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
					<BackupIcon fontSize="small" style={{ marginRight: 8 }} />
					Backup all
				</MenuItem>
				<MenuItem
					onClick={async () => {
						closeRootMenu();
						await downloadAllConfigs(list);
					}}
				>
					<DownloadIcon fontSize="small" style={{ marginRight: 8 }} />
					Download all
				</MenuItem>
			</Menu>
		</Stack>
	);
}
