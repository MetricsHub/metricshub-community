// components/config/editor/VelocityTestResultPanel.jsx
import * as React from "react";
import { Box, Typography, IconButton, Stack, Alert } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import { useAppDispatch } from "../../../hooks/store";
import { validateConfig } from "../../../store/thunks/config-thunks";

const YamlEditor = React.lazy(() => import("../../yaml-editor/YamlEditor"));

/**
 * Read-only panel showing the result of a Velocity template test.
 * Displays the generated YAML content with validation support.
 * @param {{result?:string,error?:string,loading:boolean,yamlValidation?:object,onClose:()=>void}} props
 * @returns {JSX.Element}
 */
export default function VelocityTestResultPanel({
	result,
	error,
	loading,
	yamlValidation,
	onClose,
}) {
	const dispatch = useAppDispatch();

	// Validation function for the YamlEditor to display lint markers
	// Uses a synthetic .yaml filename to trigger YAML validation (not Velocity)
	const validateFn = React.useCallback(
		async (content) => {
			try {
				const res = await dispatch(validateConfig({ name: "generated.yaml", content })).unwrap();
				return res?.result ?? { valid: true };
			} catch {
				return { valid: true }; // fallback to no lint markers on error
			}
		},
		[dispatch],
	);

	if (loading) {
		return (
			<Box sx={{ p: 2, display: "flex", alignItems: "center", gap: 1 }}>
				<Typography variant="body2">Evaluating template...</Typography>
			</Box>
		);
	}

	// Show YAML validation errors if present
	const hasYamlErrors =
		yamlValidation && !yamlValidation.valid && Array.isArray(yamlValidation.errors);

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
				<>
					{hasYamlErrors && (
						<Alert severity="warning" sx={{ m: 1, whiteSpace: "pre-wrap" }}>
							<Typography variant="subtitle2" sx={{ mb: 0.5 }}>
								Generated YAML validation errors:
							</Typography>
							{yamlValidation.errors.map((err, idx) => (
								<Typography key={idx} variant="body2" component="div">
									{err.line ? `Line ${err.line}: ` : ""}
									{err.message || err.msg || String(err)}
								</Typography>
							))}
						</Alert>
					)}
					<Box sx={{ flex: 1, minHeight: 0 }}>
						<React.Suspense fallback={<Box sx={{ p: 2 }}>Loading viewer...</Box>}>
							<YamlEditor
								value={result}
								readOnly
								height="100%"
								fileName="generated.yaml"
								validateFn={validateFn}
							/>
						</React.Suspense>
					</Box>
				</>
			)}
		</Box>
	);
}
