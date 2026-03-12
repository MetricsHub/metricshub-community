import * as React from "react";
import { SimpleTreeView, TreeItem, treeItemClasses } from "@mui/x-tree-view";
import FileTypeIcon from "../config/tree/icons/FileTypeIcons";
import { Stack, Box, IconButton, Menu, MenuItem } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import BackupIcon from "@mui/icons-material/Backup";
import DownloadIcon from "@mui/icons-material/Download";
import OtelFileTreeItem from "./tree/OtelFileTreeItem";
import OtelBackupSetNode from "./tree/OtelBackupSetNode";
import { parseBackupFileName } from "../../utils/backup-names";
import { useAppDispatch, useAppSelector } from "../../hooks/store";
import { useAuth } from "../../hooks/use-auth";
import { createOtelConfigBackup } from "../../store/thunks/otel-config-thunks";
import { downloadAllOtelConfigs } from "../../services/download-service";

const ROOT_ID = "__otel_root__";
const BACKUP_ROOT_ID = "__otel_backup_root__";

const FolderLabel = React.memo(function FolderLabel({ name, onMenuClick }) {
	return (
		<Box
			sx={{
				display: "flex",
				alignItems: "center",
				justifyContent: "space-between",
				width: "100%",
				pr: 1,
			}}
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

export default function OtelConfigTree({
	files,
	selectedName,
	onSelect,
	onRename,
	onDelete,
	onMakeDraft,
}) {
	const dispatch = useAppDispatch();
	const list = useAppSelector((s) => s.otelConfig.list);
	const { user } = useAuth();
	const isReadOnly = user?.role === "ro";
	const selectedIds = React.useMemo(() => (selectedName ? [selectedName] : []), [selectedName]);

	const [rootMenuAnchor, setRootMenuAnchor] = React.useState(null);
	const openRootMenu = React.useCallback(
		(e) => {
			if (isReadOnly) return;
			e.stopPropagation();
			setRootMenuAnchor(e.currentTarget);
		},
		[isReadOnly],
	);
	const closeRootMenu = React.useCallback(() => setRootMenuAnchor(null), []);
	const handleBackupAll = React.useCallback(async () => {
		if (isReadOnly) return;
		document.activeElement?.blur?.();
		closeRootMenu();
		try {
			await dispatch(createOtelConfigBackup({ kind: "all" })).unwrap();
		} catch (e) {
			console.error("Backup all failed:", e);
		}
	}, [closeRootMenu, dispatch, isReadOnly]);

	const handleDownloadAll = React.useCallback(async () => {
		if (isReadOnly) return;
		closeRootMenu();
		await downloadAllOtelConfigs(list);
	}, [closeRootMenu, list, isReadOnly]);

	const dirtyByName = useAppSelector((s) => s.otelConfig.dirtyByName) ?? {};
	const filesByName = useAppSelector((s) => s.otelConfig.filesByName) ?? {};

	const { configFiles, backupsBySet } = React.useMemo(() => {
		const cfg = [];
		const groups = {};
		for (const f of files || []) {
			const n = f?.name ?? "";
			const parsed = parseBackupFileName(n);
			if (parsed) {
				const { id, originalName } = parsed;
				(groups[id] ||= []).push({ ...f, displayName: originalName });
				continue;
			}
			cfg.push(f);
		}
		for (const id of Object.keys(groups)) {
			groups[id].sort((a, b) => a.displayName.localeCompare(b.displayName));
		}
		cfg.sort((a, b) => a.name.localeCompare(b.name));
		return { configFiles: cfg, backupsBySet: groups };
	}, [files]);

	const onTreeSelectionChange = React.useCallback(
		(_, id) => {
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
				defaultExpandedItems={[ROOT_ID, BACKUP_ROOT_ID]}
				aria-label="OTEL configuration files"
				multiSelect={false}
				selectedItems={selectedIds}
				onSelectedItemsChange={onTreeSelectionChange}
				sx={{
					[`& .${treeItemClasses.content}`]: { py: 0.25, width: "100%" },
					[`& .${treeItemClasses.label}`]: { flex: 1, minWidth: 0 },
				}}
			>
				<TreeItem
					itemId={ROOT_ID}
					label={<FolderLabel name="Otel" onMenuClick={!isReadOnly ? openRootMenu : null} />}
					sx={{ "& .MuiTreeItem-label": { fontWeight: 400 } }}
				>
					{configFiles.map((f) => (
						<OtelFileTreeItem
							key={f.name}
							file={f}
							isDirty={!!dirtyByName?.[f.name]}
							validation={filesByName[f.name]?.validation}
							onSelect={onSelect}
							onRename={onRename}
							onDelete={onDelete}
							onMakeDraft={onMakeDraft}
							isReadOnly={isReadOnly}
							isSelected={selectedName === f.name}
						/>
					))}
				</TreeItem>

				<TreeItem
					itemId={BACKUP_ROOT_ID}
					label={<FolderLabel name="otel-backup" />}
					sx={{ "& .MuiTreeItem-label": { fontWeight: 400 } }}
				>
					{Object.keys(backupsBySet)
						.sort((a, b) => b.localeCompare(a))
						.map((id) => (
							<OtelBackupSetNode
								key={id}
								id={id}
								files={backupsBySet[id]}
								dirtyByName={dirtyByName}
								filesByName={filesByName}
								onSelect={onSelect}
								onRename={onRename}
								onDelete={onDelete}
								isReadOnly={isReadOnly}
								selectedName={selectedName}
							/>
						))}
				</TreeItem>
			</SimpleTreeView>

			<Menu
				anchorEl={rootMenuAnchor}
				open={Boolean(rootMenuAnchor)}
				onClose={closeRootMenu}
				disableRestoreFocus
				disableAutoFocusItem
				anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
				transformOrigin={{ vertical: "top", horizontal: "right" }}
			>
				<MenuItem onClick={handleBackupAll} disabled={isReadOnly}>
					<BackupIcon fontSize="small" sx={{ mr: 1 }} />
					Backup all
				</MenuItem>
				<MenuItem onClick={handleDownloadAll} disabled={isReadOnly}>
					<DownloadIcon fontSize="small" sx={{ mr: 1 }} />
					Download all
				</MenuItem>
			</Menu>
		</Stack>
	);
}
