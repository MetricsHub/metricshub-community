import * as React from "react";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { setContent } from "../../../store/slices/configSlice";
import { saveConfig, validateConfig } from "../../../store/thunks/configThunks";
import ConfigEditor from "./ConfigEditor";
import QuestionDialog from "../../common/QuestionDialog";
import { shortYamlError } from "../../../utils/yaml-error";
import { extractYamlErrorRange } from "../../../utils/yaml-lint-utils";

/**
 * Container component for the configuration file editor.
 * Connects to Redux store for state management.
 * @returns The connected editor component.
 */
const ConfigEditorContainer = React.forwardRef(function ConfigEditorContainer(_props, ref) {
	const dispatch = useAppDispatch();
	const {
		selected,
		content: storeContent,
		saving,
		dirtyByName = {},
	} = useAppSelector((s) => s.config);
	const isDirty = !!(selected && dirtyByName[selected]);
	const canSave = !!selected && isDirty && !saving;

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

	// validate-then-save: run validation first, then either save or open dialog listing errors
	// dialog: null = closed; otherwise { open:true, message, from?, to? }
	const wrappedSave = React.useCallback(async () => {
		if (!canSave) return;
		try {
			const res = await dispatch(validateConfig({ name: selected, content: local })).unwrap();
			const result = res?.result ?? { valid: true };

			// if invalid -> extract first error, compute range via util, and show dialog
			if (result?.valid === false) {
				const first = Array.isArray(result.errors)
					? result.errors[0]
					: result.error
						? { message: result.error }
						: null;

				if (first) {
					const { from, to, message } = extractYamlErrorRange(first, editorViewRef.current);
					setDialog({
						open: true,
						message: shortYamlError(message || "Validation failed"),
						from,
						to,
					});
					return;
				}

				setDialog({ open: true, message: "Validation failed" });
				return;
			}

			// valid → save
			dispatch(saveConfig({ name: selected, content: local, skipValidation: false }));
		} catch (e) {
			setDialog({ open: true, message: e?.message || "Validation failed" });
		}
	}, [canSave, dispatch, selected, local]);

	// expose wrappedSave to parent via ref
	React.useImperativeHandle(
		ref,
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
				readOnly={!selected}
				onChange={onChange}
				onSave={wrappedSave}
				canSave={canSave}
				height="100%"
				fileName={selected}
				validateFn={validateFn}
			/>
			<QuestionDialog
				open={!!dialog?.open}
				title="Please fix this error"
				question={
					<>
						<p style={{ whiteSpace: "pre-wrap", margin: 0 }}>{dialog?.message}</p>
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
});

export default ConfigEditorContainer;
