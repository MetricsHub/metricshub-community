import * as React from "react";
import { useNavigate } from "react-router-dom";
import { paths } from "../../../paths";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { setContent } from "../../../store/slices/config-slice";
import {
	saveConfig,
	validateConfig,
	saveDraftConfig,
	deleteConfig,
} from "../../../store/thunks/config-thunks";
import ConfigEditor from "./ConfigEditor";
import QuestionDialog from "../../common/QuestionDialog";
import { isBackupFileName } from "../../../utils/backup-names";
import { Typography } from "@mui/material";
import { useAuth } from "../../../hooks/use-auth";

/**
 * Container component for the configuration file editor.
 * Connects to Redux store for state management.
 * In React 19, refs to function components are passed as a regular prop.
 * @returns The connected editor component.
 */
function ConfigEditorContainer(props) {
	const forwardedRef = props?.ref;
	const dispatch = useAppDispatch();
	const navigate = useNavigate();
	const { user } = useAuth();
	const isReadOnly = user?.role === "ro";
	const {
		selected,
		content: storeContent,
		saving,
		dirtyByName = {},
	} = useAppSelector((s) => s.config);
	const isBackupSelected = !!(selected && isBackupFileName(selected));
	const isDirty = !!(selected && dirtyByName[selected]);
	const canSave = !!selected && !isBackupSelected && isDirty && !saving && !isReadOnly;

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

	// -------------------------------------------------------------------------
	// New logic for Draft vs Normal files
	// -------------------------------------------------------------------------

	const isDraft = selected?.endsWith(".draft");

	// 1. Save (Draft mode): Save as draft, skip validation
	//    Only active if isDraft = true
	const handleDraftSave = React.useCallback(async () => {
		if (!selected || !isDraft || isReadOnly) return;
		await dispatch(saveDraftConfig({ name: selected, content: local, skipValidation: true }));
	}, [dispatch, selected, isDraft, local, isReadOnly]);

	// 2. Save and Apply (Draft mode): Validate, Save as Normal, Delete Draft
	const handleApply = React.useCallback(async () => {
		if (!selected || !isDraft || isReadOnly) return;
		const targetName = selected.replace(/\.draft$/, "");

		try {
			// Validate first
			const res = await dispatch(validateConfig({ name: selected, content: local })).unwrap();
			const result = res?.result ?? { valid: true };

			if (result.valid) {
				// Save as normal file
				await dispatch(saveConfig({ name: targetName, content: local, skipValidation: false }));
				// Delete the draft
				await dispatch(deleteConfig(selected));
				// Navigate to normal file
				navigate(paths.configurationFile(targetName), { replace: true });
			} else {
				// Show error dialog
				// Reuse logic or create simpler alert? The existing dialog logic is fine.
				handleValidationError(result);
			}
		} catch (e) {
			setDialog({ open: true, message: e?.message || "Validation failed" });
		}
	}, [dispatch, selected, isDraft, local, navigate, isReadOnly]);

	// 3. Normal Save (Normal mode): Validate, Save. If error -> Dialog "Save as Draft"
	const handleNormalSave = React.useCallback(async () => {
		if (!canSave || isReadOnly) return;
		try {
			const res = await dispatch(validateConfig({ name: selected, content: local })).unwrap();
			const result = res?.result ?? { valid: true };

			if (result.valid) {
				await dispatch(saveConfig({ name: selected, content: local, skipValidation: false }));
				return;
			}
			handleValidationError(result);
		} catch (e) {
			setDialog({ open: true, message: e?.message || "Validation failed" });
		}
	}, [canSave, dispatch, selected, local, isReadOnly]);

	// Helper to show validation error dialog
	const handleValidationError = (result) => {
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
	};

	// "Save and convert to draft" - triggered from Dialog on Normal Save error
	const forceSaveAsDraft = React.useCallback(async () => {
		setDialog(null);
		if (!selected) return;

		const draftName = selected + ".draft";
		// Save content to draft
		await dispatch(saveDraftConfig({ name: draftName, content: local, skipValidation: true }));
		// Delete original regular file
		await dispatch(deleteConfig(selected));
		// Navigate to draft
		navigate(paths.configurationFile(draftName), { replace: true });
	}, [dispatch, local, selected, navigate]);

	// Decide which save handler to expose to the EditorHeader via context or ref
	// The EditorHeader interacts via ref-exposed methods from ConfigEditorContainer?
	// Actually EditorHeader is outside ConfigEditorContainer in the parent.
	// We need to look at ConfigurationPage logic again.
	// Oh, I see. ConfigurationPage passes `ref={editorRef}` to `ConfigEditorContainer`.
	// And `EditorHeader` calls `editorRef.current.save()`.

	// We need to expose `save` (default action) and `apply` (specific for draft).
	// Default action depends on context.
	// If isDraft: save -> handleDraftSave
	// If !isDraft: save -> handleNormalSave

	const primarySave = isDraft ? handleDraftSave : handleNormalSave;

	// expose methods to parent via ref prop
	React.useImperativeHandle(
		forwardedRef,
		() => ({
			save: primarySave,
			apply: handleApply,
		}),
		[primarySave, handleApply],
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

	const actions = React.useMemo(() => {
		const btns = [];
		// Only offer "convert to draft" if we are not already on a draft
		if (!isDraft && !isReadOnly) {
			btns.push({
				btnTitle: "Save and convert to draft",
				btnColor: "error",
				btnVariant: "contained",
				callback: forceSaveAsDraft,
			});
		}
		btns.push({
			btnTitle: "OK",
			callback: () => setDialog((d) => (d ? { ...d, open: false } : d)),
			btnVariant: "contained",
			autoFocus: true,
		});
		return btns;
	}, [isDraft, forceSaveAsDraft, isReadOnly]);

	return (
		<>
			<ConfigEditor
				value={local}
				readOnly={!selected || isBackupSelected || isReadOnly}
				onChange={onChange}
				onSave={isReadOnly ? undefined : primarySave}
				canSave={!isReadOnly && (canSave || (isDraft && isDirty))} // Drafts can always save if dirty
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
				actionButtons={actions}
				onClose={() => setDialog((d) => (d ? { ...d, open: false } : d))}
			/>
		</>
	);
}

export default ConfigEditorContainer;
