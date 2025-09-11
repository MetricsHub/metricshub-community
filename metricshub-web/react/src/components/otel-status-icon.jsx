import React from "react";
import { Box, CircularProgress, Tooltip } from "@mui/material";
import { useAppSelector } from "../hooks/store";
import otelColor from "../assets/opentelemetry-icon-color.png";

export default function OtelStatusIcon({ sx = {} }) {   // ✅ default {}
    const { data, loading, error } = useAppSelector((s) => s.applicationStatus);
    if (loading && !data) return <CircularProgress size={16} sx={sx} />;

    const running = Boolean(data?.otelCollectorRunning) && !error;

    return (
        <Tooltip title={`OpenTelemetry Collector: ${running ? "running" : "stopped"}`}>
            <Box sx={{ width: 24, height: 24, ...(sx || {}) }}>  {/* ✅ safe spread */}
                <img
                    src={otelColor}
                    alt="OpenTelemetry status"
                    style={{
                        width: "100%",
                        height: "100%",
                        transition: "filter 0.3s ease, opacity 0.3s ease",
                        filter: running ? "none" : "grayscale(100%) brightness(0.6)",
                        opacity: running ? 1 : 0.6,
                    }}
                />
            </Box>
        </Tooltip>
    );
}
