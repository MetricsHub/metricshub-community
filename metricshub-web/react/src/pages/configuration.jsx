// src/pages/configuration.jsx
import * as React from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Box, Button, Chip, CircularProgress, Stack } from "@mui/material";
import RefreshIcon from "@mui/icons-material/Autorenew";

import { withAuthGuard } from "../hocs/with-auth-guard";
import { SplitScreen, Left, Right } from "../components/split-screen/split-screen";

import { useAppDispatch, useAppSelector } from "../hooks/store";
import {
	fetchConfigList,
	fetchConfigContent,
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
import ConfigEditorContainer from "../components/config/editor/ConfigEditorContainer";
import ConfigTree from "../components/config/tree/ConfigTree";
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
		loadingList,
		loadingContent,
		saving,
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
	 * Sync selected file with URL parameter.
	 * Fetch content if not already cached.
	 * @type {React.EffectCallback}
	 */
	React.useEffect(() => {
		const target = routeName ? decodeURIComponent(routeName) : null;

		if (!target) {
			if (selected) dispatch(selectFile(null));
			return;
		}

		if (target !== selected) {
			const cached = filesByName?.[target];
			const isLocalOnly = list.some((f) => f.name === target && f.localOnly);
			const hasCachedContent = typeof cached?.content === "string";

			dispatch(selectFile(target));
			if (isLocalOnly) {
				dispatch(setContent(cached?.content ?? ""));
				return;
			}
			// for normal files, prefer backend unless we truly have content in cache
			if (hasCachedContent) {
				dispatch(setContent(cached.content));
			} else {
				dispatch(fetchConfigContent(target));
			}
		}
	}, [routeName, selected, filesByName, list, dispatch]);

	/**
	 * Auto-navigate to first file if none is selected and files are available.
	 */
	React.useEffect(() => {
		if (!routeName && list?.length > 0) {
			navigate(paths.configurationFile(list[0].name), { replace: true });
		}
	}, [routeName, list, navigate]);

	/**
	 * Handle selection of a configuration file from the tree.
	 * Fetches content if not already cached.
	 */
	const onSelect = React.useCallback(
		(name) => {
			if (!name) return;
			const url = paths.configurationFile(name);
			if (url !== window.location.pathname) {
				navigate(url, { replace: false });
			}
		},
		[navigate],
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
			if (routeName && decodeURIComponent(routeName) === oldName) {
				navigate(paths.configurationFile(newName), { replace: true });
			}
		},
		[dispatch, list, routeName, navigate],
	);

	// Auto-select is handled via routeName: when no route is set and list is available,
	// we navigate to the first file in the dedicated effect above.

	// Validation is handled elsewhere; header no longer triggers validation directly.

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

			<Right disableScroll>
				<Stack
					sx={{
						px: 1.5,
						pt: 0,
						pb: 1.5,
						gap: 1,
						height: "100%",
						// Allow right pane to scroll for non-editor content (e.g., messages),
						// while we also keep the editor itself fully height-constrained so
						// CodeMirror remains the primary scroll area for the document.
						overflow: "auto",
						minHeight: 0,
					}}
				>
					<Box
						sx={{
							position: "sticky",
							top: 0,
							zIndex: (t) => t.zIndex.appBar,
							mx: -1.5,
							px: 1.5,
							py: 1,
							bgcolor: "background.default",
							borderBottom: 1,
							borderColor: "divider",
						}}
					>
						<EditorHeader
							selected={selected}
							saving={saving}
							onSave={() => editorRef.current?.save?.()}
						/>
					</Box>

					{/* Let the editor take the remaining space without vh hacks */}
					<Box sx={{ flex: 1, minHeight: 0 }}>
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
