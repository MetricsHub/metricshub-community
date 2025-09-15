import React from "react";
import { Button } from "@mui/material";

export default function TestButton() {
	return (
		<Button
			variant="contained"
			disableRipple
			aria-hidden
			sx={{
				minWidth: 0,
				width: 24,
				height: 24,
				p: 0,
				borderRadius: 1,
				bgcolor: "error.main",
				"&:hover": { bgcolor: "error.main", opacity: 0.85 },
			}}
		/>
	);
}
