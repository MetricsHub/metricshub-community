import React, { useEffect, useCallback, useMemo, memo } from "react";
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
} from "@mui/material";
import { DataGrid } from "@mui/x-data-grid";
import RefreshIcon from "@mui/icons-material/Refresh";
import DownloadIcon from "@mui/icons-material/Download";
import DescriptionIcon from "@mui/icons-material/Description";
import { useAppDispatch, useAppSelector } from "../../hooks/store";
import { fetchLogFiles, fetchLogContent } from "../../store/thunks/log-files-thunks";
import { logFilesApi } from "../../api/logs";
import { dataGridSx } from "../explorer/views/common/table-styles";

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
 */
const LogFilesViewer = memo(() => {
    const dispatch = useAppDispatch();

    // Redux state
    const files = useAppSelector((s) => s.logFiles?.list || []);
    const listLoading = useAppSelector((s) => s.logFiles?.listLoading);
    const listError = useAppSelector((s) => s.logFiles?.listError);
    const currentFileName = useAppSelector((s) => s.logFiles?.currentFileName);
    const content = useAppSelector((s) => s.logFiles?.content || "");
    const contentLoading = useAppSelector((s) => s.logFiles?.contentLoading);

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

            {/* Log Files Table */}
            <Typography variant="subtitle1" sx={{ mb: 1 }}>
                Available Log Files
            </Typography>
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
                        headerName: "Download",
                        width: 100,
                        align: "center",
                        headerAlign: "center",
                        sortable: false,
                        renderCell: (params) => (
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
        </Box>
    );
});

LogFilesViewer.displayName = "LogFilesViewer";

export default LogFilesViewer;
