// components/config/editor/VelocityTestResultPanel.jsx
import * as React from "react";
import { Box, Typography, IconButton, Stack, Alert } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";

const YamlEditor = React.lazy(() => import("../../yaml-editor/YamlEditor"));

/**
 * Read-only panel showing the result of a Velocity template test.
 * @param {{result?:string,error?:string,loading:boolean,onClose:()=>void}} props
 * @returns {JSX.Element}
 */
export default function VelocityTestResultPanel({ result, error, loading, onClose }) {
	if (loading) {
		return (
			<Box sx={{ p: 2, display: "flex", alignItems: "center", gap: 1 }}>
				<Typography variant="body2">Evaluating template...</Typography>
			</Box>
		);
	}

	return (
		<Box sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
			<Stack
				direction="row"
				alignItems="center"
				justifyContent="space-between"
				sx={{ px: 1.5, py: 0.5, borderBottom: 1, borderColor: "divider" }}
			>
				<Typography variant="subtitle2">Generated YAML Output</Typography>
				<IconButton size="small" onClick={onClose}>
					<CloseIcon fontSize="small" />
				</IconButton>
			</Stack>
			{error ? (
				<Alert severity="error" sx={{ m: 1, whiteSpace: "pre-wrap" }}>
					{error}
				</Alert>
			) : (
				<Box sx={{ flex: 1, minHeight: 0 }}>
					<React.Suspense fallback={<Box sx={{ p: 2 }}>Loading viewer...</Box>}>
						<YamlEditor value={result} readOnly height="100%" />
					</React.Suspense>
				</Box>
			)}
		</Box>
	);
}
