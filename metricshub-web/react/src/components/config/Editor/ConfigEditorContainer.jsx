import * as React from "react";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { setContent } from "../../../store/slices/configSlice";
import { saveConfig, validateConfig } from "../../../store/thunks/configThunks";
import ConfigEditor from "./ConfigEditor";
import QuestionDialog from "../../common/QuestionDialog";
import { isBackupFileName } from "../../../utils/backupNames";
import { Typography } from "@mui/material";

/**
 * Container component for the configuration file editor.
 * Connects to Redux store for state management.
 * In React 19, refs to function components are passed as a regular prop.
 * @returns The connected editor component.
 */
function ConfigEditorContainer(props) {
	const forwardedRef = props?.ref;
	const dispatch = useAppDispatch();
	const {
		selected,
		content: storeContent,
		saving,
		dirtyByName = {},
	} = useAppSelector((s) => s.config);
	const isBackupSelected = !!(selected && isBackupFileName(selected));
	const isDirty = !!(selected && dirtyByName[selected]);
	const canSave = !!selected && !isBackupSelected && isDirty && !saving;

	const [local, setLocal] = React.useState(storeContent);

	// Sync local state with store when selected file or store content changes
	// (e.g. when switching files or after external updates)
	React.useEffect(() => {
		setLocal(storeContent);
	}, [selected, storeContent]);

	// debounced dispatch to update store content after user stops typing
	const debouncedPush = React.useRef(
		((fn, ms = 400) => {
			let t;
			return (v) => {
				clearTimeout(t);
				t = setTimeout(() => fn(v), ms);
			};
		})((v) => dispatch(setContent(v))),
	).current;

	// onChange handler for editor
	const onChange = (v) => {
		setLocal(v);
		debouncedPush(v);
	};

	// dialog state for showing validation errors on save
	const [dialog, setDialog] = React.useState(null); // { open, message, from?, to? }

	// editor view ref - set by YamlEditor via onEditorReady
	const editorViewRef = React.useRef(null);

	// validate-then-save: run validation first, then either save or show a single dialog
	// dialog: null = closed; otherwise { open:true, message, from?, to? }
	const wrappedSave = React.useCallback(async () => {
		if (!canSave) return;
		try {
			const res = await dispatch(validateConfig({ name: selected, content: local })).unwrap();
			const result = res?.result ?? { valid: true };

			if (result.valid) {
				await dispatch(saveConfig({ name: selected, content: local, skipValidation: false }));
				return;
			}

			// Prefer first error message from array; fallback to generic string
			if (Array.isArray(result.errors) && result.errors.length > 0) {
				const first = result.errors[0];
				setDialog({
					open: true,
					message: String(first?.message || first?.msg || "Validation failed"),
				});
			} else if (result.error) {
				setDialog({ open: true, message: String(result.error) });
			} else {
				setDialog({ open: true, message: "Validation failed" });
			}
		} catch (e) {
			setDialog({ open: true, message: e?.message || "Validation failed" });
		}
	}, [canSave, dispatch, selected, local]);

	// expose wrappedSave to parent via ref prop
	React.useImperativeHandle(
		forwardedRef,
		() => ({
			save: wrappedSave,
		}),
		[wrappedSave],
	);

	// validation function for editor linting
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
		<>
			<ConfigEditor
				value={local}
				readOnly={!selected || isBackupSelected}
				onChange={onChange}
				onSave={wrappedSave}
				canSave={canSave}
				height="100%"
				fileName={selected}
				validateFn={validateFn}
				onEditorReady={(view) => {
					editorViewRef.current = view;
				}}
			/>
			<QuestionDialog
				open={!!dialog?.open}
				title="Please fix this error"
				question={
					<>
						<Typography sx={{ whiteSpace: "pre-wrap", m: 0 }}>{dialog?.message}</Typography>
					</>
				}
				actionButtons={[
					{
						btnTitle: "OK",
						callback: () => setDialog((d) => (d ? { ...d, open: false } : d)),
						btnVariant: "contained",
						autoFocus: true,
					},
				]}
				onClose={() => setDialog((d) => (d ? { ...d, open: false } : d))}
			/>
		</>
	);
}

export default ConfigEditorContainer;
