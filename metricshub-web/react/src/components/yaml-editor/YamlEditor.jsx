import React from "react";
import { Box } from "@mui/material";
import { useTheme } from "@mui/material/styles";

import CodeMirror from "@uiw/react-codemirror";
import { yaml as cmYaml } from "@codemirror/lang-yaml";
import { history, historyKeymap, defaultKeymap } from "@codemirror/commands";
import { keymap, EditorView } from "@codemirror/view";
import "./lint-fallback.css";
import { buildYamlLinterExtension } from "../../utils/yaml-lint-utils";

const LOCAL_STORAGE_KEY = "yaml-editor-doc";
const SAVE_DEBOUNCE_MS = 400; // reduce localStorage IO (align with validation debounce)

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

	// Removed custom search panel state to restore default CodeMirror Ctrl+F behavior

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

	// Save to localStorage on change with debounce to reduce IO
	React.useEffect(() => {
		const id = setTimeout(() => {
			try {
				localStorage.setItem(LOCAL_STORAGE_KEY, doc);
			} catch {
				// Local storage is best-effort: ignore errors (quota, private mode, blocked storage)
			}
		}, SAVE_DEBOUNCE_MS);
		return () => clearTimeout(id);
	}, [doc]);

	/**
	 * Validation extensions for CodeMirror based on provided validateFn and fileName.
	 * @returns {Array} Array of CodeMirror extensions for validation.
	 */
	const validationExtension = React.useMemo(
		() => buildYamlLinterExtension(validateFn, fileName, 400),
		[validateFn, fileName],
	);

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

		// Ensure search navigation follows matches even when the editor is
		// nested in other scrollable containers. Using DOM scrollIntoView will
		// scroll ancestor containers as needed, not just the CM scroller.
		const followSelection = EditorView.updateListener.of((update) => {
			if (!update.transactions.length) return;

			const isSearchMove = update.transactions.some((tr) => tr.isUserEvent("select.search"));
			const isSelectionChange = update.selectionSet;

			if (isSearchMove || isSelectionChange) {
				const range = update.state.selection.main;
				const domAt = update.view.domAtPos(range.head);
				let el = domAt?.node || null;
				if (el && el.nodeType === 3) el = el.parentElement; // text node -> element

				if (el && el.scrollIntoView) {
					// Use center for search moves, nearest for regular navigation
					const block = isSearchMove ? "center" : "nearest";
					el.scrollIntoView({ block, inline: "nearest" });
				}
			}
		});

		return [cmYaml(), history(), keymap.of(km), followSelection, ...validationExtension];
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
		<Box
			sx={{
				height,
				display: "flex",
				flexDirection: "column",
				minHeight: 0,
				position: "relative",
				transition: "background-color 0.4s ease",
			}}
		>
			<Box
				sx={{
					flex: 1,
					minHeight: 0,
					borderTop: 0,
					".cm-editor": {
						height: "100%",
						transition: "background-color 0.4s ease, color 0.4s ease",
					},
					".cm-gutters": {
						transition: "background-color 0.4s ease, color 0.4s ease, border-color 0.4s ease",
					},
					".cm-content": {
						transition: "background-color 0.4s ease, color 0.4s ease",
					},
					".cm-activeLine": {
						transition: "background-color 0.4s ease",
					},
					".cm-activeLineGutter": {
						transition: "background-color 0.4s ease",
					},
					// Allow scroll chaining so the parent container can scroll when the editor
					// can't (fixes mouse wheel not scrolling when editor is focused)
					".cm-scroller": { overflow: "auto", overscrollBehavior: "auto" },
					transition: "background-color 0.4s ease",
				}}
			>
				<CodeMirror
					value={doc}
					onChange={handleChange}
					extensions={extensions}
					editable={!readOnly}
					basicSetup={{
						lineNumbers: true,
						highlightActiveLine: true,
						foldGutter: true,
						// Re-enable default search behavior and keymap (Ctrl/Cmd+F)
						searchKeymap: true,
						search: true,
					}}
					theme={theme.palette.mode}
					onCreateEditor={(view) => {
						viewRef.current = view; // <-- this is the EditorView
						onEditorReady?.(view);
					}}
				/>
			</Box>
		</Box>
	);
}
