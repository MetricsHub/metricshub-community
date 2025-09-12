import React from "react";
import { Button, Tooltip } from "@mui/material";
import buildInfo from "../build-info.json";

export default function TestButton() {
	const built = new Date(buildInfo.buildTime);
	const time = built.toLocaleTimeString([], {
		hour: "2-digit",
		minute: "2-digit",
		second: "2-digit",
	});

	return (
		<Tooltip title={`Built: ${built.toLocaleString()}`}>
			<Button
				variant="contained"
				sx={{
					backgroundColor: "red",
					minWidth: 120,
					fontWeight: "bold",
					color: "#fff",
					"&:hover": { backgroundColor: "red", opacity: 0.85 },
				}}
			>
				{time}
			</Button>
		</Tooltip>
	);
}
