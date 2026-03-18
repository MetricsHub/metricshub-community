import * as React from "react";
import { useNavigate } from "react-router-dom";
import { paths } from "../../../paths";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { setContent } from "../../../store/slices/otel-config-slice";
import {
	saveOtelConfig,
	validateOtelConfig,
	saveDraftOtelConfig,
	deleteOtelConfig,
} from "../../../store/thunks/otel-config-thunks";
import ConfigEditor from "../../config/editor/ConfigEditor";
import QuestionDialog from "../../common/QuestionDialog";
import { isBackupFileName } from "../../../utils/backup-names";
import { Typography } from "@mui/material";
import { useAuth } from "../../../hooks/use-auth";

/**
 * Container for Otel configuration editor. Uses otelConfig slice and Otel thunks.
 * No Velocity; exposes ref.save and ref.apply.
 */
function OtelConfigEditorContainer(props) {
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
	} = useAppSelector((s) => s.otelConfig);
	const isBackupSelected = !!(selected && isBackupFileName(selected));
	const isDirty = !!(selected && dirtyByName[selected]);
	const canSave = !!selected && !isBackupSelected && isDirty && !saving && !isReadOnly;

	const [local, setLocal] = React.useState(storeContent);

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

	const [dialog, setDialog] = React.useState(null);
	const editorViewRef = React.useRef(null);

	const isDraft = selected?.endsWith(".draft");

	const handleValidationError = React.useCallback((result) => {
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
	}, []);

	const handleDraftSave = React.useCallback(async () => {
		if (!selected || !isDraft || isReadOnly) return;
		await dispatch(saveDraftOtelConfig({ name: selected, content: local, skipValidation: true }));
	}, [dispatch, selected, isDraft, local, isReadOnly]);

	const handleApply = React.useCallback(() => {
		if (!selected || !isDraft || isReadOnly) return;
		const targetName = selected.replace(/\.draft$/, "");

		return dispatch(validateOtelConfig({ name: selected, content: local }))
			.unwrap()
			.then((res) => {
				const result = res?.result ?? { valid: true };
				if (!result.valid) {
					handleValidationError(result);
					return null;
				}
				return dispatch(
					saveOtelConfig({ name: targetName, content: local, skipValidation: false }),
				).unwrap();
			})
			.then((saved) => {
				if (!saved) return null;
				navigate(paths.configurationOtelFile(targetName), { replace: true });
				return new Promise((resolve) => setTimeout(resolve, 50));
			})
			.then(() => {
				dispatch(deleteOtelConfig(selected));
			})
			.catch((e) => {
				setDialog({ open: true, message: e?.message || "Validation failed" });
			});
	}, [dispatch, selected, isDraft, local, navigate, isReadOnly, handleValidationError]);

	const handleNormalSave = React.useCallback(async () => {
		if (!canSave || isReadOnly) return;
		try {
			const res = await dispatch(validateOtelConfig({ name: selected, content: local })).unwrap();
			const result = res?.result ?? { valid: true };

			if (result.valid) {
				await dispatch(saveOtelConfig({ name: selected, content: local, skipValidation: false }));
				return;
			}
			handleValidationError(result);
		} catch (e) {
			setDialog({ open: true, message: e?.message || "Validation failed" });
		}
	}, [canSave, dispatch, selected, local, isReadOnly, handleValidationError]);

	const forceSaveAsDraft = React.useCallback(() => {
		setDialog(null);
		if (!selected) return;

		const draftName = selected + ".draft";
		return dispatch(saveDraftOtelConfig({ name: draftName, content: local, skipValidation: true }))
			.unwrap()
			.then(() => {
				navigate(paths.configurationOtelFile(draftName), { replace: true });
				return new Promise((resolve) => setTimeout(resolve, 50));
			})
			.then(() => {
				dispatch(deleteOtelConfig(selected));
			});
	}, [dispatch, local, selected, navigate]);

	const primarySave = isDraft ? handleDraftSave : handleNormalSave;

	React.useImperativeHandle(
		forwardedRef,
		() => ({
			save: primarySave,
			apply: handleApply,
		}),
		[primarySave, handleApply],
	);

	const validateFn = React.useCallback(
		async (content, name) => {
			try {
				const res = await dispatch(validateOtelConfig({ name, content })).unwrap();
				return res?.result ?? { valid: true };
			} catch {
				return { valid: true };
			}
		},
		[dispatch],
	);

	const actions = React.useMemo(() => {
		const btns = [];
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
				canSave={!isReadOnly && (canSave || (isDraft && isDirty))}
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

export default OtelConfigEditorContainer;
