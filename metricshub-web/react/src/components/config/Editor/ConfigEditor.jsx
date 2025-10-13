// src/components/config/Editor/ConfigEditor.jsx
import * as React from "react";
import { Box } from "@mui/material";

const YamlEditor = React.lazy(() => import("../../yaml-editor/YamlEditor"));

/**
 * Configuration file editor component.
 * @param {{value:string,readOnly?:boolean,onChange?:(v:string)=>void,onSave?:()=>void,canSave?:boolean,height?:string}} props
 * @returns The editor component.
 */
export default function ConfigEditor({
	value,
	readOnly,
	onChange,
	onSave,
	canSave = true,
	height = "100%",
	fileName,
	validateFn,
	onEditorReady,
}) {
	return (
		<Box sx={{ height }}>
			<React.Suspense fallback={<Box sx={{ p: 2 }}>Loading editor…</Box>}>
				<YamlEditor
					value={value}
					readOnly={readOnly}
					onChange={onChange}
					onSave={onSave}
					fileName={fileName}
					onEditorReady={onEditorReady}
					validateFn={validateFn}
					canSave={canSave}
					height="100%"
				/>
			</React.Suspense>
		</Box>
	);
}
