// src/components/config/Editor/ConfigEditor.jsx
import * as React from "react";
import { Box } from "@mui/material";

const YamlEditor = React.lazy(() => import("../../yaml-editor/YamlEditor"));

export default function ConfigEditor({ value, readOnly, onChange, onSave, height = "100%" }) {
	return (
		<Box sx={{ height }}>
			<React.Suspense fallback={<Box sx={{ p: 2 }}>Loading editor…</Box>}>
				<YamlEditor
					value={value}
					readOnly={readOnly}
					onChange={onChange}
					onSave={onSave}
					height="100%"
				/>
			</React.Suspense>
		</Box>
	);
}
