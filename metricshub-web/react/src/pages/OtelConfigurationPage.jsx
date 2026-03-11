// src/pages/OtelConfigurationPage.jsx
import * as React from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
	Box,
	Button,
	CircularProgress,
	Menu,
	MenuItem,
	Stack,
	Drawer,
	IconButton,
	Typography,
	useMediaQuery,
} from "@mui/material";
import RefreshIcon from "@mui/icons-material/Autorenew";
import FolderIcon from "@mui/icons-material/Folder";
import CloseIcon from "@mui/icons-material/Close";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import DeleteIcon from "@mui/icons-material/Delete";

import { SplitScreen, Left, Right } from "../components/split-screen/SplitScreen";
import { useAppDispatch, useAppSelector } from "../hooks/store";
import {
	fetchOtelConfigList,
	fetchOtelConfigContent,
	deleteOtelConfig,
	renameOtelConfig,
	saveDraftOtelConfig,
} from "../store/thunks/otel-config-thunks";
import {
	select as selectFile,
	setContent,
	addLocalFile,
	renameLocalFile,
	deleteLocalFile,
	clearError,
} from "../store/slices/otel-config-slice";
import OtelEditorHeader from "../components/otel/OtelEditorHeader";
import OtelConfigEditorContainer from "../components/otel/editor/OtelConfigEditorContainer";
import OtelConfigTree from "../components/otel/OtelConfigTree";
import OtelCollectorToolbar from "../components/otel/OtelCollectorToolbar";
import QuestionDialog from "../components/common/QuestionDialog";
import { paths } from "../paths";
import { useSnackbar } from "../hooks/use-snackbar";
import { isBackupFileName } from "../utils/backup-names";
import { useAuth } from "../hooks/use-auth";

/**
 * OTEL configuration page. URL is the source of truth: /configuration/otel/:name
 */
function OtelConfigurationPage() {
	const dispatch = useAppDispatch();
	const navigate = useNavigate();
	const snackbar = useSnackbar();
	const { user } = useAuth();
	const isReadOnly = user?.role === "ro";
	const isSmallScreen = useMediaQuery("(max-width:900px)");
	const [drawerOpen, setDrawerOpen] = React.useState(false);
	const { name: routeName } = useParams();
	const { list, filesByName, selected, loadingList, loadingContent, saving, error } =
		useAppSelector((s) => s.otelConfig);

	const [deleteOpen, setDeleteOpen] = React.useState(false);
	const [deleteTarget, setDeleteTarget] = React.useState(null);

	React.useEffect(() => {
		if (error) {
			snackbar.show(error, { severity: "error" });
			dispatch(clearError());
		}
	}, [error, snackbar, dispatch]);

	React.useEffect(() => {
		dispatch(fetchOtelConfigList());
	}, [dispatch]);

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
			if (hasCachedContent) {
				dispatch(setContent(cached.content));
			} else {
				dispatch(fetchOtelConfigContent(target));
			}
		}
	}, [routeName, selected, filesByName, list, dispatch]);

	React.useEffect(() => {
		if (!routeName && list?.length > 0) {
			const first = list.find((f) => !isBackupFileName(f?.name));
			if (first) {
				navigate(paths.configurationOtelFile(first.name), { replace: true });
			}
		}
	}, [routeName, list, navigate]);

	const onSelect = React.useCallback(
		(name) => {
			if (!name) return;
			const url = paths.configurationOtelFile(name);
			if (url !== window.location.pathname) {
				navigate(url, { replace: false });
			}
			if (isSmallScreen) setDrawerOpen(false);
		},
		[navigate, isSmallScreen],
	);

	const handleInlineRename = React.useCallback(
		async (oldName, newName) => {
			const meta = list.find((f) => f.name === oldName);
			if (meta?.localOnly) {
				dispatch(renameLocalFile({ oldName, newName }));
			} else {
				await dispatch(renameOtelConfig({ oldName, newName })).unwrap();
			}
			if (routeName && decodeURIComponent(routeName) === oldName) {
				navigate(paths.configurationOtelFile(newName), { replace: true });
			}
		},
		[dispatch, list, routeName, navigate],
	);

	const openDelete = React.useCallback((fileName) => {
		setDeleteTarget(fileName);
		setDeleteOpen(true);
	}, []);

	const submitDelete = React.useCallback(async () => {
		if (!deleteTarget) {
			setDeleteOpen(false);
			return;
		}
		const meta = list.find((f) => f.name === deleteTarget);

		let nextSelected = null;
		if (selected === deleteTarget) {
			const visibleFiles = list
				.filter((f) => !isBackupFileName(f.name))
				.sort((a, b) => a.name.localeCompare(b.name));
			const idx = visibleFiles.findIndex((f) => f.name === deleteTarget);
			if (idx >= 0 && visibleFiles.length > 1) {
				const nextIdx = idx + 1 < visibleFiles.length ? idx + 1 : idx - 1;
				nextSelected = visibleFiles[nextIdx]?.name;
			}
		}

		if (selected === deleteTarget) {
			if (nextSelected) {
				navigate(paths.configurationOtelFile(nextSelected), { replace: true });
			} else {
				navigate(paths.configurationOtel, { replace: true });
			}
		}

		setTimeout(() => {
			if (meta?.localOnly) {
				dispatch(deleteLocalFile(deleteTarget));
			} else {
				dispatch(deleteOtelConfig(deleteTarget));
			}
		}, 50);

		setDeleteOpen(false);
	}, [dispatch, deleteTarget, list, selected, navigate]);

	const handleCreate = React.useCallback(() => {
		const base = "new-otel-config.yaml.draft";
		let name = base;
		let i = 1;
		while (list.some((f) => f.name === name)) {
			name = `new-otel-config-${i}.yaml.draft`;
			i++;
		}
		const content = "# OpenTelemetry Collector Configuration\n\n";
		dispatch(addLocalFile({ name, content }));
		dispatch(saveDraftOtelConfig({ name, content, skipValidation: true }));
		navigate(paths.configurationOtelFile(name), { replace: false });
	}, [dispatch, list, navigate]);

	const [createMenuAnchor, setCreateMenuAnchor] = React.useState(null);
	const openCreateMenu = React.useCallback((e) => setCreateMenuAnchor(e.currentTarget), []);
	const closeCreateMenu = React.useCallback(() => setCreateMenuAnchor(null), []);

	const editorRef = React.useRef(null);

	const handleMakeDraft = React.useCallback(
		async (fileName) => {
			const newName = fileName + ".draft";
			if (list.some((f) => f.name === fileName && f.localOnly)) {
				dispatch(renameLocalFile({ oldName: fileName, newName }));
			} else {
				await dispatch(renameOtelConfig({ oldName: fileName, newName })).unwrap();
			}
			if (selected === fileName) {
				navigate(paths.configurationOtelFile(newName), { replace: true });
			}
		},
		[dispatch, list, selected, navigate],
	);

	const treeContent = (
		<Stack spacing={1.5} sx={{ p: 1.5 }}>
			<Stack direction="row" spacing={1} alignItems="center">
				<Button
					size="small"
					variant="outlined"
					color="inherit"
					startIcon={<RefreshIcon />}
					onClick={() => dispatch(fetchOtelConfigList())}
				>
					Refresh
				</Button>
				<Button
					size="small"
					variant="outlined"
					color="inherit"
					endIcon={<KeyboardArrowDownIcon />}
					onClick={openCreateMenu}
					disabled={isReadOnly}
				>
					Create
				</Button>
				<Menu
					anchorEl={createMenuAnchor}
					open={Boolean(createMenuAnchor)}
					onClose={closeCreateMenu}
					disableRestoreFocus
					disableAutoFocusItem
					anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
					transformOrigin={{ vertical: "top", horizontal: "left" }}
				>
					<MenuItem
						onClick={() => {
							closeCreateMenu();
							handleCreate();
						}}
					>
						New YAML config
					</MenuItem>
				</Menu>
				<Button
					size="small"
					color="inherit"
					variant="outlined"
					component="label"
					startIcon={<UploadFileIcon />}
					disabled={isReadOnly}
				>
					Import
					<input
						type="file"
						accept=".yaml,.yml"
						hidden
						disabled={isReadOnly}
						onChange={(e) => {
							const file = e.target.files?.[0];
							if (!file) return;
							const reader = new FileReader();
							reader.onload = (evt) => {
								const content = evt.target.result;
								dispatch(addLocalFile({ name: file.name, content }));
								navigate(paths.configurationOtelFile(file.name), { replace: false });
							};
							reader.readAsText(file);
						}}
					/>
				</Button>
				{loadingList && <CircularProgress size={18} />}
			</Stack>
			<OtelConfigTree
				files={list}
				selectedName={selected}
				onSelect={onSelect}
				onRename={handleInlineRename}
				onDelete={openDelete}
				onMakeDraft={handleMakeDraft}
			/>
			<QuestionDialog
				open={deleteOpen}
				title="Delete file"
				question={`Are you sure you want to delete "${deleteTarget ?? ""}"? This action cannot be undone.`}
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
	);

	const smallScreenHeader = (
		<Stack direction="row" alignItems="center" spacing={1} sx={{ px: 1.5, py: 1 }}>
			<IconButton
				size="small"
				onClick={() => setDrawerOpen(true)}
				sx={{ border: 1, borderColor: "divider", borderRadius: 1 }}
			>
				<FolderIcon fontSize="small" />
			</IconButton>
			<Typography variant="body2" noWrap sx={{ flex: 1 }}>
				{selected || "OTEL files"}
			</Typography>
		</Stack>
	);

	return (
		<>
			<Drawer
				anchor="left"
				open={drawerOpen}
				onClose={() => setDrawerOpen(false)}
				PaperProps={{ sx: { width: "85%", maxWidth: 360 } }}
			>
				<Stack
					direction="row"
					alignItems="center"
					justifyContent="space-between"
					sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: "divider" }}
				>
					<Typography variant="h6">OTEL files</Typography>
					<IconButton size="small" onClick={() => setDrawerOpen(false)}>
						<CloseIcon />
					</IconButton>
				</Stack>
				{treeContent}
			</Drawer>

			<SplitScreen initialLeftPct={35} smallScreenHeader={smallScreenHeader}>
				<Left>{treeContent}</Left>
				<Right disableScroll>
					<Stack
						sx={{
							px: 1.5,
							pt: 0,
							pb: 1.5,
							gap: 1,
							height: "100%",
							overflow: "auto",
							minHeight: 0,
							transition: "background-color 0.4s ease, color 0.4s ease",
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
								transition: "background-color 0.4s ease, border-color 0.4s ease",
							}}
						>
							<OtelEditorHeader
								selected={selected}
								saving={saving}
								onSave={() => editorRef.current?.save?.()}
								onApply={() => editorRef.current?.apply?.()}
								isReadOnly={isReadOnly}
							/>
						</Box>
						<Box sx={{ px: 1.5, pb: 0.5 }}>
							<OtelCollectorToolbar isReadOnly={isReadOnly} />
						</Box>
						<Box sx={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "row" }}>
							<Box sx={{ flex: 1, minWidth: 0 }}>
								<OtelConfigEditorContainer ref={editorRef} />
							</Box>
						</Box>
						{loadingContent && (
							<Stack
								direction="row"
								alignItems="center"
								spacing={1}
								sx={{ transition: "color 0.4s ease" }}
							>
								<CircularProgress size={18} />
								Loading content…
							</Stack>
						)}
					</Stack>
				</Right>
			</SplitScreen>
		</>
	);
}

export default OtelConfigurationPage;
