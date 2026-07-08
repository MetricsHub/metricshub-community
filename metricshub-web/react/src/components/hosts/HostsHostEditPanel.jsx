import * as React from "react";
import { Alert } from "@mui/material";
import { uiConfigApi } from "../../api/ui-config";
import { useSnackbar } from "../../hooks/use-snackbar";
import {
	buildHostPayloadFromFormAsync,
	getGroupResources,
	hostConfigToFormState,
	isMultiHostConfig,
} from "./host-config-utils";
import HostConfigPage from "./HostConfigPage";
import ShowInExplorerButton from "./ShowInExplorerButton";
import { HOSTS_VIEWS } from "./hosts-navigation";
import { pathForView } from "./hosts-route-sync";
import { compareLocale } from "../../utils/alphabetic-sort";
import { useHostsProtocolHealth } from "../../hooks/use-hosts-protocol-health";

/**
 * Resource configuration form (edit) inside the guided-config browse split view.
 *
 * @param {object} props
 * @param {{ resources?: Record<string, unknown>; resourceGroups?: Record<string, unknown> }} props.snapshot
 * @param {object} props.view
 * @param {boolean} props.busy
 * @param {(busy: boolean) => void} props.onBusyChange
 * @param {(snapshot: object) => void} props.onSnapshotChange
 * @param {(path: string, options?: object) => void} props.onNavigate
 * @param {(groupName: string, hostId: string) => void} props.onDeleteGroupedHost
 * @param {(hostId: string) => void} props.onDeleteStandaloneHost
 */
const HostsHostEditPanel = ({
	snapshot,
	view,
	busy,
	onBusyChange,
	onSnapshotChange,
	onNavigate,
	onDeleteGroupedHost,
	onDeleteStandaloneHost,
}) => {
	const { show: showSnackbar } = useSnackbar();
	const [error, setError] = React.useState(null);

	const isStandalone = view?.type === "standaloneHost";
	const groupName = view?.type === "groupedHost" ? view.groupName : null;
	const hostId = view?.type === "groupedHost" || view?.type === "standaloneHost" ? view.hostId : "";
	const hostConfig = isStandalone
		? snapshot.resources?.[hostId]
		: groupName
			? snapshot.resourceGroups?.[groupName]?.resources?.[hostId]
			: null;

	const isMultiHost = hostConfig ? isMultiHostConfig(hostConfig) : false;

	const resourceGroups = React.useMemo(
		() => Object.keys(snapshot.resourceGroups || {}).sort(compareLocale),
		[snapshot.resourceGroups],
	);

	// Per placement scope (group / standalone), excluding the resource being edited.
	const existingHostIdScopes = React.useMemo(
		() => ({
			groups: Object.fromEntries(
				Object.entries(snapshot?.resourceGroups || {}).map(([name, groupNode]) => [
					name,
					Object.keys(getGroupResources(groupNode)).filter((id) => id !== hostId),
				]),
			),
			standalone: Object.keys(snapshot?.resources || {}).filter((id) => id !== hostId),
		}),
		[snapshot?.resourceGroups, snapshot?.resources, hostId],
	);

	const initialState = React.useMemo(
		() =>
			hostConfig
				? hostConfigToFormState(hostId, hostConfig, isStandalone ? null : groupName)
				: null,
		[groupName, hostConfig, hostId, isStandalone],
	);

	const { healthByHostId } = useHostsProtocolHealth(
		[{ hostId, resourceGroup: isStandalone ? null : groupName }],
		Boolean(hostConfig) && !isMultiHost,
	);
	const protocolHealth = !isMultiHost ? healthByHostId[hostId] : undefined;

	const navigateToView = React.useCallback(
		(nextView, options = {}) => {
			onNavigate(pathForView(nextView), options);
		},
		[onNavigate],
	);

	const handleCancel = React.useCallback(() => {
		if (isStandalone) {
			navigateToView(HOSTS_VIEWS.standalone());
			return;
		}
		navigateToView(HOSTS_VIEWS.group(groupName));
	}, [groupName, isStandalone, navigateToView]);

	const handleDelete = React.useCallback(() => {
		if (isStandalone) {
			onDeleteStandaloneHost(hostId);
			return;
		}
		onDeleteGroupedHost(groupName, hostId);
	}, [groupName, hostId, isStandalone, onDeleteGroupedHost, onDeleteStandaloneHost]);

	const handleSubmit = React.useCallback(
		async (formState) => {
			onBusyChange(true);
			setError(null);
			try {
				const payload = await buildHostPayloadFromFormAsync(formState, {
					encryptPasswords: true,
				});

				const previousHostId = formState.originalHostId || hostId;
				if (formState.originalTargetType === "group") {
					await uiConfigApi.deleteGroupedHost(
						formState.originalResourceGroup || groupName,
						previousHostId,
					);
				} else {
					await uiConfigApi.deleteStandaloneHost(previousHostId);
				}

				const updated = await uiConfigApi.addHost(payload);
				onSnapshotChange(updated);

				const savedHostId = formState?.hostId || hostId;
				const savedInGroup = formState?.targetType === "group";
				const savedGroupName = formState?.resourceGroup || groupName;
				const identityChanged =
					savedHostId !== hostId ||
					(!isStandalone && savedInGroup && savedGroupName !== groupName) ||
					(isStandalone && savedInGroup);

				if (identityChanged) {
					const nextView = savedInGroup
						? HOSTS_VIEWS.groupedHost(savedGroupName, savedHostId)
						: HOSTS_VIEWS.standaloneHost(savedHostId);
					navigateToView(nextView, { replace: true, state: { hostsView: nextView } });
				}

				showSnackbar("Resource configuration saved.", { severity: "success" });
			} catch (e) {
				setError(e?.message || "Failed to save resource");
				throw e;
			} finally {
				onBusyChange(false);
			}
		},
		[groupName, hostId, isStandalone, navigateToView, onBusyChange, onSnapshotChange, showSnackbar],
	);

	if (!hostConfig || !initialState) {
		return <Alert severity="warning">Resource &quot;{hostId}&quot; was not found.</Alert>;
	}

	return (
		<>
			{error && (
				<Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
					{error}
				</Alert>
			)}
			<HostConfigPage
				mode="edit"
				hostId={hostId}
				defaultResourceGroup={isStandalone ? null : groupName}
				resourceGroups={resourceGroups}
				existingHostIdScopes={existingHostIdScopes}
				initialState={initialState}
				protocolHealth={protocolHealth}
				busy={busy}
				onCancel={handleCancel}
				onSubmit={handleSubmit}
				onDelete={handleDelete}
				headerEndAction={
					<ShowInExplorerButton
						resourceGroup={isStandalone ? null : groupName}
						hostId={hostId}
						hostConfig={hostConfig}
						disabled={busy}
					/>
				}
			/>
		</>
	);
};

export default HostsHostEditPanel;
