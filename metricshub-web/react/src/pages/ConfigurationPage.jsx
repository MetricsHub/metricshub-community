// src/pages/ConfigurationPage.jsx
import * as React from "react";
import { useNavigate, useLocation } from "react-router-dom";
import {
	Box,
	Button,
	CircularProgress,
	Menu,
	MenuItem,
	Stack,
	Drawer,
	IconButton,
	Tooltip,
	Typography,
	useMediaQuery,
} from "@mui/material";
import RefreshIcon from "@mui/icons-material/Autorenew";
import ReevaluateIcon from "@mui/icons-material/PublishedWithChanges";
import FolderIcon from "@mui/icons-material/Folder";
import CloseIcon from "@mui/icons-material/Close";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import DeleteIcon from "@mui/icons-material/Delete";

import {
	SplitScreen,
	Left,
	Right,
	scrollbarSx,
	scrollbarThumbSx,
} from "../components/split-screen/SplitScreen";
import {
	ResizableVerticalSplit,
	Top as SplitTop,
	Bottom as SplitBottom,
} from "../components/split-screen/ResizableVerticalSplit";
import { useAppDispatch, useAppSelector } from "../hooks/store";
import { useOtelCollectorLogs } from "../hooks/use-otel-collector-logs";
import {
	fetchConfigList,
	fetchConfigContent,
	deleteConfig,
	renameConfig,
	saveDraftConfig,
	testVelocityTemplate,
	reevaluateTemplate,
	reevaluateConfiguration,
} from "../store/thunks/config-thunks";
import {
	fetchOtelConfigList,
	fetchOtelConfigContent,
	deleteOtelConfig,
	renameOtelConfig,
	saveDraftOtelConfig,
} from "../store/thunks/otel-config-thunks";
import {
	select as selectConfigFile,
	addLocalFile,
	setContent as setConfigContent,
	renameLocalFile,
	deleteLocalFile,
	clearError as clearConfigError,
	clearVelocityTestResult,
	clearReevaluation,
} from "../store/slices/config-slice";
import {
	select as selectOtelFile,
	setContent as setOtelContent,
	addLocalFile as addOtelLocalFile,
	renameLocalFile as renameOtelLocalFile,
	deleteLocalFile as deleteOtelLocalFile,
	clearError as clearOtelError,
} from "../store/slices/otel-config-slice";
import EditorHeader from "../components/config/EditorHeader";
import ConfigEditorContainer from "../components/config/editor/ConfigEditorContainer";
import VelocityTestResultPanel from "../components/config/editor/VelocityTestResultPanel";
import OtelEditorHeader from "../components/otel/OtelEditorHeader";
import OtelConfigEditorContainer from "../components/otel/editor/OtelConfigEditorContainer";

import OtelCollectorLogPanel from "../components/otel/OtelCollectorLogPanel";
import UnifiedConfigTree from "../components/config/tree/UnifiedConfigTree";
import QuestionDialog from "../components/common/QuestionDialog";
import { paths } from "../paths";
import { useSnackbar } from "../hooks/use-snackbar";
import { isBackupFileName } from "../utils/backup-names";
import { isVmFile, isDraftFile, isSameConfigFile } from "../utils/file-type-utils";
import { useAuth } from "../hooks/use-auth";

/** Last file the user had open in the Configuration editor (restored when returning). */
const CONFIG_LAST_PATH_KEY = "metricshub-configuration:last-path";

function parseConfigurationPath(pathname) {
	if (!pathname || !pathname.startsWith("/configuration")) return { repo: null, name: null };
	if (pathname === "/configuration") return { repo: null, name: null };
	if (pathname === "/configuration/yaml-editor") return { repo: null, name: null };
	const configPrefix = "/configuration/yaml-editor/config/";
	const otelPrefix = "/configuration/yaml-editor/otel/";
	if (pathname.startsWith(configPrefix)) {
		const name = decodeURIComponent(pathname.slice(configPrefix.length));
		return { repo: "config", name: name || null };
	}
	if (pathname.startsWith(otelPrefix)) {
		const name = decodeURIComponent(pathname.slice(otelPrefix.length));
		return { repo: "otel", name: name || null };
	}
	return { repo: null, name: null };
}

/**
 * Single configuration page: tree on the left (roots "config" and "otel"), editor on the right.
 * URL is the source of truth:
 * /configuration/yaml-editor,
 * /configuration/yaml-editor/config/:name,
 * /configuration/yaml-editor/otel/:name.
 */
function ConfigurationPage() {
	const dispatch = useAppDispatch();
	const navigate = useNavigate();
	const location = useLocation();
	const snackbar = useSnackbar();
	const { user } = useAuth();
	const isReadOnly = user?.role === "ro";
	const isSmallScreen = useMediaQuery("(max-width:900px)");
	const [drawerOpen, setDrawerOpen] = React.useState(false);
	const [otelLogsOpen, setOtelLogsOpen] = React.useState(false);
	const otelLogs = useOtelCollectorLogs();

	const { repo: routeRepo, name: routeName } = parseConfigurationPath(location.pathname);

	const configState = useAppSelector((s) => s.config);
	const otelState = useAppSelector((s) => s.otelConfig);
	const {
		list: configList,
		filesByName: configFilesByName,
		selected: configSelected,
		loadingList: configLoadingList,
		loadingContent: configLoadingContent,
		saving: configSaving,
		error: configError,
	} = configState;
	const {
		list: otelList,
		filesByName: otelFilesByName,
		selected: otelSelected,
		loadingList: otelLoadingList,
		loadingContent: otelLoadingContent,
		saving: otelSaving,
		error: otelError,
	} = otelState;
	const velocityTestResult = useAppSelector((s) => s.config.velocityTestResult);
	const reevaluation = useAppSelector((s) => s.config.reevaluation);

	const [deleteOpen, setDeleteOpen] = React.useState(false);
	const [deleteTarget, setDeleteTarget] = React.useState(null); // { repo, name }
	const [reloadConfirmOpen, setReloadConfirmOpen] = React.useState(false);

	React.useEffect(() => {
		if (configError) {
			snackbar.show(configError, { severity: "error" });
			dispatch(clearConfigError());
		}
	}, [configError, snackbar, dispatch]);
	React.useEffect(() => {
		if (otelError) {
			snackbar.show(otelError, { severity: "error" });
			dispatch(clearOtelError());
		}
	}, [otelError, snackbar, dispatch]);

	// A re-evaluation (one template, or the whole configuration) reports what it did. Without this,
	// a failure would only stop the spinner and the user would be left guessing.
	React.useEffect(() => {
		if (!reevaluation || reevaluation.loading) return;
		if (reevaluation.error) {
			snackbar.show(reevaluation.error, { severity: "error" });
		} else if (reevaluation.message) {
			snackbar.show(reevaluation.message, { severity: "success" });
		}
		dispatch(clearReevaluation());
	}, [reevaluation, snackbar, dispatch]);

	React.useEffect(() => {
		dispatch(fetchConfigList());
		dispatch(fetchOtelConfigList());
	}, [dispatch]);

	// Sync URL (routeRepo, routeName) to the appropriate slice and fetch content
	React.useEffect(() => {
		if (routeRepo === "config") {
			const target = routeName;
			if (!target) {
				if (configSelected) dispatch(selectConfigFile(null));
				return;
			}
			if (target !== configSelected) {
				const cached = configFilesByName?.[target];
				const isLocalOnly = configList.some((f) => f.name === target && f.localOnly);
				const hasCached = typeof cached?.content === "string";
				dispatch(selectConfigFile(target));
				if (isLocalOnly) {
					dispatch(setConfigContent(cached?.content ?? ""));
					return;
				}
				if (hasCached) dispatch(setConfigContent(cached.content));
				else dispatch(fetchConfigContent(target));
			}
			return;
		}
		if (routeRepo === "otel") {
			const target = routeName;
			if (!target) {
				if (otelSelected) dispatch(selectOtelFile(null));
				return;
			}
			if (target !== otelSelected) {
				const cached = otelFilesByName?.[target];
				const isLocalOnly = otelList.some((f) => f.name === target && f.localOnly);
				const hasCached = typeof cached?.content === "string";
				dispatch(selectOtelFile(target));
				if (isLocalOnly) {
					dispatch(setOtelContent(cached?.content ?? ""));
					return;
				}
				if (hasCached) dispatch(setOtelContent(cached.content));
				else dispatch(fetchOtelConfigContent(target));
			}
			return;
		}
		if (!routeRepo && (configSelected || otelSelected)) {
			dispatch(selectConfigFile(null));
			dispatch(selectOtelFile(null));
		}
	}, [
		routeRepo,
		routeName,
		configSelected,
		otelSelected,
		configList,
		otelList,
		configFilesByName,
		otelFilesByName,
		dispatch,
	]);

	// Remember the open file so returning to /configuration lands on the same page.
	React.useEffect(() => {
		if (!routeRepo || !routeName) return;
		try {
			sessionStorage.setItem(CONFIG_LAST_PATH_KEY, location.pathname);
		} catch {
			// ignore quota errors
		}
	}, [routeRepo, routeName, location.pathname]);

	// Auto-navigate when on /configuration with no selection: last visited file
	// first (if it still exists), then the first available file.
	React.useEffect(() => {
		if (routeRepo || routeName) return;
		const lastPath = sessionStorage.getItem(CONFIG_LAST_PATH_KEY);
		if (lastPath) {
			const parsed = parseConfigurationPath(lastPath);
			const stillExists =
				parsed.repo === "config"
					? (configList || []).some((f) => f.name === parsed.name)
					: parsed.repo === "otel"
						? (otelList || []).some((f) => f.name === parsed.name)
						: false;
			if (stillExists) {
				navigate(lastPath, { replace: true });
				return;
			}
		}
		const configFiles = (configList || []).filter((f) => !isBackupFileName(f.name));
		const otelFiles = (otelList || []).filter((f) => !isBackupFileName(f.name));
		if (configFiles.length > 0) {
			navigate(paths.configurationFile(configFiles[0].name), { replace: true });
		} else if (otelFiles.length > 0) {
			navigate(paths.configurationOtelFile(otelFiles[0].name), { replace: true });
		}
	}, [routeRepo, routeName, configList, otelList, navigate]);

	const onSelect = React.useCallback(
		(selectedRepo, selectedName) => {
			if (!selectedRepo || !selectedName) return;
			const url =
				selectedRepo === "config"
					? paths.configurationFile(selectedName)
					: paths.configurationOtelFile(selectedName);
			if (url !== location.pathname) navigate(url, { replace: false });
			if (isSmallScreen) setDrawerOpen(false);
		},
		[navigate, isSmallScreen, location.pathname],
	);

	const handleRenameConfig = React.useCallback(
		async (oldName, newName) => {
			const meta = configList.find((f) => f.name === oldName);
			if (meta?.localOnly) {
				dispatch(renameLocalFile({ oldName, newName }));
			} else {
				await dispatch(renameConfig({ oldName, newName })).unwrap();
			}
			if (routeRepo === "config" && routeName === oldName) {
				navigate(paths.configurationFile(newName), { replace: true });
			}
		},
		[dispatch, configList, routeRepo, routeName, navigate],
	);

	const handleRenameOtel = React.useCallback(
		async (oldName, newName) => {
			const meta = otelList.find((f) => f.name === oldName);
			if (meta?.localOnly) {
				dispatch(renameOtelLocalFile({ oldName, newName }));
			} else {
				await dispatch(renameOtelConfig({ oldName, newName })).unwrap();
			}
			if (routeRepo === "otel" && routeName === oldName) {
				navigate(paths.configurationOtelFile(newName), { replace: true });
			}
		},
		[dispatch, otelList, routeRepo, routeName, navigate],
	);

	const openDelete = React.useCallback((targetRepo, fileName) => {
		setDeleteTarget({ repo: targetRepo, name: fileName });
		setDeleteOpen(true);
	}, []);

	const submitDelete = React.useCallback(() => {
		if (!deleteTarget) {
			setDeleteOpen(false);
			return;
		}
		const { repo, name } = deleteTarget;
		const list = repo === "config" ? configList : otelList;
		const visibleFiles = list
			.filter((f) => !isBackupFileName(f.name))
			.sort((a, b) => a.name.localeCompare(b.name));
		const currentSelected = repo === "config" ? configSelected : otelSelected;
		let nextName = null;
		const idx = visibleFiles.findIndex((f) => f.name === name);
		if (idx >= 0 && visibleFiles.length > 1) {
			const nextIdx = idx + 1 < visibleFiles.length ? idx + 1 : idx - 1;
			nextName = visibleFiles[nextIdx]?.name;
		}
		if (currentSelected === name) {
			if (nextName) {
				navigate(
					repo === "config"
						? paths.configurationFile(nextName)
						: paths.configurationOtelFile(nextName),
					{ replace: true },
				);
			} else {
				navigate(paths.configuration, { replace: true });
			}
		}
		setTimeout(() => {
			const meta = list.find((f) => f.name === name);
			if (repo === "config") {
				if (meta?.localOnly) dispatch(deleteLocalFile(name));
				else dispatch(deleteConfig(name));
			} else {
				if (meta?.localOnly) dispatch(deleteOtelLocalFile(name));
				else dispatch(deleteOtelConfig(name));
			}
		}, 50);
		setDeleteOpen(false);
		setDeleteTarget(null);
	}, [deleteTarget, configList, otelList, configSelected, otelSelected, navigate, dispatch]);

	const handleCreateConfig = React.useCallback(
		(type = "yaml") => {
			const ext = type === "vm" ? "vm" : "yaml";
			// Compare on the base name: a new file is created as a draft, but it is saved without the
			// ".draft" suffix. Matching the draft name alone would miss the saved "new-config.vm" and
			// the new draft would silently overwrite it once saved.
			let name = `new-config.${ext}.draft`;
			let i = 1;
			while (configList.some((f) => isSameConfigFile(f.name, name))) {
				name = `new-config-${i}.${ext}.draft`;
				i++;
			}
			const content =
				type === "vm"
					? "## MetricsHub Velocity Configuration Template\nresources:\n\n"
					: "# MetricsHub Configuration\n\n";
			dispatch(addLocalFile({ name, content }));
			dispatch(saveDraftConfig({ name, content, skipValidation: true }));
			navigate(paths.configurationFile(name), { replace: false });
		},
		[dispatch, configList, navigate],
	);

	const handleCreateOtel = React.useCallback(() => {
		// Same base-name comparison as handleCreateConfig: a saved file has no ".draft" suffix.
		let name = "new-otel-config.yaml.draft";
		let i = 1;
		while (otelList.some((f) => isSameConfigFile(f.name, name))) {
			name = `new-otel-config-${i}.yaml.draft`;
			i++;
		}
		const content = "# OpenTelemetry Collector Configuration\n\n";
		dispatch(addOtelLocalFile({ name, content }));
		dispatch(saveDraftOtelConfig({ name, content, skipValidation: true }));
		navigate(paths.configurationOtelFile(name), { replace: false });
	}, [dispatch, otelList, navigate]);

	const [createMenuAnchor, setCreateMenuAnchor] = React.useState(null);
	const configEditorRef = React.useRef(null);
	const otelEditorRef = React.useRef(null);

	const handleTest = React.useCallback(() => {
		if (routeRepo !== "config" || !routeName || !isVmFile(routeName)) return;
		const content = configFilesByName[routeName]?.content ?? "";
		dispatch(testVelocityTemplate({ name: routeName, content }));
	}, [dispatch, routeRepo, routeName, configFilesByName]);

	const handleCloseTestResult = React.useCallback(() => {
		dispatch(clearVelocityTestResult());
	}, [dispatch]);

	const handleReevaluateTemplate = React.useCallback(() => {
		if (routeRepo !== "config" || !routeName || !isVmFile(routeName)) return;
		// A draft is not loaded by the agent, so it has no configuration to refresh.
		if (isDraftFile(routeName)) return;
		dispatch(reevaluateTemplate({ name: routeName }));
	}, [dispatch, routeRepo, routeName]);

	const handleReevaluateConfiguration = React.useCallback(() => {
		setReloadConfirmOpen(false);
		dispatch(reevaluateConfiguration());
	}, [dispatch]);

	React.useEffect(() => {
		if (velocityTestResult && velocityTestResult.name !== configSelected) {
			dispatch(clearVelocityTestResult());
		}
	}, [configSelected, velocityTestResult, dispatch]);

	const handleMakeDraftConfig = React.useCallback(
		async (fileName) => {
			const newName = fileName + ".draft";
			if (configList.some((f) => f.name === fileName && f.localOnly)) {
				dispatch(renameLocalFile({ oldName: fileName, newName }));
			} else {
				await dispatch(renameConfig({ oldName: fileName, newName })).unwrap();
			}
			if (routeRepo === "config" && routeName === fileName) {
				navigate(paths.configurationFile(newName), { replace: true });
			}
		},
		[dispatch, configList, routeRepo, routeName, navigate],
	);

	const handleMakeDraftOtel = React.useCallback(
		async (fileName) => {
			const newName = fileName + ".draft";
			if (otelList.some((f) => f.name === fileName && f.localOnly)) {
				dispatch(renameOtelLocalFile({ oldName: fileName, newName }));
			} else {
				await dispatch(renameOtelConfig({ oldName: fileName, newName })).unwrap();
			}
			if (routeRepo === "otel" && routeName === fileName) {
				navigate(paths.configurationOtelFile(newName), { replace: true });
			}
		},
		[dispatch, otelList, routeRepo, routeName, navigate],
	);

	const displayName =
		routeRepo === "config" ? configSelected : routeRepo === "otel" ? otelSelected : null;
	const loadingContent =
		(routeRepo === "config" && configLoadingContent) ||
		(routeRepo === "otel" && otelLoadingContent);

	// Mirror the Explorer tree: don't render the tree until the lists have loaded, so
	// it appears fully-formed (already expanded) in one paint instead of visibly filling
	// in as the config/otel lists stream in. Only gates the initial load — once files
	// exist, a manual Refresh keeps the current tree on screen.
	const treeInitialLoading =
		(configLoadingList || otelLoadingList) &&
		(configList?.length ?? 0) === 0 &&
		(otelList?.length ?? 0) === 0;

	const treeContent = (
		<Stack spacing={1.5} sx={{ p: 1.5 }}>
			<Stack direction="row" spacing={1} alignItems="center">
				<Button
					size="small"
					variant="outlined"
					color="inherit"
					startIcon={<RefreshIcon />}
					onClick={() => {
						dispatch(fetchConfigList());
						dispatch(fetchOtelConfigList());
					}}
				>
					Refresh
				</Button>
				<Button
					size="small"
					variant="outlined"
					color="inherit"
					endIcon={<KeyboardArrowDownIcon />}
					onClick={(e) => setCreateMenuAnchor(e.currentTarget)}
					disabled={isReadOnly}
				>
					Create
				</Button>
				<Menu
					anchorEl={createMenuAnchor}
					open={Boolean(createMenuAnchor)}
					onClose={() => setCreateMenuAnchor(null)}
					anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
					transformOrigin={{ vertical: "top", horizontal: "left" }}
				>
					<MenuItem
						onClick={() => {
							setCreateMenuAnchor(null);
							handleCreateConfig("yaml");
						}}
					>
						New YAML config
					</MenuItem>
					<MenuItem
						onClick={() => {
							setCreateMenuAnchor(null);
							handleCreateConfig("vm");
						}}
					>
						New Velocity template
					</MenuItem>
					<MenuItem
						onClick={() => {
							setCreateMenuAnchor(null);
							handleCreateOtel();
						}}
					>
						New OTel config
					</MenuItem>
				</Menu>
				<Button
					size="small"
					variant="outlined"
					color="inherit"
					component="label"
					startIcon={<UploadFileIcon />}
					disabled={isReadOnly}
				>
					Import
					<input
						type="file"
						accept=".yaml,.yml,.vm"
						hidden
						disabled={isReadOnly}
						onChange={(e) => {
							const file = e.target.files?.[0];
							if (!file) return;
							const reader = new FileReader();
							reader.onload = (evt) => {
								const content = evt.target.result;
								dispatch(addLocalFile({ name: file.name, content }));
								navigate(paths.configurationFile(file.name), { replace: false });
							};
							reader.readAsText(file);
						}}
					/>
				</Button>
				<Tooltip title="Reevaluate Configuration">
					{/* Wrapped: a disabled button fires no events, so the tooltip needs a live element. */}
					<span>
						<Button
							size="small"
							variant="outlined"
							color="inherit"
							startIcon={<ReevaluateIcon />}
							onClick={() => setReloadConfirmOpen(true)}
							disabled={reevaluation?.scope === "configuration" && !!reevaluation?.loading}
						>
							{reevaluation?.scope === "configuration" && reevaluation?.loading
								? "Reloading..."
								: "Reload"}
						</Button>
					</span>
				</Tooltip>
				{(configLoadingList || otelLoadingList) && <CircularProgress size={18} />}
			</Stack>
			{treeInitialLoading ? (
				<Box sx={{ p: 1, display: "flex", alignItems: "center", gap: 1 }}>
					<CircularProgress size={18} />
					<Typography variant="body2">Loading configuration…</Typography>
				</Box>
			) : (
				<UnifiedConfigTree
					selectedRepo={routeRepo}
					selectedName={routeName}
					onSelect={onSelect}
					onRenameConfig={handleRenameConfig}
					onDeleteConfig={(name) => openDelete("config", name)}
					onMakeDraftConfig={handleMakeDraftConfig}
					onRenameOtel={handleRenameOtel}
					onDeleteOtel={(name) => openDelete("otel", name)}
					onMakeDraftOtel={handleMakeDraftOtel}
				/>
			)}
			<QuestionDialog
				open={deleteOpen}
				title="Delete file"
				question={`Are you sure you want to delete "${deleteTarget?.name ?? ""}"? This action cannot be undone.`}
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
				{displayName ?? "Configuration"}
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
					<Typography variant="h6">Configuration</Typography>
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
						sx={(t) => ({
							...scrollbarSx(t),
							px: 1.5,
							pt: 0,
							pb: 1.5,
							gap: 1,
							minHeight: 0,
							// The actual scrolling element is CodeMirror's internal .cm-scroller,
							// not this Stack — WebKit scrollbar pseudo-elements don't inherit.
							"& .cm-scroller": scrollbarThumbSx(t),
						})}
					>
						{routeRepo === "config" && (
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
									selected={configSelected}
									saving={configSaving}
									onSave={() => configEditorRef.current?.save?.()}
									onApply={() => configEditorRef.current?.apply?.()}
									onTest={handleTest}
									testLoading={!!velocityTestResult?.loading}
									onReevaluate={handleReevaluateTemplate}
									reevaluateLoading={reevaluation?.scope === "template" && !!reevaluation?.loading}
									isReadOnly={isReadOnly}
								/>
							</Box>
						)}
						{routeRepo === "otel" && (
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
								<OtelEditorHeader
									selected={otelSelected}
									saving={otelSaving}
									onSave={() => otelEditorRef.current?.save?.()}
									onApply={() => otelEditorRef.current?.apply?.()}
									isReadOnly={isReadOnly}
									logsOpen={otelLogsOpen}
									onToggleLogs={() => setOtelLogsOpen((prev) => !prev)}
									onOpenLogs={otelLogs.fetchLogs}
								/>
							</Box>
						)}
						<Box sx={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "row" }}>
							{routeRepo === "config" && (
								<>
									<Box sx={{ flex: 1, minWidth: 0 }}>
										<ConfigEditorContainer ref={configEditorRef} />
									</Box>
									{velocityTestResult && (
										<Box sx={{ flex: 1, minWidth: 0, borderLeft: 1, borderColor: "divider" }}>
											<VelocityTestResultPanel
												result={velocityTestResult.result}
												error={velocityTestResult.error}
												loading={velocityTestResult.loading}
												yamlValidation={velocityTestResult.yamlValidation}
												onClose={handleCloseTestResult}
											/>
										</Box>
									)}
								</>
							)}
							{routeRepo === "otel" && (
								<ResizableVerticalSplit
									initialTopPct={60}
									bottomVisible={otelLogsOpen}
									sx={{ flex: 1, minWidth: 0, minHeight: 0 }}
								>
									<SplitTop>
										<OtelConfigEditorContainer ref={otelEditorRef} />
									</SplitTop>
									<SplitBottom disableScroll>
										{otelLogsOpen && (
											<OtelCollectorLogPanel
												logs={otelLogs.logs}
												logsLoading={otelLogs.logsLoading}
												logsError={otelLogs.logsError}
												tailLines={otelLogs.tailLines}
												onTailLinesChange={otelLogs.setTailLines}
												onRefresh={otelLogs.fetchLogs}
											/>
										)}
									</SplitBottom>
								</ResizableVerticalSplit>
							)}
							{!routeRepo && (
								<Box
									sx={{
										flex: 1,
										display: "flex",
										alignItems: "center",
										justifyContent: "center",
										color: "text.secondary",
									}}
								>
									<Typography variant="body2">
										Select a file from the tree (config or otel).
									</Typography>
								</Box>
							)}
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
			{/* Rendered here and not in treeContent, which the drawer and the split view both render. */}
			<QuestionDialog
				open={reloadConfirmOpen}
				title="Reload the configuration"
				question="This re-runs every configuration source, including all Velocity templates, and applies the result to the running agent. Resources whose settings changed are restarted. Do you want to continue?"
				onClose={() => setReloadConfirmOpen(false)}
				actionButtons={[
					{ btnTitle: "Cancel", callback: () => setReloadConfirmOpen(false), autoFocus: true },
					{
						btnTitle: "Reload",
						btnColor: "primary",
						btnVariant: "contained",
						btnIcon: <ReevaluateIcon />,
						callback: handleReevaluateConfiguration,
					},
				]}
			/>
		</>
	);
}

export default ConfigurationPage;
