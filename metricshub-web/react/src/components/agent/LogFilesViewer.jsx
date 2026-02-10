import React, { useEffect, useCallback, useMemo, memo, useState } from "react";
import {
	Box,
	Typography,
	Card,
	CardContent,
	IconButton,
	Tooltip,
	CircularProgress,
	Alert,
	Chip,
	Button,
	Stack,
} from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import RefreshIcon from "@mui/icons-material/Refresh";
import DownloadIcon from "@mui/icons-material/Download";
import DeleteIcon from "@mui/icons-material/Delete";
import DeleteSweepIcon from "@mui/icons-material/DeleteSweep";
import DescriptionIcon from "@mui/icons-material/Description";
import { useAppDispatch, useAppSelector } from "../../hooks/store";
import {
	fetchLogFiles,
	fetchLogContent,
	deleteLogFile,
	deleteAllLogFiles,
} from "../../store/thunks/log-files-thunks";
import { logFilesApi } from "../../api/logs";
import { dataGridSx } from "../explorer/views/common/table-styles";
import QuestionDialog from "../common/QuestionDialog";

/**
 * Format bytes to human readable string
 * @param {number} bytes
 * @returns {string}
 */
function formatBytes(bytes) {
	if (bytes === 0) return "0 B";
	const k = 1024;
	const sizes = ["B", "KB", "MB", "GB"];
	const i = Math.floor(Math.log(bytes) / Math.log(k));
	return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
}

/**
 * Format ISO date string to locale string
 * @param {string} isoString
 * @returns {string}
 */
function formatDate(isoString) {
	if (!isoString) return "-";
	try {
		return new Date(isoString).toLocaleString();
	} catch {
		return isoString;
	}
}

/**
 * Find the latest metricshub-agent-global log file from the list
 * @param {Array<{name:string, lastModificationTime:string}>} files
 * @returns {{name:string, lastModificationTime:string}|null}
 */
function findLatestGlobalLog(files) {
	const globalLogs = files.filter((f) => f.name.startsWith("metricshub-agent-global"));
	if (globalLogs.length === 0) return null;

	return globalLogs.reduce((latest, current) => {
		const latestTime = new Date(latest.lastModificationTime).getTime();
		const currentTime = new Date(current.lastModificationTime).getTime();
		return currentTime > latestTime ? current : latest;
	});
}

/**
 * LogFilesViewer component - displays log file content and a list of downloadable log files
 * @param {{ isReadOnly?: boolean }} props
 */
const LogFilesViewer = memo(({ isReadOnly = false }) => {
	const dispatch = useAppDispatch();

	// Redux state
	const files = useAppSelector((s) => s.logFiles?.list || []);
	const listLoading = useAppSelector((s) => s.logFiles?.listLoading);
	const listError = useAppSelector((s) => s.logFiles?.listError);
	const currentFileName = useAppSelector((s) => s.logFiles?.currentFileName);
	const content = useAppSelector((s) => s.logFiles?.content || "");
	const contentLoading = useAppSelector((s) => s.logFiles?.contentLoading);
	const deleting = useAppSelector((s) => s.logFiles?.deleting);

	// Dialog state
	const [deleteTarget, setDeleteTarget] = useState(null);
	const [deleteAllOpen, setDeleteAllOpen] = useState(false);

	// Initial load
	useEffect(() => {
		dispatch(fetchLogFiles()).then((action) => {
			if (fetchLogFiles.fulfilled.match(action)) {
				const data = action.payload || [];
				const latest = findLatestGlobalLog(data);
				if (latest) {
					dispatch(fetchLogContent({ fileName: latest.name }));
				}
			}
		});
	}, [dispatch]);

	// Refresh handler - refreshes both list and content
	const handleRefresh = useCallback(() => {
		dispatch(fetchLogFiles()).then((action) => {
			if (fetchLogFiles.fulfilled.match(action)) {
				const data = action.payload || [];
				const latest = findLatestGlobalLog(data);
				if (latest) {
					dispatch(fetchLogContent({ fileName: latest.name }));
				}
			}
		});
	}, [dispatch]);

	// Download handler
	const handleDownload = useCallback((fileName) => {
		const url = logFilesApi.getDownloadUrl(fileName);
		// Use a hidden anchor to trigger download
		const a = document.createElement("a");
		a.href = url;
		a.download = fileName;
		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);
	}, []);

	// Delete single file handler
	const handleDeleteFile = useCallback(
		(fileName) => {
			dispatch(deleteLogFile({ fileName }));
			setDeleteTarget(null);
		},
		[dispatch],
	);

	// Delete all files handler
	const handleDeleteAll = useCallback(() => {
		dispatch(deleteAllLogFiles());
		setDeleteAllOpen(false);
	}, [dispatch]);

	// Sorted files (most recent first)
	const sortedFiles = useMemo(() => {
		return [...files].sort((a, b) => {
			const timeA = new Date(a.lastModificationTime).getTime();
			const timeB = new Date(b.lastModificationTime).getTime();
			return timeB - timeA;
		});
	}, [files]);

	return (
		<Box>
			{/* Header */}
			<Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 2 }}>
				<Typography variant="h6">Log Files</Typography>
				<Tooltip title="Refresh logs">
					<span>
						<IconButton
							onClick={handleRefresh}
							disabled={listLoading || contentLoading}
							size="small"
						>
							<RefreshIcon />
						</IconButton>
					</span>
				</Tooltip>
			</Box>

			{/* Error Alert */}
			{listError && (
				<Alert severity="error" sx={{ mb: 2 }}>
					{listError}
				</Alert>
			)}

			{/* Log Content Viewer */}
			<Card variant="outlined" sx={{ mb: 3 }}>
				<CardContent sx={{ p: 0, "&:last-child": { pb: 0 } }}>
					{/* Viewer Header */}
					<Box
						sx={{
							display: "flex",
							alignItems: "center",
							justifyContent: "space-between",
							px: 2,
							py: 1,
							borderBottom: 1,
							borderColor: "divider",
							bgcolor: "background.default",
						}}
					>
						<Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
							<DescriptionIcon fontSize="small" color="action" />
							<Typography variant="body2" fontWeight="medium">
								{currentFileName || "No log file selected"}
							</Typography>
							{currentFileName && (
								<Chip label="Last 1 MB" size="small" variant="outlined" sx={{ ml: 1 }} />
							)}
						</Box>
						{contentLoading && <CircularProgress size={16} />}
					</Box>

					{/* Log Content */}
					<Box
						component="pre"
						sx={{
							m: 0,
							p: 2,
							maxHeight: 400,
							overflow: "auto",
							bgcolor: "grey.900",
							color: "grey.100",
							fontFamily: "monospace",
							fontSize: "0.75rem",
							lineHeight: 1.5,
							whiteSpace: "pre-wrap",
							wordBreak: "break-all",
						}}
					>
						{listLoading && !content ? "Loading..." : content || "No log content available"}
					</Box>
				</CardContent>
			</Card>

			{/* Log Files Table Header */}
			<Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
				<Typography variant="subtitle1">Available Log Files</Typography>
				{!isReadOnly && files.length > 0 && (
					<Button
						variant="outlined"
						color="error"
						size="small"
						startIcon={<DeleteSweepIcon />}
						onClick={() => setDeleteAllOpen(true)}
						disabled={deleting || listLoading}
					>
						Delete All
					</Button>
				)}
			</Stack>
			<DataGrid
				rows={sortedFiles.map((file) => ({ id: file.name, ...file }))}
				columns={[
					{
						field: "name",
						headerName: "File Name",
						flex: 2,
						renderCell: (params) => (
							<Typography
								variant="body2"
								sx={{
									fontFamily: "monospace",
									fontWeight: params.value === currentFileName ? "bold" : "normal",
								}}
							>
								{params.value}
							</Typography>
						),
					},
					{
						field: "size",
						headerName: "Size",
						flex: 1,
						align: "right",
						headerAlign: "right",
						valueFormatter: (value) => formatBytes(value),
					},
					{
						field: "lastModificationTime",
						headerName: "Last Modified",
						flex: 1.5,
						align: "right",
						headerAlign: "right",
						valueFormatter: (value) => formatDate(value),
					},
					{
						field: "actions",
						headerName: "Actions",
						width: isReadOnly ? 100 : 150,
						align: "center",
						headerAlign: "center",
						sortable: false,
						renderCell: (params) => (
							<Stack direction="row" spacing={0.5}>
								<Tooltip title={`Download ${params.row.name}`}>
									<IconButton
										size="small"
										onClick={(e) => {
											e.stopPropagation();
											handleDownload(params.row.name);
										}}
										color="primary"
									>
										<DownloadIcon fontSize="small" />
									</IconButton>
								</Tooltip>
								{!isReadOnly && (
									<Tooltip title={`Delete ${params.row.name}`}>
										<IconButton
											size="small"
											onClick={(e) => {
												e.stopPropagation();
												setDeleteTarget(params.row.name);
											}}
											color="error"
											disabled={deleting}
										>
											<DeleteIcon fontSize="small" />
										</IconButton>
									</Tooltip>
								)}
							</Stack>
						),
					},
				]}
				loading={listLoading && files.length === 0}
				disableRowSelectionOnClick
				pageSizeOptions={[5, 10, 25]}
				initialState={{
					pagination: { paginationModel: { pageSize: 5 } },
				}}
				density="compact"
				sx={dataGridSx}
				localeText={{
					noRowsLabel: "No log files found",
				}}
			/>

			{/* Delete Single File Confirmation Dialog */}
			<QuestionDialog
				open={Boolean(deleteTarget)}
				title="Delete Log File"
				question={`Are you sure you want to delete "${deleteTarget || ""}"? This action cannot be undone.`}
				onClose={() => setDeleteTarget(null)}
				actionButtons={[
					{ btnTitle: "Cancel", callback: () => setDeleteTarget(null), autoFocus: true },
					{
						btnTitle: "Delete",
						btnColor: "error",
						btnVariant: "contained",
						btnIcon: <DeleteIcon />,
						callback: () => handleDeleteFile(deleteTarget),
						disabled: deleting,
					},
				]}
			/>

			{/* Delete All Files Confirmation Dialog */}
			<QuestionDialog
				open={deleteAllOpen}
				title="Delete All Log Files"
				question={`Are you sure you want to delete all ${files.length} log file(s)? This action cannot be undone.`}
				onClose={() => setDeleteAllOpen(false)}
				actionButtons={[
					{ btnTitle: "Cancel", callback: () => setDeleteAllOpen(false), autoFocus: true },
					{
						btnTitle: "Delete All",
						btnColor: "error",
						btnVariant: "contained",
						btnIcon: <DeleteSweepIcon />,
						callback: handleDeleteAll,
						disabled: deleting,
					},
				]}
			/>
		</Box>
	);
});

LogFilesViewer.displayName = "LogFilesViewer";

export default LogFilesViewer;
