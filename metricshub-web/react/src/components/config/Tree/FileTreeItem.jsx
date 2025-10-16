import * as React from "react";
import { TreeItem } from "@mui/x-tree-view";
import { Box, IconButton, Menu, MenuItem, TextField, Stack } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import DeleteIcon from "@mui/icons-material/Delete";
import BackupIcon from "@mui/icons-material/Backup";
import RestoreIcon from "@mui/icons-material/Restore";
import FileTypeIcon from "./icons/FileTypeIcons";
import FileMeta from "./FileMeta";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import { useAppDispatch } from "../../../hooks/store";
import { createConfigBackup } from "../../../store/thunks/configThunks";
import { restoreConfigFromBackup } from "../../../store/thunks/configThunks";
import QuestionDialog from "../../common/QuestionDialog";

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

	// restore dialog
	const [restoreOpen, setRestoreOpen] = React.useState(false);

	const treeItemId = itemId ?? file.name;
	const displayName = labelName ?? file.name;

	// detect backup files: "backup-<timestamp>__<original-filename>"
	const backupMatch = /^backup-(\d{8}-\d{6})__(.+)$/.exec(file.name);
	const isBackupFile = !!backupMatch;
	const backupId = backupMatch?.[1] ?? null;

	React.useEffect(() => {
		if (!editing) setDraft(file.name);
	}, [file.name, editing]);

	const openMenu = (e) => {
		e.stopPropagation();
		setMenuAnchor(e.currentTarget);
	};
	const closeMenu = () => setMenuAnchor(null);

	const startRename = () => {
		closeMenu();
		rowRef.current?.blur?.();
		setEditing(true);
		requestAnimationFrame(() => inputRef.current?.select());
	};

	const cancelRename = () => {
		cancelledRef.current = true;
		setDraft(file.name);
		setEditing(false);
		rowRef.current?.blur?.();
	};

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
			} catch (e) {
				console.error("Backup failed:", e);
			}
		}, 0);
	}, [dispatch, file.name]);

	const askRestore = React.useCallback(() => {
		document.activeElement?.blur?.();
		closeMenu();
		setRestoreOpen(true);
	}, []);

	const doRestore = React.useCallback(
		async (overwrite) => {
			setRestoreOpen(false);
			setTimeout(async () => {
				try {
					const res = await dispatch(
						restoreConfigFromBackup({ backupName: file.name, overwrite }),
					).unwrap();
					console.info("Restored:", res);
					if (onSelect && res?.restoredName) onSelect(res.restoredName);
					else if (onSelect && res?.originalName) onSelect(res.originalName);
				} catch (e) {
					console.error("Restore failed:", e);
				}
			}, 0);
		},
		[dispatch, file.name, onSelect],
	);

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
								<FileTypeIcon type="yaml" />
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
							<FileTypeIcon type="yaml" />
							<Box
								sx={{
									fontWeight: isDirty ? 510 : 500,
									whiteSpace: "nowrap",
									overflow: "hidden",
									textOverflow: "ellipsis",
								}}
								title={displayName}
							>
								{displayName}
							</Box>
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
					onMouseDown={(e) => e.preventDefault()}
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
						sx: { "&.Mui-focusVisible": { backgroundColor: "transparent" } },
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
						<DriveFileRenameOutlineIcon fontSize="small" style={{ marginRight: 8 }} />
						Rename
					</MenuItem>
				)}

				{!isBackupFile ? (
					<MenuItem onClick={backupThisFile}>
						<BackupIcon fontSize="small" style={{ marginRight: 8 }} />
						Backup
					</MenuItem>
				) : (
					<MenuItem onClick={askRestore}>
						<RestoreIcon fontSize="small" style={{ marginRight: 8 }} />
						Restore{backupId ? ` (${backupId})` : ""}
					</MenuItem>
				)}

				<MenuItem
					onClick={() => {
						closeMenu();
						onDelete(file.name);
					}}
				>
					<DeleteIcon fontSize="small" style={{ marginRight: 8 }} />
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
