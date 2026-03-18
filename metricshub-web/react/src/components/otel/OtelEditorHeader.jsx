import * as React from "react";
import { Stack, Typography, Button, Box, Chip } from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import PushPinIcon from "@mui/icons-material/PushPin";
import DoneAllIcon from "@mui/icons-material/DoneAll";
import RestartAltIcon from "@mui/icons-material/RestartAlt";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import { useAppSelector } from "../../hooks/store";
import { isBackupFileName } from "../../utils/backup-names";
import { getFileType } from "../../utils/file-type-utils";
import FileTypeIcon from "../config/tree/icons/FileTypeIcons";
import { otelCollectorApi } from "../../api/config/otel-collector-api";
import { useSnackbar } from "../../hooks/use-snackbar";

/**
 * Editor header for OTEL configuration: file name, save/apply, restart collector, toggle logs.
 */
export default function OtelEditorHeader({
	selected,
	saving,
	onSave,
	onApply,
	isReadOnly = false,
	logsOpen = false,
	onToggleLogs,
	onOpenLogs,
}) {
	const { show: showSnackbar } = useSnackbar();
	const [restarting, setRestarting] = React.useState(false);
	const dirtyByName = useAppSelector((s) => s.otelConfig.dirtyByName) ?? {};
	const filesByName = useAppSelector((s) => s.otelConfig.filesByName) ?? {};
	const isDirty = !!dirtyByName?.[selected];
	const fileValidation = selected ? filesByName[selected]?.validation : null;
	const hasErrors = !!(fileValidation && fileValidation.valid === false);
	const isBackup = !!(selected && isBackupFileName(selected));
	const fileType = selected ? getFileType(selected) : "file";

	const isDraft = selected && selected.endsWith(".draft");
	const displayName = isDraft
		? selected.replace(/\.draft$/, "")
		: (selected ?? "Select a file to edit");

	const handleRestart = React.useCallback(async () => {
		if (isReadOnly) return;
		setRestarting(true);
		try {
			await otelCollectorApi.restart();
			showSnackbar("OpenTelemetry Collector restarted successfully", { severity: "success" });
		} catch (e) {
			showSnackbar(e?.message || "Restart failed", { severity: "error" });
		} finally {
			setRestarting(false);
		}
	}, [isReadOnly, showSnackbar]);

	const handleToggleLogs = React.useCallback(() => {
		if (!logsOpen && onOpenLogs) onOpenLogs();
		onToggleLogs?.();
	}, [logsOpen, onToggleLogs, onOpenLogs]);

	const nonPosErrors = !Array.isArray(fileValidation?.errors)
		? []
		: fileValidation.errors.filter((e) => {
				const ln = Number(e?.line);
				const col = Number(e?.column);
				return !Number.isFinite(ln) || !Number.isFinite(col) || ln <= 0 || col <= 0;
			});

	return (
		<>
			<Stack direction="row" alignItems="center" justifyContent="space-between">
				<Stack direction="row" alignItems="center" spacing={0}>
					{selected && <FileTypeIcon type={fileType} />}
					<Typography
						variant="subtitle1"
						sx={{ fontWeight: isDirty ? 510 : 500, transition: "color 0.4s ease" }}
					>
						{displayName}
					</Typography>
					{isDraft && (
						<Chip
							label="Draft"
							size="small"
							color="secondary"
							icon={<PushPinIcon style={{ fontSize: 14 }} />}
							sx={{
								height: 20,
								ml: 1,
								"& .MuiChip-label": { px: 0.5, fontSize: "0.7rem" },
								"& .MuiChip-icon": { ml: 0.5 },
							}}
						/>
					)}
					{isDirty && selected && (
						<Box
							component="span"
							aria-hidden
							sx={{
								width: 8,
								height: 8,
								borderRadius: "50%",
								bgcolor: (t) => (hasErrors ? t.palette.error.main : t.palette.warning.main),
								ml: 0.25,
								transition: "background-color 0.4s ease",
							}}
						/>
					)}
				</Stack>
				<Stack direction="row" spacing={1} alignItems="center">
					{isDraft && onApply && (
						<Button
							size="small"
							startIcon={<DoneAllIcon />}
							onClick={onApply}
							disabled={!selected || saving || isReadOnly}
							variant="contained"
							sx={{
								background: "linear-gradient(135deg, #167c4cff 0%, #45ce52 100%)",
								color: "#fff",
								"&:hover": {
									background: "linear-gradient(135deg, #115e3a 0%, #36a843 100%)",
								},
							}}
						>
							{saving ? "Applying..." : "Apply"}
						</Button>
					)}
					<Button
						size="small"
						startIcon={<SaveIcon />}
						onClick={onSave}
						disabled={!selected || isBackup || (!isDirty && !isDraft) || saving || isReadOnly}
						variant="contained"
						sx={{
							"&:not(.Mui-disabled)": {
								background: "linear-gradient(135deg, #0A58CA 0%, #267DF4 100%)",
								color: "#fff",
							},
							"&:hover:not(.Mui-disabled)": {
								background: "linear-gradient(135deg, #084298 0%, #0A58CA 100%)",
							},
						}}
					>
						{saving ? "Saving..." : "Save"}
					</Button>
					<Button
						size="small"
						variant="outlined"
						color="inherit"
						startIcon={<RestartAltIcon />}
						onClick={handleRestart}
						disabled={isReadOnly || restarting}
					>
						{restarting ? "Restarting\u2026" : "Restart OTEL Collector"}
					</Button>
					{onToggleLogs && (
						<Button
							size="small"
							variant="outlined"
							color="inherit"
							startIcon={logsOpen ? <ExpandLessIcon /> : <ExpandMoreIcon />}
							onClick={handleToggleLogs}
						>
							{logsOpen ? "Hide logs" : "View logs"}
						</Button>
					)}
				</Stack>
			</Stack>
			{nonPosErrors.length > 0 && (
				<Box
					sx={{
						color: (t) => t.palette.error.main,
						typography: "body2",
						whiteSpace: "pre-wrap",
						maxHeight: 96,
						overflow: "auto",
						mt: 0.5,
						transition: "color 0.4s ease",
					}}
				>
					{nonPosErrors.map((e) => e?.message || String(e)).join("\n")}
				</Box>
			)}
		</>
	);
}
