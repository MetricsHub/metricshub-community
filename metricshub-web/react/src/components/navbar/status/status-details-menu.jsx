import React, { useState } from "react";
import {
	IconButton,
	Menu,
	Box,
	CircularProgress,
	Tooltip,
	Typography,
	List,
	ListItem,
	ListItemText,
	Divider,
} from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { useAppSelector } from "../../../hooks/store";

// key prettifier
function prettifyKey(key = "") {
	const ACR = new Set(["id", "url", "api", "cpu", "gpu", "os", "otel", "cc"]);
	let k = String(key)
		.replace(/([a-z0-9])([A-Z])/g, "$1 $2")
		.replace(/[._-]+/g, " ")
		.trim()
		.toLowerCase();
	return k
		.split(/\s+/)
		.map((w) => (ACR.has(w) ? w.toUpperCase() : w.charAt(0).toUpperCase() + w.slice(1)))
		.join(" ");
}
const formatValue = (v) =>
	v == null ? "—" : typeof v === "boolean" ? (v ? "Yes" : "No") : String(v);

export default function StatusDetailsMenu() {
	const [anchorEl, setAnchorEl] = useState(null);
	const { data, loading, error } = useAppSelector((s) => s.applicationStatus);
	const open = Boolean(anchorEl);

	const agentInfo = data?.agentInfo ?? null;
	const rest =
		data && typeof data === "object"
			? Object.fromEntries(
					Object.entries(data).filter(
						([k]) => k !== "agentInfo" && k !== "status" && k !== "otelCollectorRunning",
					),
				)
			: null;

	return (
		<>
			<Tooltip title="System Info">
				<IconButton
					size="small"
					onClick={(e) => setAnchorEl(e.currentTarget)}
					aria-label="system info"
				>
					<InfoOutlinedIcon fontSize="small" />
				</IconButton>
			</Tooltip>

			<Menu
				anchorEl={anchorEl}
				open={open}
				onClose={() => setAnchorEl(null)}
				slotProps={{ paper: { sx: { maxWidth: 460, width: { xs: "92vw", sm: 460 }, p: 2 } } }}
			>
				<Box sx={{ minWidth: 260, maxHeight: 440, overflow: "auto" }}>
					{loading && !data ? (
						<Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
							<CircularProgress size={18} />
							<Typography variant="body2">Loading…</Typography>
						</Box>
					) : error && !data ? (
						<Typography variant="body2" color="error.main">
							{error}
						</Typography>
					) : data ? (
						<>
							{agentInfo && (
								<>
									<Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
										Agent info
									</Typography>
									<List dense disablePadding>
										{Object.entries(agentInfo).map(([k, v]) => (
											<ListItem key={k} sx={{ py: 0.25 }}>
												<ListItemText
													primary={prettifyKey(k)}
													secondary={formatValue(v)}
													slotProps={{
														primary: { sx: { fontSize: "0.9rem", fontWeight: 600 } },
														secondary: { sx: { fontSize: "0.9rem" } },
													}}
												/>
											</ListItem>
										))}
									</List>
									<Divider sx={{ my: 1 }} />
								</>
							)}
							{rest && (
								<>
									<Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
										Overview
									</Typography>
									<List dense disablePadding>
										{Object.entries(rest).map(([k, v]) => (
											<ListItem key={k} sx={{ py: 0.25 }}>
												<ListItemText
													primary={prettifyKey(k)}
													secondary={formatValue(v)}
													primaryTypographyProps={{ fontSize: "0.9rem", fontWeight: 600 }}
													secondaryTypographyProps={{ fontSize: "0.9rem" }}
												/>
											</ListItem>
										))}
									</List>
								</>
							)}
						</>
					) : (
						<Typography variant="body2">No data available</Typography>
					)}
				</Box>
			</Menu>
		</>
	);
}
