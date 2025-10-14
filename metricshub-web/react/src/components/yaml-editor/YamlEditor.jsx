import React from "react";
import { Box } from "@mui/material";
import { useTheme } from "@mui/material/styles";

import CodeMirror from "@uiw/react-codemirror";
import { yaml as cmYaml } from "@codemirror/lang-yaml";
import { history, historyKeymap, defaultKeymap } from "@codemirror/commands";
import { keymap } from "@codemirror/view";
import { linter, lintGutter } from "@codemirror/lint";
import "./lint-fallback.css";
import { shortYamlError } from "../../utils/yaml-error";

const LOCAL_STORAGE_KEY = "yaml-editor-doc";

/**
 * YAML Editor component.
 *
 * @param {{value?:string,onChange?:(val:string)=>void,onSave?:(val:string)=>void,height?:string,readOnly?:boolean}} props The component props.
 * @returns {JSX.Element} The YAML editor component.
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
	onEditorReady,
}) {
	const theme = useTheme();

	// expose editor view for parent components (used to scroll to error locations)
	const viewRef = React.useRef(null);

	const [doc, setDoc] = React.useState(() => {
		const stored = localStorage.getItem(LOCAL_STORAGE_KEY);
		return stored ?? value;
	});

	// Sync external value if provided
	React.useEffect(() => {
		if (value != null) {
			setDoc(value);
			localStorage.setItem(LOCAL_STORAGE_KEY, value);
		}
	}, [value]);

	// Save to localStorage on every change
	React.useEffect(() => {
		localStorage.setItem(LOCAL_STORAGE_KEY, doc);
	}, [doc]);

	// Robust mapping of backend validation result -> CodeMirror diagnostics
	/**
	 * Map backend validation result to CodeMirror diagnostics.
	 * Handles various formats and attempts to map line/column to document positions.
	 * @param {object} result - Validation result from backend.
	 * @param {import("@codemirror/state").Text} cmDoc - CodeMirror document instance.
	 * @returns {Array} Array of diagnostic objects for CodeMirror.
	 */
	const toDiagnostics = React.useCallback((result, cmDoc) => {
		if (!result || result.valid) return [];

		// normalize message: trim and collapse exact repeated whole-message repetitions
		const normalizeMessage = (m) => {
			if (!m) return "";
			let txt = String(m).trim();
			// try collapsing k-fold exact repeats for small k
			for (let k = 2; k <= 4; k++) {
				if (txt.length % k !== 0) continue;
				const part = txt.slice(0, txt.length / k);
				if (part.repeat(k) === txt) {
					txt = part.trim();
					break;
				}
			}
			// collapse consecutive identical lines to reduce trivial duplication
			const lines = txt.split(/\r?\n/);
			const out = [];
			for (let i = 0; i < lines.length; i++) {
				if (i === 0 || lines[i] !== lines[i - 1]) out.push(lines[i]);
			}
			return out.join("\n").trim();
		};

		// Use reusable helper for extracting concise YAML error messages
		const shortError = shortYamlError;

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
			const diags = result.errors.map((e) => {
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
						// prefer a short extracted message, but still normalize duplicates
						message: normalizeMessage(shortError(e.message || e.msg || "Validation error")),
						severity: e.severity || "error",
					};
				} catch (_err) {
					// fallback single diagnostic if mapping failed
					return {
						from: 0,
						to: Math.min(1, cmDoc.length),
						message: normalizeMessage(shortError(e?.message || _err?.message || "Validation error")),
						severity: e?.severity || "error",
					};
				}
			});

			const seen = new Map();
			const uniq = [];
			for (const d of diags) {
				const key = `${d.from}:${d.to}:${String(d.message || "")
					.replace(/\s+/g, " ")
					.trim()}`;
				if (!seen.has(key)) {
					seen.set(key, true);
					uniq.push(d);
				}
			}
			return uniq;
		}

		if (result.error) {
			const text = String(result.error || "");
			const locRegex = /line\s+(\d+),\s*column\s+(\d+)/gi;
			const matches = Array.from(text.matchAll(locRegex));

			if (matches.length > 0) {
				// Keep only first location per line (avoid hover duplication)
				const seenLines = new Set();
				const filtered = matches.filter((m) => {
					const ln = parseInt(m[1], 10) || 1;
					if (seenLines.has(ln)) return false;
					seenLines.add(ln);
					return true;
				});

				const diags = filtered.slice(0, 1 /* keep only first if you prefer */).map((m) => {
					const ln = parseInt(m[1], 10) || 1;
					const col = parseInt(m[2], 10) || 1;
					const from = makePos(ln, col);
					const lineInfo = cmDoc.line(Math.max(1, Math.min(cmDoc.lines, ln)));
					const offsetInLine = Math.max(0, Math.min(lineInfo.length, from - lineInfo.from));
					const lineText = lineInfo.text || "";
					let endOffset = offsetInLine;
					while (endOffset < lineText.length && !/\s/.test(lineText[endOffset])) endOffset++;
					let to = lineInfo.from + endOffset;
					if (to <= from) to = Math.min(cmDoc.length, from + 1);
					return {
						from,
						to,
						message: normalizeMessage(shortError(text)),
						severity: "error",
					};
				});

				// Deduplicate by message only (avoid double hover)
				const byMsg = new Map();
				for (const d of diags) {
					const key = String(d.message || "")
						.replace(/\s+/g, " ")
						.trim();
					if (!byMsg.has(key)) byMsg.set(key, d);
				}
				return [...byMsg.values()];
			}

			// fallback single diagnostic
			return [
				{
					from: 0,
					to: Math.min(1, cmDoc.length),
					message: normalizeMessage(shortError(text)),
					severity: "error",
				},
			];
		}

		return [];
	}, []);

	/**
	 * Validation extensions for CodeMirror based on provided validateFn and fileName.
	 * @returns {Array} Array of CodeMirror extensions for validation.
	 */
	const validationExtension = React.useMemo(() => {
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

	const extensions = React.useMemo(() => {
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
	const handleChange = React.useCallback(
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
					onCreateEditor={(editor) => {
						viewRef.current = editor.view;
						onEditorReady?.(editor.view);
					}}
				/>
			</Box>
		</Box>
	);
}
