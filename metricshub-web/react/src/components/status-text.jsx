import React from "react";
import { Typography, CircularProgress } from "@mui/material";
import { useAppSelector } from "../hooks/store";

export default function StatusText({ sx }) {
    const { data, loading, error } = useAppSelector(s => s.applicationStatus);
    if (loading && !data) return <CircularProgress size={16} sx={sx} />;
    if (error && !data) return <Typography variant="body2" sx={{ color: "error.main", fontWeight: 700, ...sx }}>ERROR</Typography>;

    const status = String(data?.status ?? "UNKNOWN").toUpperCase();
    const color = status === "UP" ? "success.main" : status === "DOWN" ? "error.main" : "warning.main";

    return (
        <Typography variant="body2" sx={{ color, fontWeight: 800, fontSize: "1.1rem", letterSpacing: "0.5px", ...sx }}>
            {status}
        </Typography>
    );
}
