import * as React from "react";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { setContent } from "../../../store/slices/configSlice";
import { saveConfig, validateConfig } from "../../../store/thunks/configThunks";
import ConfigEditor from "./ConfigEditor";
import QuestionDialog from "../../common/QuestionDialog";
import { forwardRef } from "react";

/**
 * Container component for the configuration file editor.
 * Connects to Redux store for state management.
 * @returns The connected editor component.
 */
const ConfigEditorContainer = forwardRef(function ConfigEditorContainer(_props, ref) {
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
	// useImperativeHandle will be attached after wrappedSave is defined

	/**
	 * Handle save action.
	 * Dispatches saveConfig thunk if saving is allowed.
	 * @type {React.MouseEventHandler}
	 */
	// (onSave is handled by wrappedSave below)

	// dialog state for preventing save when file has validation errors
	const [dialogOpen, setDialogOpen] = React.useState(false);
	const [dialogErrors, setDialogErrors] = React.useState([]);

	// editor view ref - set by YamlEditor via onEditorReady
	const editorViewRef = React.useRef(null);

	// helper: scroll to a document position (from,to) and focus
	const scrollToRange = React.useCallback((from, to) => {
		const view = editorViewRef.current;
		if (!view) return;
		view.dispatch({ selection: { anchor: from, head: to } });
		view.focus();
	}, []);

	// validate-then-save: run validation first, then either save or open dialog listing errors
	const wrappedSave = React.useCallback(async () => {
		if (!canSave) return;
		try {
			const res = await dispatch(validateConfig({ name: selected, content: local })).unwrap();
			const result = res?.result ?? { valid: true };
			if (result && result.valid === false) {
				let errs = [];
				if (Array.isArray(result.errors)) errs = result.errors;
				else if (result.error) errs = [{ message: result.error }];

				// If we have an editor view, convert errors to { message, from, to }
				const view = editorViewRef.current;
				if (view) {
					const doc = view.state.doc;
					const converted = errs.map((err) => {
						const message = err.message || String(err || "");
						let from = 0;
						let to = 0;

						// prefer explicit line/column fields
						const hasLine = err.line != null || err.lineNumber != null;
						const lineRaw = err.line ?? err.lineNumber ?? err.ln ?? null;
						const colRaw = err.column ?? err.col ?? err.columnNumber ?? null;

						if (hasLine && lineRaw != null) {
							let lineNum = Number(lineRaw);
							if (Number.isNaN(lineNum) || lineNum < 0) lineNum = 0;
							// doc.line is 1-based. If incoming line looks 0-based, bump it.
							if (lineNum === 0) lineNum = 1;
							if (lineNum > doc.lines) lineNum = doc.lines;
							const line = doc.line(lineNum);
							const colNum = Math.max(1, Number(colRaw) || 1);
							const offsetInLine = Math.max(0, colNum - 1);
							from = Math.min(line.to, Math.max(line.from, line.from + offsetInLine));

							// compute `to` using endLine/endColumn if provided
							if (err.endLine != null || err.endColumn != null) {
								const endLineRaw = err.endLine ?? err.end_line ?? lineNum;
								const endColRaw = err.endColumn ?? err.end_column ?? colNum;
								let endLine = Number(endLineRaw) || lineNum;
								if (endLine <= 0) endLine = 1;
								if (endLine > doc.lines) endLine = doc.lines;
								const endLineObj = doc.line(endLine);
								const endCol = Math.max(1, Number(endColRaw) || 1);
								to = Math.min(
									endLineObj.to,
									Math.max(endLineObj.from, endLineObj.from + (endCol - 1)),
								);
							} else {
								// expand to a sensible token end: word chars or end of line
								let t = from;
								while (t < doc.length) {
									const ch = doc.sliceString(t, t + 1);
									if (!/\w/.test(ch)) break;
									t += 1;
								}
								to = t > from ? t : Math.min(doc.length, from + 1);
							}
						} else if (err.message && typeof err.message === "string") {
							// try to parse "line X, column Y" from message
							const m = /line\s*(\d+),?\s*column\s*(\d+)/i.exec(err.message);
							if (m) {
								let lineNum = Number(m[1]) || 1;
								let colNum = Number(m[2]) || 1;
								if (lineNum <= 0) lineNum = 1;
								if (lineNum > doc.lines) lineNum = doc.lines;
								const line = doc.line(lineNum);
								from = Math.min(line.to, Math.max(line.from, line.from + (colNum - 1)));
								// simple token end
								let t = from;
								while (t < doc.length) {
									const ch = doc.sliceString(t, t + 1);
									if (!/\w/.test(ch)) break;
									t += 1;
								}
								to = t > from ? t : Math.min(doc.length, from + 1);
							} else {
								// fallback: show start of doc
								from = 0;
								to = 0;
							}
						}

						// Clamp
						from = Math.max(0, Math.min(doc.length, from));
						to = Math.max(from, Math.min(doc.length, to || from + 1));

						return { message, from, to, raw: err };
					});
					setDialogErrors(converted);
				} else {
					// no editor available, fall back to message-only errors
					setDialogErrors(errs.map((e) => ({ message: e.message || String(e) })));
				}

				setDialogOpen(true);
				return;
			}
			// if valid, proceed to save
			dispatch(saveConfig({ name: selected, content: local, skipValidation: false }));
		} catch (e) {
			// if validation request itself failed, show generic dialog
			setDialogErrors([{ message: e?.message || "Validation failed" }]);
			setDialogOpen(true);
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

	/**
	 * Handle validation action.
	 * Dispatches validateConfig thunk for the current file.
	 * @type {React.MouseEventHandler}
	 */
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
				open={dialogOpen}
				title="Fix validation errors"
				question={
					<>
						<p>Please fix the following validation errors before saving:</p>
						<ul>
							{dialogErrors.map((e, i) => {
								const msg = e?.message || String(e || "");
								const hasRange = e && typeof e.from === "number";
								return (
									<li key={i}>
										{hasRange ? (
											<button
												type="button"
												onClick={() => {
													scrollToRange(e.from, e.to ?? e.from + 1);
													setDialogOpen(false);
												}}
												style={{
													cursor: "pointer",
													background: "none",
													border: "none",
													padding: 0,
													color: "inherit",
													textDecoration: "underline",
												}}
											>
												{msg}
											</button>
										) : (
											<span>{msg}</span>
										)}
									</li>
								);
							})}
						</ul>
					</>
				}
				actionButtons={[
					{
						btnTitle: "OK",
						callback: () => setDialogOpen(false),
						btnVariant: "contained",
						autoFocus: true,
					},
				]}
				onClose={() => setDialogOpen(false)}
			/>
		</>
	);
});

export default ConfigEditorContainer;
