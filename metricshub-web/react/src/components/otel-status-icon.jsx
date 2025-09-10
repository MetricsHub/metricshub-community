import React, { useEffect, useState } from "react";
import { Box, CircularProgress, Tooltip } from "@mui/material";
import { statusApi } from "../api/auth";
import otelColor from "../assets/opentelemetry-icon-color.png";

export default function OtelStatusIcon({ sx }) {
    const [running, setRunning] = useState(null);

    useEffect(() => {
        let alive = true;
        (async () => {
            try {
                const data = await statusApi.getStatus();
                if (!alive) return;
                setRunning(Boolean(data?.otelCollectorRunning));
            } catch {
                if (alive) setRunning(false);
            }
        })();
        return () => {
            alive = false;
        };
    }, []);

    if (running === null) {
        return <CircularProgress size={16} sx={{ ...sx }} />;
    }

    return (
        <Tooltip title={`OpenTelemetry Collector: ${running ? "running" : "stopped"}`}>
            <Box sx={{ width: 24, height: 24, ...sx }}>
                <img
                    src={otelColor}
                    alt="OpenTelemetry status"
                    style={{
                        width: "100%",
                        height: "100%",
                        transition: "filter 0.3s ease",
                        filter: running ? "none" : "grayscale(100%) brightness(0.6)",
                        opacity: running ? 1 : 0.6,
                    }}
                />
            </Box>
        </Tooltip>
    );
}
