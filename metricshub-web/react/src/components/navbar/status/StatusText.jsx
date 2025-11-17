import React from "react";
import { Box, CircularProgress, Tooltip } from "@mui/material";
import { useAppSelector } from "../../../hooks/store";
import WifiIcon from "@mui/icons-material/Wifi";

export default function StatusText({ sx }) {
	const { data, loading } = useAppSelector((s) => s.applicationStatus);
	const status = String(data?.status ?? "UNKNOWN").toUpperCase();

	let color = "warning.main";
	if (status === "UP") color = "success.main";
	else if (status === "DOWN") color = "text.disabled";

	return (
		<Box
			sx={{
				display: "flex",
				alignItems: "center",
				...sx,
			}}
		>
			<Tooltip title={`Application Status: ${status}`}>
				<Box sx={{ width: 26, height: 26, display: "grid", placeItems: "center" }}>
					{loading ? (
						<CircularProgress size={18} thickness={4} />
					) : (
						<WifiIcon sx={{ color, fontSize: 26 }} />
					)}
				</Box>
			</Tooltip>
		</Box>
	);
}
