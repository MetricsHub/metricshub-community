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
import { prettifyKey } from "../../../utils/text-prettifier";

// Fields to display in the Agent Info section, in order
const AGENT_INFO_FIELDS = [
	{ key: "osType", label: "OS Type" },
	{ key: "name", label: "Name" },
	{ key: "ccVersion", label: "CC Version" },
	{ key: "version", label: "Version" },
	{ key: "hostType", label: "Host Type" },
	{ key: "agentHostName", label: "Agent Host Name" },
	{ key: "hostName", label: "Host Name" },
	{ key: "serviceName", label: "Service Name" },
	{ key: "buildDate", label: "Build Date" },
	{ key: "buildNumber", label: "Build Number" },
];

// Format value for display
const formatValue = (v) =>
	v == null ? "—" : typeof v === "boolean" ? (v ? "Yes" : "No") : String(v);

export default function StatusDetailsMenu() {
	const [anchorEl, setAnchorEl] = useState(null);
	const { data, loading, error } = useAppSelector((s) => s.applicationStatus);
	const open = Boolean(anchorEl);

	const agentInfo = data?.agentInfo ?? null;

	// Keys to exclude from the Overview section
	const EXCLUDE_KEYS = new Set(["agentInfo", "status", "otelCollectorStatus"]);

	const rest =
		data && typeof data === "object"
			? Object.fromEntries(Object.entries(data).filter(([k]) => !EXCLUDE_KEYS.has(k)))
			: null;

	const curatedKeys = new Set(AGENT_INFO_FIELDS.map((f) => f.key));
	const additionalAgentInfoEntries = agentInfo
		? Object.entries(agentInfo).filter(([k, v]) => !curatedKeys.has(k) && v != null && v !== "")
		: [];

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
					{/* Loading */}
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
							{/* ======= Agent Info Section ======= */}
							{agentInfo && (
								<>
									<Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
										Agent info
									</Typography>

									{/* Curated (ordered) fields */}
									<List dense disablePadding>
										{AGENT_INFO_FIELDS.map(({ key, label }) => {
											const value = agentInfo[key];
											if (value == null || value === "") return null;
											return (
												<ListItem key={key} sx={{ py: 0.25 }}>
													<ListItemText
														primary={label}
														secondary={formatValue(value)}
														slotProps={{
															primary: { sx: { fontSize: "0.9rem", fontWeight: 600 } },
															secondary: { sx: { fontSize: "0.9rem" } },
														}}
													/>
												</ListItem>
											);
										})}
									</List>

									{/* Any extra keys not in the curated list */}
									{additionalAgentInfoEntries.length > 0 && (
										<>
											<Divider sx={{ my: 1 }} />
											<Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
												More
											</Typography>
											<List dense disablePadding>
												{additionalAgentInfoEntries.map(([k, v]) => (
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
										</>
									)}

									<Divider sx={{ my: 1 }} />
								</>
							)}

							{/* ======= Overview Section ======= */}
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
