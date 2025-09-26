import React, { useMemo, useState, useEffect, useCallback, useRef } from "react";
import { Box, Stack, Typography, IconButton, Tooltip, Chip } from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import { useTheme } from "@mui/material/styles";

import CodeMirror from "@uiw/react-codemirror";
import { yaml as cmYaml } from "@codemirror/lang-yaml";
import { history, historyKeymap, defaultKeymap } from "@codemirror/commands";
import { keymap } from "@codemirror/view";
import YAML from "yaml";

const LOCAL_STORAGE_KEY = "yaml-editor-doc";

const DEFAULT_YAML = `# Example
service:
  name: metricshub
  port: 8080
  enabled: true
`;

/**
 * YAML Editor component
 * Props:
 * - value?: string
 * - onChange?: (val: string) => void
 * - onSave?: (val: string) => void
 * - height?: CSS height (default "100%")
 * - readOnly?: boolean (default false)
 */
export default function YamlEditor({ value, onChange, onSave, height = "100%", readOnly = false }) {
	const theme = useTheme();

	const [doc, setDoc] = useState(() => {
		const stored = localStorage.getItem(LOCAL_STORAGE_KEY);
		return stored ?? value ?? DEFAULT_YAML;
	});

	const [validateResult, setValidateResult] = useState(null); // "ok" | "error" | null
	const [showValidateCue, setShowValidateCue] = useState(false);
	const debounceRef = useRef(null); // for live validation debounce

	// Sync external value if provided
	useEffect(() => {
		if (value != null) {
			setDoc(value);
			localStorage.setItem(LOCAL_STORAGE_KEY, value);
		}
	}, [value]);

	// Save to localStorage on every change
	useEffect(() => {
		localStorage.setItem(LOCAL_STORAGE_KEY, doc);
	}, [doc]);

	// Cleanup timers on unmount
	useEffect(() => {
		return () => {
			if (debounceRef.current) clearTimeout(debounceRef.current);
		};
	}, []);

	/**
	 * CodeMirror extensions, memoized to avoid re-creating on every render.
	 * Includes YAML language support, linting, history, and keymaps.
	 */
	const extensions = useMemo(() => {
		const km = [...defaultKeymap, ...historyKeymap];
		if (onSave) {
			km.unshift({
				key: "Mod-s",
				preventDefault: true,
				run: (view) => {
					onSave(view.state.doc.toString());
					return true;
				},
			});
		}
		return [cmYaml(), history(), keymap.of(km)];
	}, [onSave]);

	/**
	 * Handle document changes.
	 * Updates local state, performs live validation, and calls onChange prop if provided.
	 */
	const handleChange = useCallback(
		(val) => {
			setShowValidateCue(false);
			setDoc(val);
			onChange?.(val);
		},
		[onChange],
	);

	/** Runs actual validation (chip feedback) */
	const runValidation = useCallback(() => {
		try {
			if (doc.trim()) YAML.parse(doc);
			setValidateResult("ok");
		} catch {
			setValidateResult("error");
		}
		setShowValidateCue(true);
	}, [doc]);

	/** Debounce validation: run 1 second after user stops typing */
	useEffect(() => {
		if (debounceRef.current) clearTimeout(debounceRef.current);
		debounceRef.current = setTimeout(runValidation, 1000);
	}, [doc, runValidation]);

	/**
	 * Format the YAML document.
	 * Parses and re-serializes the YAML to ensure consistent formatting.
	 * If parsing fails, sets error state and shows validation cue.
	 */
	const handleFormat = useCallback(() => {
		try {
			const obj = doc.trim() ? YAML.parse(doc) : {};
			const formatted = YAML.stringify(obj, { indent: 2, lineWidth: 100 });
			setDoc(formatted);
			onChange?.(formatted);
		} catch {
			setValidateResult("error");
			setShowValidateCue(true);
		}
	}, [doc, onChange]);

	const handleSave = useCallback(() => {
		onSave?.(doc);
	}, [onSave, doc]);

	return (
		<Box sx={{ height, display: "flex", flexDirection: "column", minHeight: 0 }}>
			{/* Top toolbar */}
			<Stack direction="row" alignItems="center" spacing={1} sx={{ px: 1, py: 0.5 }}>
				<Typography variant="subtitle2" sx={{ flex: 1 }}>
					YAML Editor
				</Typography>

				{/* Feedback chip */}
				{showValidateCue && (
					<Chip
						size="small"
						label={validateResult === "ok" ? "Valid YAML" : "Invalid YAML"}
						color={validateResult === "ok" ? "success" : "error"}
						variant="filled"
						sx={{ mr: 0.5 }}
					/>
				)}

				{/* Format */}
				<Tooltip title="Format YAML">
					<IconButton size="small" onClick={handleFormat} aria-label="Format YAML">
						<AutoFixHighIcon />
					</IconButton>
				</Tooltip>

				{/* Save */}
				{onSave && (
					<Tooltip title="Save">
						<IconButton size="small" onClick={handleSave} aria-label="Save YAML">
							<SaveIcon />
						</IconButton>
					</Tooltip>
				)}
			</Stack>

			{/* Editor container */}
			<Box
				sx={{
					flex: 1,
					minHeight: 0,
					borderTop: 1,
					borderColor: theme.palette.divider,
					".cm-editor": { height: "100%" },
				}}
			>
				<CodeMirror
					value={doc}
					onChange={handleChange}
					extensions={extensions}
					editable={!readOnly}
					basicSetup={{ lineNumbers: true, highlightActiveLine: true, foldGutter: true }}
					theme={theme.palette.mode}
				/>
			</Box>
		</Box>
	);
}
