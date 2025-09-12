import React from "react";
import { Typography, CircularProgress } from "@mui/material";
import { useAppSelector } from "../hooks/store";

export default function StatusText({ sx }) {
	const { data, loading, error } = useAppSelector((s) => s.applicationStatus);

	let status = String(data?.status ?? "UNKNOWN").toUpperCase();
	let color = "warning.main";

	if (status === "UP") color = "success.main";
	else if (status === "DOWN") color = "error.main";

	return (
		<Typography
			variant="body2"
			sx={{
				display: "flex",
				alignItems: "center",
				gap: 0.5,
				color,
				fontWeight: 800,
				fontSize: "1.1rem",
				letterSpacing: "0.5px",
				...sx,
			}}
		>
			{loading && <CircularProgress size={14} thickness={4} />}
			{error ? "ERROR" : status}
		</Typography>
	);
}
