import * as React from "react";
import { SimpleTreeView, TreeItem, treeItemClasses } from "@mui/x-tree-view";
import FileTypeIcon from "./icons/FileTypeIcons";
import { Stack, Box, IconButton, Menu, MenuItem } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import BackupIcon from "@mui/icons-material/Backup";
import DownloadIcon from "@mui/icons-material/Download";
import FileTreeItem from "./FileTreeItem";
import BackupSetNode from "./BackupSetNode";
import OtelFileTreeItem from "../../otel/tree/OtelFileTreeItem";
import OtelBackupSetNode from "../../otel/tree/OtelBackupSetNode";
import { parseBackupFileName } from "../../../utils/backup-names";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { useAuth } from "../../../hooks/use-auth";
import { createConfigBackup } from "../../../store/thunks/config-thunks";
import { createOtelConfigBackup } from "../../../store/thunks/otel-config-thunks";
import { downloadAllConfigs } from "../../../services/download-service";
import { downloadAllOtelConfigs } from "../../../services/download-service";

const CONFIG_ROOT = "config";
const CONFIG_BACKUP_ROOT = "config_backup";
const OTEL_ROOT = "otel";
const OTEL_BACKUP_ROOT = "otel_backup";

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

/**
 * Unified configuration tree with two roots: "config" (monitoring) and "otel" (OTEL collector).
 * Selection uses prefixed ids: "config:fileName" and "otel:fileName".
 */
export default function UnifiedConfigTree({
	selectedRepo,
	selectedName,
	onSelect,
	onRenameConfig,
	onDeleteConfig,
	onMakeDraftConfig,
	onRenameOtel,
	onDeleteOtel,
	onMakeDraftOtel,
}) {
	const dispatch = useAppDispatch();
	const { user } = useAuth();
	const isReadOnly = user?.role === "ro";

	const configList = useAppSelector((s) => s.config.list);
	const configDirtyByName = useAppSelector((s) => s.config.dirtyByName) ?? {};
	const configFilesByName = useAppSelector((s) => s.config.filesByName) ?? {};

	const otelList = useAppSelector((s) => s.otelConfig.list);
	const otelDirtyByName = useAppSelector((s) => s.otelConfig.dirtyByName) ?? {};
	const otelFilesByName = useAppSelector((s) => s.otelConfig.filesByName) ?? {};

	const selectedIds = React.useMemo(
		() => (selectedRepo && selectedName ? [`${selectedRepo}:${selectedName}`] : []),
		[selectedRepo, selectedName],
	);

	const { configFiles, configBackupsBySet } = React.useMemo(() => {
		const cfg = [];
		const groups = {};
		for (const f of configList || []) {
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
		return { configFiles: cfg, configBackupsBySet: groups };
	}, [configList]);

	const { otelFiles, otelBackupsBySet } = React.useMemo(() => {
		const cfg = [];
		const groups = {};
		for (const f of otelList || []) {
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
		return { otelFiles: cfg, otelBackupsBySet: groups };
	}, [otelList]);

	const [configMenuAnchor, setConfigMenuAnchor] = React.useState(null);
	const [otelMenuAnchor, setOtelMenuAnchor] = React.useState(null);

	const handleConfigBackupAll = React.useCallback(async () => {
		if (isReadOnly) return;
		setConfigMenuAnchor(null);
		try {
			await dispatch(createConfigBackup({ kind: "all" })).unwrap();
		} catch (e) {
			console.error("Config backup all failed:", e);
		}
	}, [dispatch, isReadOnly]);

	const handleConfigDownloadAll = React.useCallback(async () => {
		if (isReadOnly) return;
		setConfigMenuAnchor(null);
		await downloadAllConfigs(configList);
	}, [configList, isReadOnly]);

	const handleOtelBackupAll = React.useCallback(async () => {
		if (isReadOnly) return;
		setOtelMenuAnchor(null);
		try {
			await dispatch(createOtelConfigBackup({ kind: "all" })).unwrap();
		} catch (e) {
			console.error("OTEL backup all failed:", e);
		}
	}, [dispatch, isReadOnly]);

	const handleOtelDownloadAll = React.useCallback(async () => {
		if (isReadOnly) return;
		setOtelMenuAnchor(null);
		await downloadAllOtelConfigs(otelList);
	}, [otelList, isReadOnly]);

	const onTreeSelectionChange = React.useCallback(
		(_, id) => {
			if (
				!id ||
				id === CONFIG_ROOT ||
				id === CONFIG_BACKUP_ROOT ||
				id === OTEL_ROOT ||
				id === OTEL_BACKUP_ROOT
			)
				return;
			if (typeof id === "string" && id.includes("__backup_set__/")) return;
			const idx = id.indexOf(":");
			if (idx === -1) return;
			const repo = id.slice(0, idx);
			const name = id.slice(idx + 1);
			if (repo === "config" || repo === "otel") onSelect(repo, name);
		},
		[onSelect],
	);

	return (
		<Stack sx={{ p: 0 }}>
			<SimpleTreeView
				defaultExpandedItems={[CONFIG_ROOT, CONFIG_BACKUP_ROOT, OTEL_ROOT, OTEL_BACKUP_ROOT]}
				aria-label="Configuration files"
				multiSelect={false}
				selectedItems={selectedIds}
				onSelectedItemsChange={onTreeSelectionChange}
				sx={{
					[`& .${treeItemClasses.content}`]: { py: 0.25, width: "100%" },
					[`& .${treeItemClasses.label}`]: { flex: 1, minWidth: 0 },
				}}
			>
				{/* Config root */}
				<TreeItem
					itemId={CONFIG_ROOT}
					label={
						<FolderLabel
							name="config"
							onMenuClick={!isReadOnly ? (e) => setConfigMenuAnchor(e.currentTarget) : null}
						/>
					}
					sx={{ "& .MuiTreeItem-label": { fontWeight: 400 } }}
				>
					{configFiles.map((f) => (
						<FileTreeItem
							key={f.name}
							file={f}
							itemId={`config:${f.name}`}
							isDirty={!!configDirtyByName[f.name]}
							validation={configFilesByName[f.name]?.validation}
							onSelect={(name) => onSelect("config", name)}
							onRename={onRenameConfig}
							onDelete={onDeleteConfig}
							onMakeDraft={onMakeDraftConfig}
							isReadOnly={isReadOnly}
							isSelected={selectedRepo === "config" && selectedName === f.name}
						/>
					))}
					<TreeItem
						itemId={CONFIG_BACKUP_ROOT}
						label={<FolderLabel name="backup" />}
						sx={{ "& .MuiTreeItem-label": { fontWeight: 400 } }}
					>
						{Object.keys(configBackupsBySet)
							.sort((a, b) => b.localeCompare(a))
							.map((id) => (
								<BackupSetNode
									key={id}
									id={id}
									files={configBackupsBySet[id]}
									dirtyByName={configDirtyByName}
									filesByName={configFilesByName}
									onSelect={(name) => onSelect("config", name)}
									onRename={onRenameConfig}
									onDelete={onDeleteConfig}
									isReadOnly={isReadOnly}
									selectedName={selectedRepo === "config" ? selectedName : null}
									idPrefix="config"
								/>
							))}
					</TreeItem>
				</TreeItem>

				{/* Otel root */}
				<TreeItem
					itemId={OTEL_ROOT}
					label={
						<FolderLabel
							name="otel"
							onMenuClick={!isReadOnly ? (e) => setOtelMenuAnchor(e.currentTarget) : null}
						/>
					}
					sx={{ "& .MuiTreeItem-label": { fontWeight: 400 } }}
				>
					{otelFiles.map((f) => (
						<OtelFileTreeItem
							key={f.name}
							file={f}
							itemId={`otel:${f.name}`}
							isDirty={!!otelDirtyByName[f.name]}
							validation={otelFilesByName[f.name]?.validation}
							onSelect={(name) => onSelect("otel", name)}
							onRename={onRenameOtel}
							onDelete={onDeleteOtel}
							onMakeDraft={onMakeDraftOtel}
							isReadOnly={isReadOnly}
							isSelected={selectedRepo === "otel" && selectedName === f.name}
						/>
					))}
					<TreeItem
						itemId={OTEL_BACKUP_ROOT}
						label={<FolderLabel name="otel-backup" />}
						sx={{ "& .MuiTreeItem-label": { fontWeight: 400 } }}
					>
						{Object.keys(otelBackupsBySet)
							.sort((a, b) => b.localeCompare(a))
							.map((id) => (
								<OtelBackupSetNode
									key={id}
									id={id}
									files={otelBackupsBySet[id]}
									dirtyByName={otelDirtyByName}
									filesByName={otelFilesByName}
									onSelect={(name) => onSelect("otel", name)}
									onRename={onRenameOtel}
									onDelete={onDeleteOtel}
									isReadOnly={isReadOnly}
									selectedName={selectedRepo === "otel" ? selectedName : null}
									idPrefix="otel"
								/>
							))}
					</TreeItem>
				</TreeItem>
			</SimpleTreeView>

			<Menu
				anchorEl={configMenuAnchor}
				open={Boolean(configMenuAnchor)}
				onClose={() => setConfigMenuAnchor(null)}
				anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
				transformOrigin={{ vertical: "top", horizontal: "right" }}
			>
				<MenuItem onClick={handleConfigBackupAll} disabled={isReadOnly}>
					<BackupIcon fontSize="small" sx={{ mr: 1 }} />
					Backup all
				</MenuItem>
				<MenuItem onClick={handleConfigDownloadAll} disabled={isReadOnly}>
					<DownloadIcon fontSize="small" sx={{ mr: 1 }} />
					Download all
				</MenuItem>
			</Menu>
			<Menu
				anchorEl={otelMenuAnchor}
				open={Boolean(otelMenuAnchor)}
				onClose={() => setOtelMenuAnchor(null)}
				anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
				transformOrigin={{ vertical: "top", horizontal: "right" }}
			>
				<MenuItem onClick={handleOtelBackupAll} disabled={isReadOnly}>
					<BackupIcon fontSize="small" sx={{ mr: 1 }} />
					Backup all
				</MenuItem>
				<MenuItem onClick={handleOtelDownloadAll} disabled={isReadOnly}>
					<DownloadIcon fontSize="small" sx={{ mr: 1 }} />
					Download all
				</MenuItem>
			</Menu>
		</Stack>
	);
}
