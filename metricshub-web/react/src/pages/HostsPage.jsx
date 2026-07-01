import * as React from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import {
	Box,
	Button,
	Dialog,
	DialogActions,
	DialogContent,
	DialogContentText,
	DialogTitle,
	TextField,
	Alert,
} from "@mui/material";
import { uiConfigApi } from "../api/ui-config";
import { useSnackbar } from "../hooks/use-snackbar";
import HostsBrowseView from "../components/hosts/HostsBrowseView";
import HostConfigEditLayout from "../components/hosts/HostConfigEditLayout";
import ResourceGroupFormPage from "../components/hosts/ResourceGroupFormPage";
import { clearHostWizardSessionForRoute } from "../components/hosts/host-wizard-session";
import {
	buildHostPayloadFromWizardAsync,
	getGroupResources,
} from "../components/hosts/host-config-utils";
import { groupNameForCreateHost, HOSTS_VIEWS } from "../components/hosts/hosts-navigation";
import {
	isGuidedConfigPath,
	pathForView,
	viewFromLocation,
	viewFromPathname,
} from "../components/hosts/hosts-route-sync";
import { paths } from "../paths";
import { formatResourceCount, NO_RESOURCE_GROUP } from "../components/hosts/hosts-labels";

const CREATE_RESOURCE_GROUP_QUERY_PARAM = "resource-group";
const LEGACY_CREATE_RESOURCE_GROUP_QUERY_PARAM = "resourceGroup";
const CREATE_RESOURCE_GROUP_NONE = "none";
const CREATE_RESOURCE_GROUP_STANDALONE = "no-resource-group";

const createResourceGroupParamFromSearch = (search) => {
	const params = new URLSearchParams(search);
	return (
		params.get(CREATE_RESOURCE_GROUP_QUERY_PARAM) ||
		params.get(LEGACY_CREATE_RESOURCE_GROUP_QUERY_PARAM) ||
		CREATE_RESOURCE_GROUP_NONE
	);
};

const resourceGroupParamForCreateView = (view) => {
	if (view?.type === "group" || view?.type === "groupedHost") {
		return view.groupName;
	}
	if (view?.type === "standalone" || view?.type === "standaloneHost") {
		return CREATE_RESOURCE_GROUP_STANDALONE;
	}
	return CREATE_RESOURCE_GROUP_NONE;
};

const inlineCreateStateFromParam = (resourceGroupParam) => {
	if (
		resourceGroupParam === CREATE_RESOURCE_GROUP_STANDALONE ||
		resourceGroupParam === "__none__"
	) {
		return {
			defaultResourceGroup: null,
			initialState: { targetType: "standalone", resourceGroup: "" },
		};
	}
	if (!resourceGroupParam || resourceGroupParam === CREATE_RESOURCE_GROUP_NONE) {
		return {
			defaultResourceGroup: null,
			initialState: { targetType: "", resourceGroup: "" },
		};
	}
	return {
		defaultResourceGroup: resourceGroupParam,
		initialState: { targetType: "group", resourceGroup: resourceGroupParam },
	};
};

const returnViewFromCreateParam = (resourceGroupParam) => {
	if (
		resourceGroupParam === CREATE_RESOURCE_GROUP_STANDALONE ||
		resourceGroupParam === "__none__"
	) {
		return HOSTS_VIEWS.standalone();
	}
	if (!resourceGroupParam || resourceGroupParam === CREATE_RESOURCE_GROUP_NONE) {
		return HOSTS_VIEWS.resourceGroups();
	}
	return HOSTS_VIEWS.group(resourceGroupParam);
};

const HostsPage = () => {
	const navigate = useNavigate();
	const location = useLocation();
	const { show: showSnackbar } = useSnackbar();
	const { groupName: routeGroupName, resourceId, hostId: routeHostId } = useParams();
	const [snapshot, setSnapshot] = React.useState({ resources: {}, resourceGroups: {} });
	const [view, setView] = React.useState(() =>
		typeof window !== "undefined" && isGuidedConfigPath(window.location.pathname)
			? viewFromPathname(window.location.pathname)
			: HOSTS_VIEWS.resourceGroups(),
	);
	const [loading, setLoading] = React.useState(true);
	const [error, setError] = React.useState(null);
	const [busy, setBusy] = React.useState(false);
	const [confirmDialog, setConfirmDialog] = React.useState(null);
	const [confirmInput, setConfirmInput] = React.useState("");
	const [browseCreateContext, setBrowseCreateContext] = React.useState(null);
	const [abandonDialog, setAbandonDialog] = React.useState(null);
	const unsavedFormRef = React.useRef(false);
	const isResourceGroupLegacyEditRoute =
		location.pathname.endsWith("/edit") && !location.pathname.includes("/resources/");
	const isHostEditRoute =
		location.pathname.endsWith("/edit") && location.pathname.includes("/resources/");
	const isStandaloneCreateRoute =
		location.pathname === "/configuration/guided-config/no-resource-group/resources/new";
	const isCreateRoute =
		location.pathname === "/configuration/guided-config/resources/new" || isStandaloneCreateRoute;
	const isResourceGroupCreateRoute = location.pathname === paths.hostsResourceGroupNew;

	const load = React.useCallback(async () => {
		setLoading(true);
		setError(null);
		try {
			const data = await uiConfigApi.getHosts();
			setSnapshot(data || { resources: {}, resourceGroups: {} });
		} catch (e) {
			setError(e?.message || "Failed to load resources");
		} finally {
			setLoading(false);
		}
	}, []);

	React.useEffect(() => {
		load();
	}, [load]);

	React.useEffect(() => {
		const navState = location.state;
		if (!navState?.refreshHosts) {
			return;
		}
		load();
		if (navState.hostsView) {
			setView(navState.hostsView);
			navigate(pathForView(navState.hostsView), { replace: true, state: {} });
			return;
		}
		navigate(`${location.pathname}${location.search}`, { replace: true, state: {} });
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [location.key]);

	const navigateBrowseView = React.useCallback(
		(nextView) => {
			setBrowseCreateContext(null);
			setView(nextView);
			navigate(pathForView(nextView));
		},
		[navigate],
	);

	const syncViewFromUrl = React.useCallback(() => {
		if (isCreateRoute) {
			const resourceGroupParam = isStandaloneCreateRoute
				? CREATE_RESOURCE_GROUP_STANDALONE
				: createResourceGroupParamFromSearch(location.search);
			setView(location.state?.returnToHosts?.view || returnViewFromCreateParam(resourceGroupParam));
			return;
		}
		if (isResourceGroupCreateRoute) {
			setView(location.state?.returnView ?? HOSTS_VIEWS.resourceGroups());
			return;
		}
		const urlView = viewFromLocation(location.pathname, {
			groupName: routeGroupName,
			resourceId,
			hostId: routeHostId,
		});
		setView(urlView);
	}, [
		isCreateRoute,
		isResourceGroupCreateRoute,
		isStandaloneCreateRoute,
		location.pathname,
		location.search,
		location.state,
		routeGroupName,
		resourceId,
		routeHostId,
	]);

	React.useEffect(() => {
		if (!isHostEditRoute || loading) {
			return;
		}
		const urlView = viewFromLocation(location.pathname, {
			groupName: routeGroupName,
			resourceId,
			hostId: routeHostId,
		});
		if (urlView.type === "groupedHost" || urlView.type === "standaloneHost") {
			navigate(pathForView(urlView), { replace: true, state: { hostsView: urlView } });
			setView(urlView);
		}
	}, [
		isHostEditRoute,
		loading,
		location.pathname,
		routeGroupName,
		resourceId,
		routeHostId,
		navigate,
	]);

	React.useEffect(() => {
		if (loading || !isResourceGroupLegacyEditRoute || !routeGroupName) {
			return;
		}
		const groupName = decodeURIComponent(routeGroupName);
		const nextView = HOSTS_VIEWS.group(groupName);
		navigate(pathForView(nextView), { replace: true, state: { hostsView: nextView } });
		setView(nextView);
	}, [isResourceGroupLegacyEditRoute, loading, navigate, routeGroupName]);

	React.useEffect(() => {
		if (loading || isCreateRoute) {
			return;
		}
		const urlView = viewFromLocation(location.pathname, {
			groupName: routeGroupName,
			resourceId,
			hostId: routeHostId,
		});
		if (
			urlView.type === "group" &&
			urlView.groupName &&
			!snapshot.resourceGroups?.[urlView.groupName]
		) {
			navigate(paths.hostsResourceGroups(), { replace: true });
			setView(HOSTS_VIEWS.resourceGroups());
			return;
		}
		if (urlView.type === "groupedHost") {
			const hostConfig = getGroupResources(snapshot.resourceGroups?.[urlView.groupName])?.[
				urlView.hostId
			];
			if (!hostConfig) {
				navigate(pathForView(HOSTS_VIEWS.group(urlView.groupName)), { replace: true });
				setView(HOSTS_VIEWS.group(urlView.groupName));
			}
		}
		if (urlView.type === "standaloneHost") {
			if (!snapshot.resources?.[urlView.hostId]) {
				navigate(pathForView(HOSTS_VIEWS.standalone()), { replace: true });
				setView(HOSTS_VIEWS.standalone());
			}
		}
	}, [
		loading,
		isCreateRoute,
		location.pathname,
		routeGroupName,
		resourceId,
		routeHostId,
		snapshot.resourceGroups,
		snapshot.resources,
		navigate,
	]);

	const createRouteContext = React.useMemo(() => {
		if (!isCreateRoute) {
			return null;
		}
		const resourceGroupParam = isStandaloneCreateRoute
			? CREATE_RESOURCE_GROUP_STANDALONE
			: createResourceGroupParamFromSearch(location.search);
		const state = inlineCreateStateFromParam(resourceGroupParam);
		const returnToHosts = location.state?.returnToHosts || {};
		const fallbackReturnView = returnViewFromCreateParam(resourceGroupParam);
		const returnView = returnToHosts.view || fallbackReturnView;
		return {
			...state,
			returnView,
		};
	}, [isCreateRoute, isStandaloneCreateRoute, location.search, location.state]);

	const activeInlineCreateContext = createRouteContext || browseCreateContext;

	React.useEffect(() => {
		if (!isCreateRoute && browseCreateContext) {
			setBrowseCreateContext(null);
		}
	}, [browseCreateContext, isCreateRoute]);

	React.useEffect(() => {
		syncViewFromUrl();
	}, [syncViewFromUrl]);

	const handleFormUnsavedChange = React.useCallback((dirty) => {
		unsavedFormRef.current = dirty;
	}, []);

	const guardNavigation = React.useCallback((action) => {
		if (unsavedFormRef.current) {
			setAbandonDialog({
				onConfirm: () => {
					unsavedFormRef.current = false;
					action();
				},
			});
			return;
		}
		action();
	}, []);

	const openInlineCreateHost = React.useCallback(
		(resourceGroup) => {
			guardNavigation(() => {
				const resourceGroupParam =
					resourceGroup === "__none__"
						? CREATE_RESOURCE_GROUP_STANDALONE
						: (resourceGroup ?? resourceGroupParamForCreateView(view));
				const createState = inlineCreateStateFromParam(resourceGroupParam);
				const createPath = paths.hostsResourceNew(resourceGroupParam);
				clearHostWizardSessionForRoute({
					mode: "create",
					defaultResourceGroup: createState.defaultResourceGroup,
					pathname: createPath,
				});
				const returnToHosts = {
					pathname: location.pathname,
					search: location.search,
					view,
				};
				setBrowseCreateContext({
					...createState,
					returnView: view,
				});
				navigate(createPath, {
					state: { returnToHosts },
				});
			});
		},
		[guardNavigation, navigate, location.pathname, location.search, view],
	);

	const cancelInlineCreateHost = React.useCallback(() => {
		setBrowseCreateContext(null);
		const returnView = activeInlineCreateContext?.returnView || HOSTS_VIEWS.resourceGroups();
		navigateBrowseView(returnView);
	}, [activeInlineCreateContext?.returnView, navigateBrowseView]);

	const submitInlineCreateHost = React.useCallback(
		async (wizardState) => {
			setBusy(true);
			setError(null);
			try {
				const payload = await buildHostPayloadFromWizardAsync(wizardState, {
					encryptPasswords: true,
				});
				const updated = await uiConfigApi.addHost(payload);
				setSnapshot(updated);

				const savedHostId = wizardState.hostId;
				const savedInGroup = wizardState.targetType === "group";
				const savedGroupName = wizardState.resourceGroup;
				const nextView = savedInGroup
					? HOSTS_VIEWS.groupedHost(savedGroupName, savedHostId)
					: HOSTS_VIEWS.standaloneHost(savedHostId);

				setBrowseCreateContext(null);
				setView(nextView);
				navigate(pathForView(nextView), { replace: true, state: { hostsView: nextView } });

				const placementLabel = savedInGroup ? savedGroupName : NO_RESOURCE_GROUP;
				showSnackbar(`Resource has been added successfully to ${placementLabel}.`, {
					severity: "success",
				});
			} catch (e) {
				setError(e?.message || "Failed to save resource");
				throw e;
			} finally {
				setBusy(false);
			}
		},
		[navigate, showSnackbar],
	);

	const resourceGroupNames = React.useMemo(
		() => Object.keys(snapshot.resourceGroups || {}),
		[snapshot.resourceGroups],
	);

	const handleCreateGroup = React.useCallback(() => {
		guardNavigation(() => {
			navigate(paths.hostsResourceGroupNew, { state: { returnView: view } });
		});
	}, [guardNavigation, navigate, view]);

	const handleCreateGroupSubmit = React.useCallback(async (payload) => {
		setBusy(true);
		setError(null);
		try {
			const updated = await uiConfigApi.createResourceGroup(payload);
			setSnapshot(updated);
		} catch (e) {
			setError(e?.message || "Failed to create resource group");
			throw e;
		} finally {
			setBusy(false);
		}
	}, []);

	const handleCancelGroupCreate = React.useCallback(() => {
		// Honour the view the user came from (passed via location state in
		// handleCreateGroup). Falls back to Resource Groups when the page was
		// reached directly by URL.
		const returnView = location.state?.returnView ?? HOSTS_VIEWS.resourceGroups();
		navigateBrowseView(returnView);
	}, [location.state, navigateBrowseView]);

	const handleConfigureGroup = React.useCallback(
		(groupName) => {
			navigateBrowseView(HOSTS_VIEWS.group(groupName));
		},
		[navigateBrowseView],
	);

	const handleUpdateResourceGroup = React.useCallback(
		async (groupName, payload) => {
			setBusy(true);
			setError(null);
			try {
				const updated = await uiConfigApi.updateResourceGroup(groupName, payload);
				setSnapshot(updated);
				if (payload.name !== groupName) {
					navigateBrowseView(HOSTS_VIEWS.group(payload.name));
				}
			} catch (e) {
				setError(e?.message || "Failed to save resource group");
				throw e;
			} finally {
				setBusy(false);
			}
		},
		[navigateBrowseView],
	);

	const handleCloseResourceGroupForm = React.useCallback(() => {
		navigateBrowseView(HOSTS_VIEWS.resourceGroups());
	}, [navigateBrowseView]);

	const handleDeleteGroupedHost = React.useCallback(
		(groupName, hostId) => {
			setConfirmDialog({
				title: "Delete resource",
				message: `Remove resource "${hostId}" from group "${groupName}"?`,
				onConfirm: async () => {
					setBusy(true);
					setError(null);
					try {
						const updated = await uiConfigApi.deleteGroupedHost(groupName, hostId);
						setSnapshot(updated);
						if (updated?.resourceGroups?.[groupName]) {
							navigateBrowseView(HOSTS_VIEWS.group(groupName));
						} else {
							navigateBrowseView(HOSTS_VIEWS.resourceGroups());
						}
					} catch (e) {
						setError(e?.message || "Failed to delete resource");
					} finally {
						setBusy(false);
						setConfirmDialog(null);
					}
				},
			});
		},
		[navigateBrowseView],
	);

	const handleDeleteGroupedHosts = React.useCallback(
		(groupName, hostIds) => {
			const ids = Array.from(new Set((hostIds || []).map(String).filter(Boolean)));
			if (ids.length === 0) {
				return;
			}
			setConfirmDialog({
				title: ids.length === 1 ? "Delete resource" : "Delete resources",
				message:
					ids.length === 1
						? `Remove resource "${ids[0]}" from group "${groupName}"?`
						: `Remove ${ids.length} resources from group "${groupName}"? This cannot be undone.`,
				onConfirm: async () => {
					setBusy(true);
					setError(null);
					try {
						let updated = snapshot;
						for (const hostId of ids) {
							updated = await uiConfigApi.deleteGroupedHost(groupName, hostId);
						}
						setSnapshot(updated);
						if (updated?.resourceGroups?.[groupName]) {
							navigateBrowseView(HOSTS_VIEWS.group(groupName));
						} else {
							navigateBrowseView(HOSTS_VIEWS.resourceGroups());
						}
					} catch (e) {
						setError(e?.message || "Failed to delete resources");
					} finally {
						setBusy(false);
						setConfirmDialog(null);
					}
				},
			});
		},
		[navigateBrowseView, snapshot],
	);

	const handleDeleteStandaloneHost = React.useCallback(
		(hostId) => {
			setConfirmDialog({
				title: "Delete resource",
				message: `Remove resource "${hostId}" from ${NO_RESOURCE_GROUP}?`,
				onConfirm: async () => {
					setBusy(true);
					setError(null);
					try {
						const updated = await uiConfigApi.deleteStandaloneHost(hostId);
						setSnapshot(updated);
						const remaining = Object.keys(updated?.resources || {}).length;
						navigateBrowseView(
							remaining > 0 ? HOSTS_VIEWS.standalone() : HOSTS_VIEWS.resourceGroups(),
						);
					} catch (e) {
						setError(e?.message || "Failed to delete resource");
					} finally {
						setBusy(false);
						setConfirmDialog(null);
					}
				},
			});
		},
		[navigateBrowseView],
	);

	const handleDeleteStandaloneHosts = React.useCallback(
		(hostIds) => {
			const ids = Array.from(new Set((hostIds || []).map(String).filter(Boolean)));
			if (ids.length === 0) {
				return;
			}
			setConfirmDialog({
				title: ids.length === 1 ? "Delete resource" : "Delete resources",
				message:
					ids.length === 1
						? `Remove resource "${ids[0]}" from ${NO_RESOURCE_GROUP}?`
						: `Remove ${ids.length} resources from ${NO_RESOURCE_GROUP}? This cannot be undone.`,
				onConfirm: async () => {
					setBusy(true);
					setError(null);
					try {
						let updated = snapshot;
						for (const hostId of ids) {
							updated = await uiConfigApi.deleteStandaloneHost(hostId);
						}
						setSnapshot(updated);
						const remaining = Object.keys(updated?.resources || {}).length;
						navigateBrowseView(
							remaining > 0 ? HOSTS_VIEWS.standalone() : HOSTS_VIEWS.resourceGroups(),
						);
					} catch (e) {
						setError(e?.message || "Failed to delete resources");
					} finally {
						setBusy(false);
						setConfirmDialog(null);
					}
				},
			});
		},
		[navigateBrowseView, snapshot],
	);

	const handleDeleteGroup = React.useCallback(
		(groupName, hostCount = 0) => {
			const hasHosts = hostCount > 0;
			setConfirmInput("");
			setConfirmDialog({
				title: "Delete resource group",
				message: hasHosts
					? `Delete resource group "${groupName}" and its ${formatResourceCount(hostCount)}? This cannot be undone. Type delete below to confirm.`
					: `Delete resource group "${groupName}"? This cannot be undone.`,
				requireConfirmText: hasHosts ? "delete" : undefined,
				onConfirm: async () => {
					setBusy(true);
					setError(null);
					try {
						const updated = await uiConfigApi.deleteResourceGroup(groupName);
						setSnapshot(updated);
						navigateBrowseView(HOSTS_VIEWS.resourceGroups());
					} catch (e) {
						setError(e?.message || "Failed to delete resource group");
					} finally {
						setBusy(false);
						setConfirmDialog(null);
						setConfirmInput("");
					}
				},
			});
		},
		[navigateBrowseView],
	);

	let editRightPane = null;
	if (isResourceGroupCreateRoute) {
		// Use the view the user navigated from as the breadcrumb context.
		// e.g. coming from the "Elyes" group page → breadcrumb shows "Resource Groups > Elyes".
		const returnView = location.state?.returnView;
		const groupCreateBreadcrumbPathname = returnView
			? pathForView(returnView)
			: paths.hostsResourceGroups();
		editRightPane = (
			<HostConfigEditLayout breadcrumbPathname={groupCreateBreadcrumbPathname}>
				<ResourceGroupFormPage
					key="resource-group-create"
					mode="create"
					existingGroupNames={resourceGroupNames}
					busy={busy}
					onSave={handleCreateGroupSubmit}
					onCancel={handleCancelGroupCreate}
					onUnsavedChangesChange={handleFormUnsavedChange}
				/>
			</HostConfigEditLayout>
		);
	}

	return (
		<>
			<Box
				sx={{
					p: 0,
					display: "flex",
					flexDirection: "column",
					height: "calc(100vh - 76px)",
					minHeight: 0,
					overflow: "hidden",
					maxWidth: "none",
					mx: 0,
					width: "100%",
				}}
			>
				{error && (
					<Alert
						severity="error"
						sx={{ flexShrink: 0, m: 2, mb: 0 }}
						onClose={() => setError(null)}
					>
						{error}
					</Alert>
				)}
				<Box sx={{ flex: 1, minHeight: 0, overflow: "hidden" }}>
					<HostsBrowseView
						snapshot={snapshot}
						loading={loading}
						busy={busy}
						view={view}
						pathname={`${location.pathname}${location.search}`}
						onViewChange={navigateBrowseView}
						onCreateGroup={handleCreateGroup}
						onCreateHost={() => openInlineCreateHost(groupNameForCreateHost(view))}
						onAddHostToGroup={(groupName) => openInlineCreateHost(groupName)}
						inlineCreateContext={activeInlineCreateContext}
						onInlineCreateCancel={cancelInlineCreateHost}
						onInlineCreateSubmit={submitInlineCreateHost}
						onDeleteGroup={handleDeleteGroup}
						onDeleteGroupedHost={handleDeleteGroupedHost}
						onDeleteGroupedHosts={handleDeleteGroupedHosts}
						onDeleteStandaloneHost={handleDeleteStandaloneHost}
						onDeleteStandaloneHosts={handleDeleteStandaloneHosts}
						onConfigureGroup={handleConfigureGroup}
						onUpdateResourceGroup={handleUpdateResourceGroup}
						onCloseResourceGroupForm={handleCloseResourceGroupForm}
						onFormUnsavedChange={handleFormUnsavedChange}
						onSnapshotChange={setSnapshot}
						onNavigate={navigate}
						onBusyChange={setBusy}
						disableNewGroup={isResourceGroupCreateRoute}
						disableNewResource={isCreateRoute}
						editRightPane={editRightPane}
					/>
				</Box>
			</Box>

			<Dialog
				open={Boolean(confirmDialog)}
				onClose={() => {
					if (!busy) {
						setConfirmDialog(null);
						setConfirmInput("");
					}
				}}
			>
				<DialogTitle>{confirmDialog?.title}</DialogTitle>
				<DialogContent>
					<DialogContentText sx={{ mb: confirmDialog?.requireConfirmText ? 2 : 0 }}>
						{confirmDialog?.message}
					</DialogContentText>
					{confirmDialog?.requireConfirmText && (
						<TextField
							autoFocus
							fullWidth
							size="small"
							label={`Type ${confirmDialog.requireConfirmText} to confirm`}
							value={confirmInput}
							onChange={(e) => setConfirmInput(e.target.value)}
							disabled={busy}
						/>
					)}
				</DialogContent>
				<DialogActions>
					<Button
						onClick={() => {
							setConfirmDialog(null);
							setConfirmInput("");
						}}
						disabled={busy}
					>
						Cancel
					</Button>
					<Button
						color="error"
						variant="contained"
						disabled={
							busy ||
							(confirmDialog?.requireConfirmText &&
								confirmInput.trim() !== confirmDialog.requireConfirmText)
						}
						onClick={confirmDialog?.onConfirm}
					>
						Delete
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog
				open={Boolean(abandonDialog)}
				onClose={() => setAbandonDialog(null)}
				fullWidth
				maxWidth="xs"
			>
				<DialogTitle>Discard unsaved changes?</DialogTitle>
				<DialogContent>
					<DialogContentText>
						You have unsaved changes on this page. You can keep editing or leave without saving.
					</DialogContentText>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setAbandonDialog(null)} autoFocus>
						Keep editing
					</Button>
					<Button
						color="error"
						variant="contained"
						onClick={() => {
							const onConfirm = abandonDialog?.onConfirm;
							setAbandonDialog(null);
							onConfirm?.();
						}}
					>
						Leave without saving
					</Button>
				</DialogActions>
			</Dialog>
		</>
	);
};

export default HostsPage;
