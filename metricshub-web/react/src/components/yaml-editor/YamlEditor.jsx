import React from "react";
import { Box } from "@mui/material";
import { useTheme } from "@mui/material/styles";

import CodeMirror from "@uiw/react-codemirror";
import { yaml as cmYaml } from "@codemirror/lang-yaml";
import { history, historyKeymap, defaultKeymap } from "@codemirror/commands";
import { keymap } from "@codemirror/view";
import "./lint-fallback.css";
import { shortYamlError } from "../../utils/yaml-error";
import { buildYamlLinterExtension } from "../../utils/yaml-lint-utils";

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

	/**
	 * Validation extensions for CodeMirror based on provided validateFn and fileName.
	 * @returns {Array} Array of CodeMirror extensions for validation.
	 */
	const validationExtension = React.useMemo(
		() => buildYamlLinterExtension(validateFn, fileName, shortYamlError, 400),
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
