import * as React from "react";
import {
	Box,
	Card,
	CardContent,
	Typography,
	Chip,
	CircularProgress,
	IconButton,
	Tooltip,
	Alert,
	Select,
	FormControl,
	InputLabel,
	MenuItem,
} from "@mui/material";
import DescriptionIcon from "@mui/icons-material/Description";
import RefreshIcon from "@mui/icons-material/Refresh";

const TAIL_OPTIONS = [100, 200, 500, 1000];

/**
 * OTel Collector log panel with the same visual format as the Agent page Log Files viewer:
 * Card with header (icon, title, "Last N lines" chip, refresh) and dark monospace log content.
 */
export default function OtelCollectorLogPanel({
	logs,
	logsLoading,
	logsError,
	tailLines,
	onTailLinesChange,
	onRefresh,
}) {
	return (
		<Box sx={{ height: "100%", display: "flex", flexDirection: "column", minHeight: 0, p: 1.5 }}>
			{logsError && (
				<Alert severity="error" sx={{ mb: 1 }}>
					{logsError}
				</Alert>
			)}
			<Card
				variant="outlined"
				sx={{ flex: 1, display: "flex", flexDirection: "column", minHeight: 0 }}
			>
				<CardContent
					sx={{
						p: 0,
						flex: 1,
						display: "flex",
						flexDirection: "column",
						minHeight: 0,
						"&:last-child": { pb: 0 },
					}}
				>
					{/* Viewer Header - same structure as LogFilesViewer */}
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
						<Box sx={{ display: "flex", alignItems: "center", gap: 1, flexWrap: "wrap" }}>
							<DescriptionIcon fontSize="small" color="action" />
							<Typography variant="body2" fontWeight="medium">
								OTel Collector log
							</Typography>
							<Chip
								label={`Last ${tailLines} lines`}
								size="small"
								variant="outlined"
								sx={{ ml: 1 }}
							/>
							<FormControl size="small" sx={{ minWidth: 90, ml: 1 }}>
								<InputLabel>Lines</InputLabel>
								<Select
									value={tailLines}
									label="Lines"
									onChange={(e) => onTailLinesChange(Number(e.target.value))}
									disabled={logsLoading}
								>
									{TAIL_OPTIONS.map((n) => (
										<MenuItem key={n} value={n}>
											{n}
										</MenuItem>
									))}
								</Select>
							</FormControl>
							<Tooltip title="Refresh logs">
								<span>
									<IconButton
										size="small"
										onClick={onRefresh}
										disabled={logsLoading}
										aria-label="Refresh logs"
									>
										<RefreshIcon />
									</IconButton>
								</span>
							</Tooltip>
						</Box>
						{logsLoading && <CircularProgress size={16} />}
					</Box>

					{/* Log Content - same styles as LogFilesViewer */}
					<Box
						component="pre"
						sx={{
							m: 0,
							p: 2,
							flex: 1,
							minHeight: 0,
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
						{logsLoading && !logs ? "Loading..." : logs || "No log content available"}
					</Box>
				</CardContent>
			</Card>
		</Box>
	);
}
