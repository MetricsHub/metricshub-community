import React from "react";
import { Box, CircularProgress, Tooltip } from "@mui/material";
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";
import { useAppSelector } from "../../../hooks/store";
import otelCollectorIcon from "../../../assets/opentelemetry-icon-color.svg";

// Define the possible statuses and their visual/tooltip representations
const OTEL_COLLECTOR_STATUSES = {
	running: {
		tooltip: "OpenTelemetry Collector: Running",
		showCross: false,
		grayscale: false,
	},
	disabled: {
		tooltip: "OpenTelemetry Collector: Disabled",
		showCross: false,
		grayscale: true,
	},
	errored: {
		tooltip: "OpenTelemetry Collector: Errored",
		showCross: true,
		grayscale: false,
	},
};

/**
 * OpenTelemetry status icon component
 * Renders nothing if the collector is not available/installed.
 */
export default function OtelStatusIcon({ sx = {} }) {
	const { data, loading } = useAppSelector((s) => s.applicationStatus);

	if (loading && !data) return <CircularProgress size={16} sx={sx} />;

	// Determine the OpenTelemetry Collector status
	const otelCollectorStatus = data?.otelCollectorStatus ?? null;

	if (!otelCollectorStatus || otelCollectorStatus === "not-installed") return null;

	// Determine visual/tooltip states
	let tooltip = OTEL_COLLECTOR_STATUSES[otelCollectorStatus]?.tooltip;
	let showCross = OTEL_COLLECTOR_STATUSES[otelCollectorStatus]?.showCross;
	let grayscale = OTEL_COLLECTOR_STATUSES[otelCollectorStatus]?.grayscale;

	return (
		<Tooltip title={tooltip}>
			<Box
				sx={{
					position: "relative",
					display: "inline-block",
					width: "1.8em",
					height: "1.8em",
					...sx,
				}}
			>
				{/* Base icon */}
				<Box
					component="img"
					src={otelCollectorIcon}
					sx={{
						width: "100%",
						height: "100%",
						transition: "filter 0.3s ease, opacity 0.3s ease",
						filter: grayscale ? "grayscale(100%) brightness(0.6)" : "none",
						opacity: grayscale ? 0.6 : 1,
						display: "block",
					}}
				/>

				{/* Small red cross badge when enabled but not running */}
				{showCross && (
					<Box
						sx={(t) => ({
							position: "absolute",
							right: -2,
							top: -2,
							width: "0.9em",
							height: "0.9em",
							borderRadius: "50%",
							backgroundColor: t.palette.error.main,
							color: t.palette.error.contrastText,
							display: "grid",
							placeItems: "center",
							boxShadow: "0 0 0 1px rgba(0,0,0,0.15)",
						})}
						aria-hidden
					>
						<CloseRoundedIcon sx={{ fontSize: "0.7em", lineHeight: 1 }} />
					</Box>
				)}
			</Box>
		</Tooltip>
	);
}
