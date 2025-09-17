import React from "react";
import { Box, CircularProgress, Tooltip } from "@mui/material";
import { useAppSelector } from "../../hooks/store";
import opentelemetryCollectorIcon from "../../assets/opentelemetry-icon-color.svg";

/**
 * OpenTelemetry status icon component
 * @param {object} props - Component props
 * @param {object} props.sx - MUI sx prop for styling
 * @returns JSX.Element
 */
export default function OtelStatusIcon({ sx = {} }) {
	const { data, loading, error } = useAppSelector((s) => s.applicationStatus);
	if (loading && !data) return <CircularProgress size={16} sx={sx} />;

	const running = Boolean(data?.otelCollectorRunning) && !error;

	return (
		<Tooltip title={`OpenTelemetry Collector: ${running ? "running" : "stopped"}`}>
			<Box sx={{ width: "1.2em", height: "1.2em", ...(sx || {}) }}>
				{" "}
				<img
					src={opentelemetryCollectorIcon}
					alt="OpenTelemetry Collector Status"
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
