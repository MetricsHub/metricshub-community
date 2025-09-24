import React, { useMemo, useState, useEffect, useCallback, useRef } from "react";
import { Box, Stack, Typography, IconButton, Tooltip } from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import { useTheme } from "@mui/material/styles";

import CodeMirror from "@uiw/react-codemirror";
import { yaml as cmYaml } from "@codemirror/lang-yaml";
import { linter, lintGutter, getDiagnostics /* , lintPanel */ } from "@codemirror/lint";
import { history, historyKeymap, defaultKeymap } from "@codemirror/commands";
import { keymap, EditorView, ViewPlugin, WidgetType, Decoration } from "@codemirror/view";
import { RangeSetBuilder } from "@codemirror/state";
import YAML from "yaml";

const LOCAL_STORAGE_KEY = "yaml-editor-doc";

const DEFAULT_YAML = `# Example
service:
  name: metricshub
  port: 8080
  enabled: true
`;

function createYamlLinter() {
	return linter((view) => {
		const text = view.state.doc.toString();
		if (!text.trim()) return [];
		try {
			YAML.parse(text);
			return [];
		} catch (e) {
			const from = e.pos ?? 0;
			return [
				{
					from,
					to: from,
					message: e.message || "Unknown error",
					severity: "error",
				},
			];
		}
	}, { delay: 1000 });
}

const inlineLintMessages = ViewPlugin.fromClass(class {
	decorations;

	constructor(view) {
		this.decorations = this.build(view);
	}

	update(update) {
		if (update.docChanged || update.viewportChanged || update.transactions.length) {
			this.decorations = this.build(update.view);
		}
	}

	build(view) {
		const deco = new RangeSetBuilder();
		const diags = getDiagnostics(view.state);
		if (!diags || diags.length === 0) return deco.finish();

		for (const d of diags) {
			const line = view.state.doc.lineAt(d.from);
			const widget = Decoration.widget({
				widget: new (class extends WidgetType {
					toDOM() {
						const el = document.createElement("div");
						el.className =
							`cm-inline-lint ${d.severity === "error" ? "cm-inline-lint-error" : "cm-inline-lint-warning"}`;
						el.textContent = d.message;
						return el;
					}
				})(),
				side: 1,
				block: true,
			});
			deco.add(line.to, line.to, widget);
		}

		return deco.finish();
	}
}, {
	decorations: v => v.decorations
});

const inlineLintTheme = EditorView.theme({
	".cm-inline-lint": {
		fontSize: "0.80rem",
		lineHeight: 1.4,
		padding: "2px 8px",
		margin: "2px 0 4px 0",
		borderLeft: "3px solid",
		borderRadius: "4px",
		background: "rgba(0,0,0,0.04)",
	},
	".cm-inline-lint-error": {
		borderColor: "#d32f2f",
		color: "#b71c1c",
	},
	".cm-inline-lint-warning": {
		borderColor: "#f57c00",
		color: "#e65100",
	},
}, { dark: false });

const inlineLintThemeDark = EditorView.theme({
	".cm-inline-lint": {
		background: "rgba(255,255,255,0.06)",
	},
	".cm-inline-lint-error": {
		borderColor: "#ef5350",
		color: "#ef9a9a",
	},
	".cm-inline-lint-warning": {
		borderColor: "#ffa726",
		color: "#ffcc80",
	},
}, { dark: true });

export default function YamlEditor({ value, onChange, onSave, height = "100%", readOnly = false }) {
	const theme = useTheme();

	// Load initial value from localStorage OR prop OR default
	const [doc, setDoc] = useState(() => {
		const stored = localStorage.getItem(LOCAL_STORAGE_KEY);
		return stored ?? value ?? DEFAULT_YAML;
	});

	const docRef = useRef(doc);
	useEffect(() => {
		docRef.current = doc;
	}, [doc]);

	useEffect(() => {
		if (value == null) return;
		setDoc(value);
		localStorage.setItem(LOCAL_STORAGE_KEY, value);
	}, [value]);

	// Save to localStorage on every change
	useEffect(() => {
		localStorage.setItem(LOCAL_STORAGE_KEY, doc);
	}, [doc]);

	const extensions = useMemo(() => {
		const km = [...defaultKeymap, ...historyKeymap];

		if (onSave) {
			km.push({
				key: "Mod-s",
				preventDefault: true,
				run: () => {
					onSave(docRef.current);
					return true;
				},
			});
		}
		return [
			cmYaml(),
			lintGutter(),
			createYamlLinter(),
			history(),
			keymap.of(km),
			inlineLintMessages,
			inlineLintTheme,
			inlineLintThemeDark,
			// Optional: show a bottom panel listing all diagnostics
			// lintPanel(),
		];
	}, [onSave]);

	const handleChange = useCallback((val) => {
		setDoc(val);
		if (onChange) onChange(val);
	}, [onChange]);

	const handleFormat = useCallback(() => {
		try {
			const obj = doc.trim() ? YAML.parse(doc) : {};
			const formatted = YAML.stringify(obj, { indent: 2, lineWidth: 100 });
			setDoc(formatted);
			if (onChange) onChange(formatted);
		} catch {
		}
	}, [doc, onChange]);

	const handleSave = useCallback(() => {
		if (onSave) onSave(docRef.current);
	}, [onSave]);

	return (
		<Box sx={{ height, display: "flex", flexDirection: "column", minHeight: 0 }}>
			{/* Top toolbar */}
			<Stack direction="row" alignItems="center" spacing={1} sx={{ px: 1, py: 0.5 }}>
				<Typography variant="subtitle2" sx={{ flex: 1 }}>
					YAML Editor
				</Typography>

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
					theme={theme.palette.mode === "dark" ? "dark" : "light"}
				/>
			</Box>
		</Box>
	);
}
