// src/components/config/Editor/ConfigEditorContainer.jsx
import * as React from "react";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { setContent } from "../../../store/slices/configSlice";
import { saveConfig, validateConfig } from "../../../store/thunks/configThunks";
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
	const dirtyByName = useAppSelector((s) => s.config.dirtyByName) ?? {};
	const isDirty = !!(selected && dirtyByName[selected]);
	const canSave = !!selected && isDirty && !saving;

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
	 * Handle save action.
	 * Dispatches saveConfig thunk if saving is allowed.
	 * @type {React.MouseEventHandler}
	 */
	const onSave = React.useCallback(() => {
		if (!canSave) return;
		dispatch(saveConfig({ name: selected, content: local, skipValidation: false }));
	}, [canSave, dispatch, selected, local]);

const validateFn = React.useCallback(
  async (content, name) => {
    try {
      const res = await dispatch(validateConfig({ name, content })).unwrap();
      return res?.result ?? { valid: true };
    } catch {
      return { valid: true }; // fallback to no lint markers
    }
  },
  [dispatch],
);

	return (
		<ConfigEditor
			value={local}
			readOnly={!selected}
			onChange={onChange}
			onSave={onSave}
			canSave={canSave}
			height="100%"
			fileName={selected}
			validateFn={validateFn}
		/>
	);
}

export { ConfigEditorContainer };
