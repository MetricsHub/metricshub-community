// src/pages/configuration.jsx
import * as React from "react";
import { useEffect, useCallback, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Box, Button, Chip, CircularProgress, Stack } from "@mui/material";
import RefreshIcon from "@mui/icons-material/Refresh";

import { withAuthGuard } from "../hocs/with-auth-guard";
import { SplitScreen, Left, Right } from "../components/split-screen/split-screen";

import { useAppDispatch, useAppSelector } from "../hooks/store";
import {
	fetchConfigList,
	fetchConfigContent,
	validateConfig,
	deleteConfig,
	renameConfig,
} from "../store/thunks/configThunks";
import {
	select as selectFile,
	addLocalFile,
	setContent,
	renameLocalFile,
	deleteLocalFile,
} from "../store/slices/configSlice";
import EditorHeader from "../components/config/EditorHeader";
import ConfigEditorContainer from "../components/config/Editor/ConfigEditorContainer";
import ConfigTree from "../components/config/Tree/ConfigTree";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import QuestionDialog from "../components/common/QuestionDialog";
import DeleteIcon from "@mui/icons-material/Delete";
import { paths } from "../paths";

/**
 * Configuration page component.
 * URL is the source of truth for selection: /configuration/:name
 */
function ConfigurationPage() {
	const dispatch = useAppDispatch();
	const navigate = useNavigate();
	const { name: routeName } = useParams();
	const {
		list,
		filesByName,
		selected,
		content,
		loadingList,
		loadingContent,
		saving,
		validation,
		error,
	} = useAppSelector((s) => s.config);

	const [deleteOpen, setDeleteOpen] = useState(false);
	const [deleteTarget, setDeleteTarget] = useState(null);

	useEffect(() => {
		dispatch(fetchConfigList());
	}, [dispatch]);

	useEffect(() => {
		const target = routeName ? decodeURIComponent(routeName) : null;

		if (!target) {
			if (selected) dispatch(selectFile(null));
			return;
		}

		if (target !== selected) {
			const cached = filesByName?.[target];
			const isLocalOnly = list.find((f) => f.name === target)?.localOnly;

			dispatch(selectFile(target));
			if (cached || isLocalOnly) {
				dispatch(setContent(cached?.content ?? ""));
			} else {
				dispatch(fetchConfigContent(target));
			}
		}
	}, [routeName, selected, filesByName, list, dispatch]);

	useEffect(() => {
		if (!routeName && list?.length > 0) {
			navigate(paths.configurationFile(list[0].name), { replace: true });
		}
	}, [routeName, list, navigate]);

	const onSelect = useCallback(
		(name) => {
			if (!name) return;
			const url = paths.configurationFile(name);
			if (url !== window.location.pathname) {
				navigate(url, { replace: false });
			}
		},
		[navigate],
	);

	const handleInlineRename = useCallback(
		(oldName, newName) => {
			const meta = list.find((f) => f.name === oldName);
			if (meta?.localOnly) {
				dispatch(renameLocalFile({ oldName, newName }));
			} else {
				dispatch(renameConfig({ oldName, newName }));
			}
			if (routeName && decodeURIComponent(routeName) === oldName) {
				navigate(paths.configurationFile(newName), { replace: true });
			}
		},
		[dispatch, list, routeName, navigate],
	);

	// onSave is handled by the editor container (validate-then-save). Header will call container.save via ref.

	const onValidate = useCallback(() => {
		if (!selected) return;
		dispatch(validateConfig({ name: selected, content }));
	}, [dispatch, selected, content]);

	const openDelete = useCallback((fileName) => {
		setDeleteTarget(fileName);
		setDeleteOpen(true);
	}, []);
	const submitDelete = useCallback(() => {
		if (!deleteTarget) {
			setDeleteOpen(false);
			return;
		}
		const meta = list.find((f) => f.name === deleteTarget);
		if (meta?.localOnly) {
			dispatch(deleteLocalFile(deleteTarget));
		} else {
			dispatch(deleteConfig(deleteTarget));
		}
		if (selected === deleteTarget) {
			navigate(paths.configuration, { replace: true });
		}
		setDeleteOpen(false);
	}, [dispatch, deleteTarget, list, selected, navigate]);

	const editorRef = React.useRef(null);

	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Stack spacing={1.5} sx={{ p: 1.5 }}>
					<Stack direction="row" spacing={1} alignItems="center">
						<Button
							size="small"
							variant="outlined"
							color="inherit"
							startIcon={<RefreshIcon />}
							onClick={() => dispatch(fetchConfigList())}
						>
							Refresh
						</Button>

						<Button
							size="small"
							color="inherit"
							variant="outlined"
							component="label"
							startIcon={<UploadFileIcon />}
						>
							Import
							<input
								type="file"
								accept=".yaml,.yml"
								hidden
								onChange={(e) => {
									const file = e.target.files?.[0];
									if (!file) return;
									const reader = new FileReader();
									reader.onload = (evt) => {
										const content = evt.target.result;
										dispatch(addLocalFile({ name: file.name, content }));
										// Navigate to new local file so URL reflects selection
										navigate(paths.configurationFile(file.name), { replace: false });
									};
									reader.readAsText(file);
								}}
							/>
						</Button>

						{loadingList && <CircularProgress size={18} />}
						{error && <Chip size="small" color="error" label={error} sx={{ maxWidth: 280 }} />}
					</Stack>

					<ConfigTree
						files={list}
						selectedName={selected}
						onSelect={onSelect}
						onRename={handleInlineRename}
						onDelete={openDelete}
					/>

					<QuestionDialog
						open={deleteOpen}
						title="Delete file"
						question={`Are you sure you want to delete “${deleteTarget ?? ""}”? This action cannot be undone.`}
						onClose={() => setDeleteOpen(false)}
						actionButtons={[
							{ btnTitle: "Cancel", callback: () => setDeleteOpen(false), autoFocus: true },
							{
								btnTitle: "Delete",
								btnColor: "error",
								btnVariant: "contained",
								btnIcon: <DeleteIcon />,
								callback: submitDelete,
							},
						]}
					/>
				</Stack>
			</Left>

			<Right>
				<Stack sx={{ p: 1.5, gap: 1 }}>
					<EditorHeader
						selected={selected}
						saving={saving}
						validation={validation}
						onValidate={onValidate}
						onSave={() => editorRef.current?.save?.()}
						canSave={!!selected && !saving}
					/>
					<Box sx={{ height: "calc(100vh - 160px)" }}>
						<ConfigEditorContainer ref={editorRef} />
					</Box>
					{loadingContent && (
						<Stack direction="row" alignItems="center" spacing={1}>
							<CircularProgress size={18} />
							Loading content…
						</Stack>
					)}
				</Stack>
			</Right>
		</SplitScreen>
	);
}

export default withAuthGuard(ConfigurationPage);
