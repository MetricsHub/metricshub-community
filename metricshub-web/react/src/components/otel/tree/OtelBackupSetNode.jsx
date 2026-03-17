import * as React from "react";
import { TreeItem } from "@mui/x-tree-view";
import { Box, IconButton, Menu, MenuItem } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import RestoreIcon from "@mui/icons-material/Restore";
import DeleteIcon from "@mui/icons-material/Delete";
import FileTypeIcon from "../../config/tree/icons/FileTypeIcons";
import OtelFileTreeItem from "./OtelFileTreeItem";
import QuestionDialog from "../../common/QuestionDialog";
import { useAppDispatch } from "../../../hooks/store";
import { useAuth } from "../../../hooks/use-auth";
import {
	restoreOtelConfigFromBackup,
	deleteOtelBackupFile,
	fetchOtelConfigList,
} from "../../../store/thunks/otel-config-thunks";
import { useSnackbar } from "../../../hooks/use-snackbar";

const BackupGroupLabel = React.memo(function BackupGroupLabel({ id, onMenuClick }) {
	const preventMouseDown = React.useCallback((e) => e.preventDefault(), []);
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
				<span>{`otel-backup-${id}`}</span>
			</Box>
			{onMenuClick && (
				<IconButton
					size="small"
					aria-label="More actions"
					onClick={onMenuClick}
					onMouseDown={preventMouseDown}
				>
					<MoreVertIcon fontSize="small" />
				</IconButton>
			)}
		</Box>
	);
});

export default function OtelBackupSetNode({
	id,
	files = [],
	dirtyByName,
	filesByName,
	onSelect,
	onRename,
	onDelete,
	isReadOnly = false,
	selectedName = null,
	/** When set (e.g. "otel"), itemIds are prefixed for use in a unified tree. */
	idPrefix = null,
}) {
	const dispatch = useAppDispatch();
	const { user } = useAuth();
	const effectiveReadOnly = isReadOnly || user?.role === "ro";
	const [menuAnchor, setMenuAnchor] = React.useState(null);
	const [restoreAllOpen, setRestoreAllOpen] = React.useState(false);
	const [deleteSetOpen, setDeleteSetOpen] = React.useState(false);
	const { show: showSnackbar } = useSnackbar();

	const openMenu = React.useCallback(
		(e) => {
			if (effectiveReadOnly) return;
			e.stopPropagation();
			setMenuAnchor(e.currentTarget);
		},
		[effectiveReadOnly],
	);
	const closeMenu = React.useCallback(() => setMenuAnchor(null), []);

	const askRestoreAll = React.useCallback(() => {
		if (effectiveReadOnly) return;
		document.activeElement?.blur?.();
		closeMenu();
		setRestoreAllOpen(true);
	}, [closeMenu, effectiveReadOnly]);

	const askDeleteSet = React.useCallback(() => {
		if (effectiveReadOnly) return;
		document.activeElement?.blur?.();
		closeMenu();
		setDeleteSetOpen(true);
	}, [closeMenu, effectiveReadOnly]);

	const doRestoreAll = React.useCallback(
		async (overwrite) => {
			if (effectiveReadOnly) return;
			setRestoreAllOpen(false);
			try {
				await Promise.all(
					(files || []).map((f) =>
						dispatch(
							restoreOtelConfigFromBackup({ backupName: f.name, overwrite, skipRefresh: true }),
						).unwrap(),
					),
				);
				await dispatch(fetchOtelConfigList());
			} catch (e) {
				console.error("Restore all failed:", e);
				showSnackbar("Restore all failed", { severity: "error" });
			}
		},
		[dispatch, files, showSnackbar, effectiveReadOnly],
	);

	const doDeleteSet = React.useCallback(async () => {
		if (effectiveReadOnly) return;
		setDeleteSetOpen(false);
		try {
			await Promise.all((files || []).map((f) => dispatch(deleteOtelBackupFile(f.name)).unwrap()));
			await dispatch(fetchOtelConfigList());
		} catch (e) {
			console.error("Delete backup set failed:", e);
			showSnackbar("Delete backup set failed", { severity: "error" });
		}
	}, [dispatch, files, showSnackbar, effectiveReadOnly]);

	const groupItemId = idPrefix ? `${idPrefix}:__backup_set__/${id}` : `__backup_set__/${id}`;

	return (
		<>
			<TreeItem
				itemId={groupItemId}
				label={<BackupGroupLabel id={id} onMenuClick={!effectiveReadOnly ? openMenu : null} />}
				slotProps={{ content: { sx: { width: "100%" } } }}
			>
				{files.map((f) => (
					<OtelFileTreeItem
						key={f.name}
						file={f}
						itemId={idPrefix ? `${idPrefix}:${f.name}` : f.name}
						labelName={f.displayName}
						isDirty={!!dirtyByName?.[f.name]}
						validation={filesByName?.[f.name]?.validation}
						onSelect={onSelect}
						onRename={onRename}
						onDelete={onDelete}
						isSelected={selectedName === f.name}
					/>
				))}
			</TreeItem>

			<Menu
				anchorEl={menuAnchor}
				open={Boolean(menuAnchor)}
				onClose={closeMenu}
				disableRestoreFocus
				disableAutoFocusItem
				anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
				transformOrigin={{ vertical: "top", horizontal: "right" }}
			>
				<MenuItem onClick={askRestoreAll} disabled={effectiveReadOnly}>
					<RestoreIcon fontSize="small" sx={{ mr: 1 }} />
					Restore all
				</MenuItem>
				<MenuItem onClick={askDeleteSet} disabled={effectiveReadOnly}>
					<DeleteIcon fontSize="small" sx={{ mr: 1 }} />
					Delete backup
				</MenuItem>
			</Menu>

			<QuestionDialog
				open={restoreAllOpen}
				title="Restore entire backup"
				question={
					"Do you want to overwrite existing files?\n\n" +
					"• Overwrite: restore into original filenames (destructive)\n" +
					"• Restore as copies: keep originals and create .restored-<timestamp> files"
				}
				onClose={() => setRestoreAllOpen(false)}
				actionButtons={[
					{ btnTitle: "Cancel", callback: () => setRestoreAllOpen(false), autoFocus: true },
					{
						btnTitle: "Restore as copies",
						btnVariant: "contained",
						callback: () => doRestoreAll(false),
						disabled: effectiveReadOnly,
					},
					{
						btnTitle: "Overwrite all",
						btnColor: "error",
						btnVariant: "contained",
						callback: () => doRestoreAll(true),
						disabled: effectiveReadOnly,
					},
				]}
			/>

			<QuestionDialog
				open={deleteSetOpen}
				title="Delete backup"
				question="Are you sure you want to delete this entire backup set? This action cannot be undone."
				onClose={() => setDeleteSetOpen(false)}
				actionButtons={[
					{ btnTitle: "Cancel", callback: () => setDeleteSetOpen(false), autoFocus: true },
					{
						btnTitle: "Delete",
						btnColor: "error",
						btnVariant: "contained",
						callback: () => doDeleteSet(),
						disabled: effectiveReadOnly,
					},
				]}
			/>
		</>
	);
}
