import React from "react";
import { Box } from "@mui/material";
import { useTheme } from "@mui/material/styles";

import CodeMirror from "@uiw/react-codemirror";
import { yaml as cmYaml } from "@codemirror/lang-yaml";
import { history, historyKeymap, defaultKeymap } from "@codemirror/commands";
import { keymap } from "@codemirror/view";
import { Prec } from "@codemirror/state";
import "./lint-fallback.css";
import { buildYamlLinterExtension } from "../../utils/yaml-lint-utils";
import SearchPanel from "./SearchPanel";

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

	// Search panel state
	const [searchOpen, setSearchOpen] = React.useState(false);

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
		// Override Ctrl+F to open our custom search panel with high precedence
		const customSearchKeymap = Prec.highest(
			keymap.of([
				{
					key: "Mod-f",
					preventDefault: true,
					run: () => {
						setSearchOpen(true);
						return true;
					},
				},
			])
		);
		return [cmYaml(), history(), keymap.of(km), customSearchKeymap, ...validationExtension];
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
		<Box sx={{ height, display: "flex", flexDirection: "column", minHeight: 0, position: "relative" }}>
			{searchOpen && (
				<SearchPanel
					onClose={() => setSearchOpen(false)}
					viewRef={viewRef}
				/>
			)}
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
					basicSetup={{ 
						lineNumbers: true, 
						highlightActiveLine: true, 
						foldGutter: true,
						searchKeymap: false,  // Disable default search
						search: false,        // Disable search module
					}}
					theme={theme.palette.mode}
					 onCreateEditor={(view) => {
   viewRef.current = view;          // <-- this is the EditorView
   onEditorReady?.(view);
 }}
				/>
			</Box>
		</Box>
	);
}
