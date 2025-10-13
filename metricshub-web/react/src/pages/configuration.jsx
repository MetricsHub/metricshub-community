// src/pages/configuration.jsx
import * as React from "react";
import { Box, Button, Chip, CircularProgress, Stack } from "@mui/material";
import RefreshIcon from "@mui/icons-material/Refresh";

import { withAuthGuard } from "../hocs/with-auth-guard";
import { SplitScreen, Left, Right } from "../components/split-screen/split-screen";

import { useAppDispatch, useAppSelector } from "../hooks/store";
import {
	fetchConfigList,
	fetchConfigContent,
	saveConfig,
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

/**
 * Configuration page component.
 * @returns The configuration page with a split view: tree on the left, YAML editor on the right.
 */
function ConfigurationPage() {
	const dispatch = useAppDispatch();
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

	const [deleteOpen, setDeleteOpen] = React.useState(false);
	const [deleteTarget, setDeleteTarget] = React.useState(null);

	/**
	 * Fetch the configuration file list on component mount.
	 * @type {React.EffectCallback}
	 */
	React.useEffect(() => {
		dispatch(fetchConfigList());
	}, [dispatch]);

	/**
	 * Handle selection of a configuration file from the tree.
	 * Fetches content if not already cached.
	 */
	const onSelect = React.useCallback(
		(name) => {
			dispatch(selectFile(name));
			const cached = filesByName?.[name];
			if (cached) {
				dispatch(setContent(cached.content ?? ""));
				return;
			}
			// if the list entry is flagged localOnly, also avoid backend and show whatever we have
			const meta = list.find((f) => f.name === name);
			if (meta?.localOnly) {
				dispatch(setContent(filesByName?.[name]?.content ?? ""));
				return;
			}
			// otherwise fetch from backend
			dispatch(fetchConfigContent(name));
		},
		[dispatch, filesByName, list],
	);

	/**
	 * Handle inline renaming of a configuration file.
	 * Decides between local rename and backend rename based on file metadata.
	 */
	const handleInlineRename = React.useCallback(
		(oldName, newName) => {
			const meta = list.find((f) => f.name === oldName);
			if (meta?.localOnly) {
				dispatch(renameLocalFile({ oldName, newName }));
			} else {
				dispatch(renameConfig({ oldName, newName }));
			}
		},
		[dispatch, list],
	);

	/**
	 * Handle saving the current configuration file.
	 * No-op if no file is selected or already saving.
	 */
	const onSave = React.useCallback(
		(doc) => {
			if (!selected) return;
			dispatch(saveConfig({ name: selected, content: doc, skipValidation: false }));
		},
		[dispatch, selected],
	);

	/**
	 * Whether we have already auto-selected a file.
	 */
	const didAutoSelect = React.useRef(false);

	/**
	 * Auto-select the first file in the list if none is selected.
	 */
	React.useEffect(() => {
		if (didAutoSelect.current) return;
		if (!selected && list?.length > 0) {
			didAutoSelect.current = true;
			onSelect(list[0].name);
		}
	}, [list, selected, onSelect]);

	/**
	 * Handle validation request from the editor header.
	 */
	const onValidate = React.useCallback(() => {
		if (!selected) return;
		dispatch(validateConfig({ name: selected, content }));
	}, [dispatch, selected, content]);

	/**
	 * Open the delete confirmation dialog for a given file.
	 */
	const openDelete = React.useCallback((fileName) => {
		setDeleteTarget(fileName);
		setDeleteOpen(true);
	}, []);

	/**
	 * Submit the delete action after confirmation.
	 */
	const submitDelete = React.useCallback(() => {
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
		setDeleteOpen(false);
	}, [dispatch, deleteTarget, list]);

	return (
		<SplitScreen initialLeftPct={35}>
			<Left>
				<Stack spacing={1.5} sx={{ p: 1.5 }}>
					<Stack direction="row" spacing={1} alignItems="center">
						<Button
							size="small"
							startIcon={<RefreshIcon />}
							onClick={() => dispatch(fetchConfigList())}
						>
							Refresh
						</Button>

						<Button
							size="small"
							//variant="outlined"
							component="label"
							startIcon={<UploadFileIcon />}
						>
							Upload
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
						onSave={() => onSave(content)}
						canSave={!!selected && !saving}
					/>
					<Box sx={{ height: "calc(100vh - 160px)" }}>
						<ConfigEditorContainer />
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
