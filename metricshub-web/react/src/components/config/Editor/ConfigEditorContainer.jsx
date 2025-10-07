// src/components/config/Editor/ConfigEditorContainer.jsx
import * as React from "react";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { setContent } from "../../../store/slices/configSlice";
import { saveConfig } from "../../../store/thunks/configThunks";
import ConfigEditor from "./ConfigEditor";

export default function ConfigEditorContainer() {
	const dispatch = useAppDispatch();
	const selected = useAppSelector((s) => s.config.selected);
	const storeContent = useAppSelector((s) => s.config.content);
	const saving = useAppSelector((s) => s.config.saving);

	const [local, setLocal] = React.useState(storeContent);

	// Reset local content when new file selected or new content fetched
	React.useEffect(() => {
		setLocal(storeContent);
	}, [selected, storeContent]);

	const debouncedPush = React.useRef(
		((fn, ms = 400) => {
			let t;
			return (v) => {
				clearTimeout(t);
				t = setTimeout(() => fn(v), ms);
			};
		})((v) => dispatch(setContent(v))),
	).current;

	const onChange = (v) => {
		setLocal(v);
		debouncedPush(v);
	};

	const onSave = () => {
		if (!selected || saving) return;
		dispatch(saveConfig({ name: selected, content: local, skipValidation: false }));
	};

	return (
		<ConfigEditor
			value={local}
			readOnly={!selected}
			onChange={onChange}
			onSave={onSave}
			height="100%"
		/>
	);
}

export { ConfigEditorContainer };
