import * as React from "react";
import { TreeItem } from "@mui/x-tree-view";
import { Box, IconButton, Menu, MenuItem, TextField, Stack, Typography } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import DeleteIcon from "@mui/icons-material/Delete";
import BackupIcon from "@mui/icons-material/Backup";
import RestoreIcon from "@mui/icons-material/Restore";
import DownloadIcon from "@mui/icons-material/Download";
import FileTypeIcon from "./icons/FileTypeIcons";
import FileMeta from "./FileMeta";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import { useAppDispatch } from "../../../hooks/store";
import { useSnackbar } from "../../../hooks/use-snackbar";
import {
	createConfigBackup,
	restoreConfigFromBackup,
	deleteBackupFile,
	fetchConfigList,
} from "../../../store/thunks/configThunks";
import QuestionDialog from "../../common/QuestionDialog";
import { downloadConfigFile } from "../../../services/downloadService";
import { parseBackupFileName } from "../../../utils/backupNames";

/**
 * File tree item component.
 * @param {{file:{name:string,size:number,lastModificationTime:string,localOnly?:boolean},onRename:(oldName:string,newName:string)=>void,onDelete:(name:string)=>void}} props The component props.
 * @returns {JSX.Element} The file tree item component.
 */
export default function FileTreeItem({
	file,
	onRename,
	onDelete,
	isDirty = false,
	validation = null,
	itemId, // optional selection id
	labelName, // optional display name
	onSelect, // optional, used to navigate after restore
}) {
	const dispatch = useAppDispatch();
	const [editing, setEditing] = React.useState(false);
	const [draft, setDraft] = React.useState(file.name);
	const inputRef = React.useRef(null);
	const rowRef = React.useRef(null);
	const containerRef = React.useRef(null);
	const cancelledRef = React.useRef(false);
	const [menuAnchor, setMenuAnchor] = React.useState(null);
	const { show: showSnackbar } = useSnackbar();

	// restore dialog
	const [restoreOpen, setRestoreOpen] = React.useState(false);

	const treeItemId = itemId ?? file.name;
	const displayName = labelName ?? file.name;

	// detect backup files: flat name using backupNames utils
	const parsed = parseBackupFileName(file.name);
	const isBackupFile = !!parsed;
	const backupId = parsed?.id ?? null;
	const backupOriginal = parsed?.originalName ?? null;

	React.useEffect(() => {
		if (!editing) setDraft(file.name);
	}, [file.name, editing]);

	const openMenu = React.useCallback((e) => {
		e.stopPropagation();
		setMenuAnchor(e.currentTarget);
	}, []);

	const closeMenu = React.useCallback(() => setMenuAnchor(null), []);

	const startRename = React.useCallback(() => {
		closeMenu();
		rowRef.current?.blur?.();
		setEditing(true);
		requestAnimationFrame(() => inputRef.current?.select());
	}, [closeMenu]);

	const cancelRename = React.useCallback(() => {
		cancelledRef.current = true;
		setDraft(file.name);
		setEditing(false);
		rowRef.current?.blur?.();
	}, [file.name]);

	const submitRename = React.useCallback(() => {
		if (cancelledRef.current) {
			cancelledRef.current = false;
			setEditing(false);
			return;
		}
		const next = draft.trim();
		if (!next || next === file.name) {
			setEditing(false);
			return;
		}
		onRename?.(file.name, next);
		setEditing(false);
	}, [draft, file.name, onRename]);

	const backupThisFile = React.useCallback(async () => {
		document.activeElement?.blur?.();
		closeMenu();
		setTimeout(async () => {
			try {
				await dispatch(createConfigBackup({ kind: "file", name: file.name })).unwrap();
				showSnackbar(`Backup created for ${file.name}`, { severity: "success" });
			} catch (e) {
				console.error("Backup failed:", e);
				showSnackbar("Backup failed", { severity: "error" });
			}
		}, 0);
	}, [dispatch, file.name, showSnackbar, closeMenu]);

	const askRestore = React.useCallback(() => {
		document.activeElement?.blur?.();
		closeMenu();
		setRestoreOpen(true);
	}, [closeMenu]);

	const doRestore = React.useCallback(
		async (overwrite) => {
			setRestoreOpen(false);
			setTimeout(async () => {
				try {
					const res = await dispatch(
						restoreConfigFromBackup({ backupName: file.name, overwrite }),
					).unwrap();
					if (onSelect && res?.restoredName) onSelect(res.restoredName);
					else if (onSelect && res?.originalName) onSelect(res.originalName);
					const targetName = res?.restoredName || res?.originalName || file.name;
					if (overwrite) {
						showSnackbar(`Restored and overwrote ${targetName}`, { severity: "success" });
					} else {
						showSnackbar(`Restored as copy ${targetName}`, { severity: "success" });
					}
				} catch (e) {
					console.error("Restore failed:", e);
					showSnackbar("Restore failed", { severity: "error" });
				}
			}, 0);
		},
		[dispatch, file.name, onSelect, showSnackbar],
	);

	const downloadThisFile = React.useCallback(async () => {
		document.activeElement?.blur?.();
		closeMenu();
		// For backups: save as the displayed filename (original name). For normal files: save as-is.
		const suggestedName = backupOriginal ?? labelName ?? file.name;
		try {
			await downloadConfigFile({ name: file.name, suggestedName });
		} catch (e) {
			console.error("Download failed:", e);
			showSnackbar("Download failed", { severity: "error" });
		}
	}, [file.name, labelName, backupOriginal, showSnackbar, closeMenu]);

	// Stable handler for Delete menu action
	const handleDeleteClick = React.useCallback(async () => {
		closeMenu();
		if (isBackupFile) {
			try {
				await dispatch(deleteBackupFile(file.name)).unwrap();
				await dispatch(fetchConfigList());
				showSnackbar("Backup deleted", { severity: "success" });
			} catch (e) {
				console.error("Delete backup failed:", e);
				showSnackbar("Failed to delete backup file", { severity: "error" });
			}
		} else {
			onDelete(file.name);
		}
	}, [closeMenu, isBackupFile, dispatch, file.name, showSnackbar, onDelete]);

	// Prevent default on mousedown for the menu button
	const preventMouseDownDefault = React.useCallback((e) => e.preventDefault(), []);

	const label = (
		<Stack
			ref={containerRef}
			direction="row"
			alignItems="center"
			justifyContent="space-between"
			sx={{ width: "100%", pr: 1, userSelect: editing ? "text" : "none" }}
		>
			<Box sx={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
				{editing ? (
					<ClickAwayListener
						onClickAway={submitRename}
						mouseEvent="onClick"
						touchEvent={false}
						disableReactTree
					>
						<Box onMouseDown={(e) => e.stopPropagation()} onClick={(e) => e.stopPropagation()}>
							<Box sx={{ display: "flex", alignItems: "center" }}>
								<FileTypeIcon type={isBackupFile ? "backup" : "file"} />
								<TextField
									inputRef={inputRef}
									value={draft}
									onChange={(e) => setDraft(e.target.value)}
									variant="standard"
									size="small"
									fullWidth
									autoFocus
									onKeyDown={(e) => {
										if (e.key === "Enter") {
											e.preventDefault();
											e.stopPropagation();
											submitRename();
											return;
										}
										if (e.key === "Escape") {
											e.preventDefault();
											e.stopPropagation();
											cancelRename();
											return;
										}
										e.stopPropagation();
									}}
									slotProps={{ htmlInput: { "aria-label": "Rename file" } }}
									sx={{ "& .MuiInputBase-input": { fontWeight: 500 } }}
								/>
							</Box>
							<FileMeta file={file} sx={{ mt: 0.5 }} />
						</Box>
					</ClickAwayListener>
				) : (
					<>
						<Box sx={{ display: "flex", alignItems: "center", minWidth: 0 }}>
							<FileTypeIcon type={isBackupFile ? "backup" : "file"} />
							<Typography
								component="span"
								noWrap
								title={displayName}
								sx={{
									fontWeight: isDirty ? 510 : 500,
									overflow: "hidden",
									textOverflow: "ellipsis",
								}}
							>
								{displayName}
							</Typography>
							{isDirty && (
								<Box
									component="span"
									title={
										validation && validation.valid === false
											? "Validation errors"
											: "Unsaved changes"
									}
									aria-label="Unsaved changes"
									sx={{
										ml: 0.75,
										width: 8,
										height: 8,
										borderRadius: "50%",
										bgcolor: (t) =>
											validation && validation.valid === false
												? t.palette.error.main
												: t.palette.warning.main,
										flexShrink: 0,
									}}
								/>
							)}
						</Box>
						<FileMeta file={file} />
					</>
				)}
			</Box>

			{!editing && (
				<IconButton
					size="small"
					aria-label="More actions"
					onClick={openMenu}
					onMouseDown={preventMouseDownDefault}
				>
					<MoreVertIcon fontSize="small" />
				</IconButton>
			)}
		</Stack>
	);

	return (
		<>
			<TreeItem
				itemId={treeItemId ?? file.name}
				label={label}
				slotProps={{
					content: {
						ref: rowRef,
						sx: { width: "100%", "&.Mui-focusVisible": { backgroundColor: "transparent" } },
					},
				}}
			/>
			<Menu
				anchorEl={menuAnchor}
				open={Boolean(menuAnchor)}
				onClose={closeMenu}
				disableRestoreFocus
				disableAutoFocusItem
				anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
				transformOrigin={{ vertical: "top", horizontal: "right" }}
			>
				{!isBackupFile && (
					<MenuItem onClick={startRename}>
						<DriveFileRenameOutlineIcon fontSize="small" sx={{ mr: 1 }} />
						Rename
					</MenuItem>
				)}

				{/* Download available for both normal and backup files */}
				<MenuItem onClick={downloadThisFile}>
					<DownloadIcon fontSize="small" sx={{ mr: 1 }} />
					Download
				</MenuItem>

				{!isBackupFile ? (
					<MenuItem onClick={backupThisFile}>
						<BackupIcon fontSize="small" sx={{ mr: 1 }} />
						Backup
					</MenuItem>
				) : (
					<MenuItem onClick={askRestore}>
						<RestoreIcon fontSize="small" sx={{ mr: 1 }} />
						Restore{backupId ? ` (${backupId})` : ""}
					</MenuItem>
				)}

				<MenuItem onClick={handleDeleteClick}>
					<DeleteIcon fontSize="small" sx={{ mr: 1 }} />
					Delete
				</MenuItem>
			</Menu>

			{/* Restore confirmation dialog */}
			<QuestionDialog
				open={restoreOpen}
				title="Restore from backup"
				question={"Do you want to overwrite the original file?\n\n"}
				onClose={() => setRestoreOpen(false)}
				actionButtons={[
					{ btnTitle: "Cancel", callback: () => setRestoreOpen(false), autoFocus: true },
					{
						btnTitle: "Restore as copy",
						btnVariant: "contained",
						callback: () => doRestore(false),
					},
					{
						btnTitle: "Overwrite",
						btnColor: "error",
						btnVariant: "contained",
						callback: () => doRestore(true),
					},
				]}
			/>
		</>
	);
}
