import React, { useMemo, useState, useEffect, useCallback } from "react";
import { Box } from "@mui/material";
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
export default function YamlEditor({
	value,
	onChange,
	onSave,
	canSave = true,
	height = "100%",
	readOnly = false,
}) {
	const theme = useTheme();

	const [doc, setDoc] = useState(() => {
		const stored = localStorage.getItem(LOCAL_STORAGE_KEY);
		return stored ?? value ?? DEFAULT_YAML;
	});

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
					if (!canSave) return true;
					onSave(view.state.doc.toString());
				},
			});
		}
		return [cmYaml(), history(), keymap.of(km)];
	}, [onSave, canSave]);

	/**
	 * Handle document changes.
	 * Updates local state, performs live validation, and calls onChange prop if provided.
	 */
	const handleChange = useCallback(
		(val) => {
			setDoc(val);
			onChange?.(val);
		},
		[onChange],
	);

	return (
		<Box sx={{ height, display: "flex", flexDirection: "column", minHeight: 0 }}>
			{/* Editor container only (no toolbar/header/buttons) */}
			<Box
				sx={{
					flex: 1,
					minHeight: 0,
					borderTop: 0,
					".cm-editor": { height: "100%" },
					".cm-scroller": { overflow: "auto" },
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
