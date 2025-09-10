import React, { useEffect, useState } from "react";
import { Typography, CircularProgress } from "@mui/material";
import { statusApi } from "../api/auth";

export default function StatusText({ sx }) {
    const [status, setStatus] = useState(null);
    const [err, setErr] = useState(null);

    useEffect(() => {
        let alive = true;
        (async () => {
            try {
                const data = await statusApi.getStatus();
                if (!alive) return;
                const s = typeof data === "string" ? data : data?.status ?? "UNKNOWN";
                setStatus(String(s).toUpperCase());
            } catch (e) {
                if (alive) setErr(true);
            }
        })();
        return () => {
            alive = false;
        };
    }, []);

    if (err) {
        return (
            <Typography variant="body2" sx={{ color: "error.main", fontWeight: 700, fontSize: "1rem", ...sx }}>
                ERROR
            </Typography>
        );
    }

    if (!status) return <CircularProgress size={16} sx={{ ...sx }} />;

    const color =
        status === "UP" ? "success.main" :
            status === "DOWN" ? "error.main" :
                "warning.main";

    return (
        <Typography
            variant="body2"
            sx={{
                color,
                fontWeight: 800,
                fontSize: "1.1rem",
                letterSpacing: "0.5px",
                ...sx,
            }}
        >
            {status}
        </Typography>
    );
}
