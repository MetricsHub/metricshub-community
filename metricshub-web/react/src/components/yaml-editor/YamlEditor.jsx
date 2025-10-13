import React, { useMemo, useState, useEffect, useCallback } from "react";
import { Box } from "@mui/material";
import { useTheme } from "@mui/material/styles";

import CodeMirror from "@uiw/react-codemirror";
import { yaml as cmYaml } from "@codemirror/lang-yaml";
import { history, historyKeymap, defaultKeymap } from "@codemirror/commands";
import { keymap } from "@codemirror/view";
import { linter, lintGutter } from "@codemirror/lint";
import "./lint-fallback.css";

const LOCAL_STORAGE_KEY = "yaml-editor-doc";

const DEFAULT_YAML = `# Example
service:
	name: metricshub
	port: 8080
	enabled: true
`;

/**
 * YAML Editor component with backend-driven validation.
 */
export default function YamlEditor({
	value,
	onChange,
	onSave,
	canSave = true,
	fileName,
	validateFn,
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

	// Robust mapping of backend validation result -> CodeMirror diagnostics
	const toDiagnostics = useCallback((result, cmDoc) => {
		if (!result || result.valid) return [];

		const makePos = (lineNum, column) => {
			// Some backends use 0-based lines, some use 1-based. Accept either.
			// If lineNum is undefined/null, default to 1.
			let ln = typeof lineNum === "number" ? lineNum : 1;
			if (ln <= 0) {
				// assume 0-based -> convert to 1-based for cmDoc.line
				ln = ln + 1;
			}
			// clamp into [1, cmDoc.lines]
			ln = Math.max(1, Math.min(cmDoc.lines, ln));

			// column: if undefined, default to 1
			let col = typeof column === "number" ? column : 1;
			// If column is 0-based (0 means first char), convert to 1-based
			if (col <= 0) col = col + 1;

			const lineInfo = cmDoc.line(ln);
			// clamp column into [1, line length+1]
			const safeCol = Math.max(1, Math.min(lineInfo.length + 1, col));
			return lineInfo.from + (safeCol - 1);
		};

		if (Array.isArray(result.errors) && result.errors.length > 0) {
			return result.errors.map((e) => {
				try {
					const from = makePos(e.line ?? e.ln ?? 1, e.column ?? e.col ?? 1);
					const to = makePos(
						e.endLine ?? e.endLn ?? e.line ?? e.ln ?? 1,
						e.endColumn ?? e.endCol ?? (e.column ?? e.col ?? 1) + 1,
					);

					// Ensure to > from. If not, extend to by one or clamp to doc end.
					let safeFrom = Math.max(0, Math.min(cmDoc.length, from));
					let safeTo = Math.max(0, Math.min(cmDoc.length, to));
					if (safeTo <= safeFrom) {
						safeTo = Math.min(cmDoc.length, safeFrom + 1);
					}

					return {
						from: safeFrom,
						to: safeTo,
						message: e.message || e.msg || "Validation error",
						severity: e.severity || "error",
					};
				} catch (_err) {
					// fallback single diagnostic if mapping failed
					return {
						from: 0,
						to: Math.min(1, cmDoc.length),
						message: e?.message || _err?.message || "Validation error",
						severity: e?.severity || "error",
					};
				}
			});
		}

		if (result.error) {
				// Attempt to parse locations like "line 9, column 1" from the error message
				const text = String(result.error || "");
				const locRegex = /line\s+(\d+),\s*column\s+(\d+)/gi;
				const matches = Array.from(text.matchAll(locRegex));
				if (matches.length > 0) {
					const diags = matches.map((m) => {
						const ln = parseInt(m[1], 10);
						const col = parseInt(m[2], 10);
						const from = makePos(ln, col);
						// try to extend to a token end on the same line for better visibility
						const lineInfo = cmDoc.line(Math.max(1, Math.min(cmDoc.lines, ln <= 0 ? ln + 1 : ln)));
						const offsetInLine = Math.max(0, Math.min(lineInfo.length, from - lineInfo.from));
						const lineText = lineInfo.text || "";
						let endOffset = offsetInLine;
						while (endOffset < lineText.length && !/\s/.test(lineText[endOffset])) endOffset++;
						let to = lineInfo.from + endOffset;
						if (to <= from) to = Math.min(cmDoc.length, from + 1);
						return {
							from,
							to,
							message: text,
							severity: "error",
						};
					});
					return diags;
				}
				return [
					{
						from: 0,
						to: Math.min(1, cmDoc.length),
						message: text,
						severity: "error",
					},
				];
		}

		return [];
	}, []);

	const validationExtension = useMemo(() => {
		if (!validateFn || !fileName) return [];
		return [
			lintGutter(),
			linter(
				async (view) => {
					const content = view.state.doc.toString();
					try {
						const res = await validateFn(content, fileName);
						return toDiagnostics(res, view.state.doc);
					} catch (_err) {
						return [
							{
								from: 0,
								to: Math.min(1, view.state.doc.length),
								message: _err?.message || "Validation request failed",
								severity: "error",
							},
						];
					}
				},
				{ delay: 400 },
			),
		];
	}, [validateFn, fileName, toDiagnostics]);

	const extensions = useMemo(() => {
		const km = [...defaultKeymap, ...historyKeymap];
		if (onSave) {
			km.unshift({
				key: "Mod-s",
				preventDefault: true,
				run: (view) => {
					if (!canSave) return true;
					onSave(view.state.doc.toString());
					return true;
				},
			});
		}
		return [cmYaml(), history(), keymap.of(km), ...validationExtension];
	}, [onSave, canSave, validationExtension]);

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
