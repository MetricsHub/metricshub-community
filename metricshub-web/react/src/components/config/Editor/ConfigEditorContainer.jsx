// src/components/config/Editor/ConfigEditorContainer.jsx
import * as React from "react";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { setContent } from "../../../store/slices/configSlice";
import { saveConfig } from "../../../store/thunks/configThunks";
import ConfigEditor from "./ConfigEditor";

/**
 * Container component for the configuration file editor.
 * Connects to Redux store for state management.
 * @returns The connected editor component.
 */
export default function ConfigEditorContainer() {
	const dispatch = useAppDispatch();
	const selected = useAppSelector((s) => s.config.selected);
	const storeContent = useAppSelector((s) => s.config.content);
	const saving = useAppSelector((s) => s.config.saving);

	const [local, setLocal] = React.useState(storeContent);

	/**
	 * When selected file or store content changes, update local state.
	 * This handles loading new files and external updates.
	 * @type {React.EffectCallback}
	 */
	React.useEffect(() => {
		setLocal(storeContent);
	}, [selected, storeContent]);

	/**
	 * Debounced push of local changes to Redux store.
	 * This avoids excessive dispatches while typing.
	 * @type {(v:string)=>void}
	 */
	const debouncedPush = React.useRef(
		((fn, ms = 400) => {
			let t;
			return (v) => {
				clearTimeout(t);
				t = setTimeout(() => fn(v), ms);
			};
		})((v) => dispatch(setContent(v))),
	).current;

	/**
	 * Handle content changes from the editor.
	 * Updates local state immediately and debounces store update.
	 * @param {string} v - New content value.
	 */
	const onChange = (v) => {
		setLocal(v);
		debouncedPush(v);
	};

	/**
	 * Handle save action from the editor.
	 * Dispatches save action to Redux store.
	 * No-op if no file is selected or already saving.
	 * @type {()=>void}
	 */
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
