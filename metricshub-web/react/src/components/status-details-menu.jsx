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
import { statusApi } from "../api/auth";

// Pretty-print keys: dot/underscore/camelCase -> "Title Case"
// Special-cases common acronyms.
function prettifyKey(key = "") {
    const ACRONYMS = new Set(["id", "url", "api", "cpu", "gpu", "os"]);
    let k = String(key)
        .replace(/([a-z0-9])([A-Z])/g, "$1 $2")   // camelCase -> space
        .replace(/[._-]+/g, " ")                  // dots/underscores/hyphens -> space
        .trim()
        .toLowerCase();
    // Title case, preserving acronyms
    k = k
        .split(/\s+/)
        .map(w => (ACRONYMS.has(w) ? w.toUpperCase() : w.charAt(0).toUpperCase() + w.slice(1)))
        .join(" ");
    return k;
}

function formatValue(v) {
    if (v === null || v === undefined) return "—";
    if (typeof v === "boolean") return v ? "Yes" : "No";
    if (typeof v === "object") return JSON.stringify(v); // fallback for unexpected nested shapes
    return String(v);
}

export default function StatusDetailsMenu() {
    const [anchorEl, setAnchorEl] = useState(null);
    const [data, setData] = useState(null);
    const [err, setErr] = useState(null);
    const [loading, setLoading] = useState(false);
    const open = Boolean(anchorEl);

    const handleOpen = (e) => {
        setAnchorEl(e.currentTarget);
        // lazy-load on first open
        if (!data && !loading && !err) {
            (async () => {
                try {
                    setLoading(true);
                    const res = await statusApi.getStatus();
                    // Strip "status"
                    const payload =
                        res && typeof res === "object" && !Array.isArray(res)
                            ? (({ status, ...rest }) => rest)(res)
                            : { payload: res };
                    setData(payload);
                } catch (e) {
                    setErr(e?.message || "Failed to load details");
                } finally {
                    setLoading(false);
                }
            })();
        }
    };

    const handleClose = () => setAnchorEl(null);

    const renderSection = (title, obj) => (
        <>
            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 0.5 }}>
                {title}
            </Typography>
            <List dense disablePadding>
                {Object.entries(obj).map(([key, value]) => (
                    <ListItem key={key} sx={{ py: 0.25 }}>
                        <ListItemText
                            primary={prettifyKey(key)}
                            secondary={formatValue(value)}
                            primaryTypographyProps={{ fontSize: "0.9rem", fontWeight: 600 }}
                            secondaryTypographyProps={{ fontSize: "0.9rem" }}
                        />
                    </ListItem>
                ))}
            </List>
        </>
    );

    const agentInfo = data?.agentInfo;
    const rest =
        data && typeof data === "object"
            ? Object.fromEntries(
                Object.entries(data).filter(
                    ([k]) => k !== "agentInfo" && k !== "otelCollectorRunning"
                )
            )
            : null;

    return (
        <>
            <Tooltip title="System info">
                <IconButton size="small" onClick={handleOpen} aria-label="backend details">
                    <InfoOutlinedIcon fontSize="small" />
                </IconButton>
            </Tooltip>

            <Menu
                anchorEl={anchorEl}
                open={open}
                onClose={handleClose}
                PaperProps={{
                    sx: {
                        maxWidth: 460,
                        width: { xs: "92vw", sm: 460 },
                        p: 2,
                    },
                }}
            >
                <Box sx={{ minWidth: 260, maxHeight: 440, overflow: "auto" }}>
                    {loading ? (
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                            <CircularProgress size={18} />
                            <Typography variant="body2">Loading…</Typography>
                        </Box>
                    ) : err ? (
                        <Typography variant="body2" color="error.main">
                            {err}
                        </Typography>
                    ) : data ? (
                        <>
                            {agentInfo && renderSection("Agent info", agentInfo)}
                            {agentInfo && <Divider sx={{ my: 1 }} />}
                            {rest && renderSection("Overview", rest)}
                        </>
                    ) : (
                        <Typography variant="body2">No data available</Typography>
                    )}
                </Box>
            </Menu>
        </>
    );
}
