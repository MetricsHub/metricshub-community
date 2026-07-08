import * as React from "react";

import { Box, Skeleton, Stack, Typography } from "@mui/material";

import { SplitScreen, Left, Right } from "../split-screen/SplitScreen";
import HostConfigEditLayout from "./HostConfigEditLayout";

import GuidedConfigSearch from "./GuidedConfigSearch";
import GuidedConfigTreePanel from "./GuidedConfigTreePanel";
import HostsGuidedConfigTopBar from "./HostsGuidedConfigTopBar";

import ResourceGroupsOverviewView from "./ResourceGroupsOverviewView";

import HostConfigPage from "./HostConfigPage";
import HostsHostEditPanel from "./HostsHostEditPanel";
import ResourceGroupFormPage from "./ResourceGroupFormPage";

import StandaloneHostsView from "./StandaloneHostsView";

import { getGroupResources } from "./host-config-utils";
import { HOSTS_VIEWS } from "./hosts-navigation";
import { pathForView } from "./hosts-route-sync";

import { NO_RESOURCE_GROUP } from "./hosts-labels";

/**

 * Tree + detail panel layout for the Hosts page (URL updates on selection, like Explorer).

 *

 * @param {object} props

 * @param {{ resources?: Record<string, unknown>; resourceGroups?: Record<string, unknown> }} props.snapshot

 * @param {boolean} props.loading

 * @param {boolean} props.busy

 * @param {object} props.view current HOSTS_VIEWS selection

 * @param {(view: object) => void} props.onViewChange navigates and updates view

 * @param {() => void} props.onCreateGroup

 * @param {() => void} props.onCreateHost

 * @param {(groupName: string) => void} props.onAddHostToGroup

 * @param {(groupName: string, hostCount: number) => void} props.onDeleteGroup

 * @param {(groupName: string, hostId: string) => void} props.onDeleteGroupedHost
 *
 * @param {(groupName: string, hostIds: string[]) => void} [props.onDeleteGroupedHosts]

 * @param {(hostId: string) => void} props.onDeleteStandaloneHost
 *
 * @param {(hostId: string) => void} props.onDeleteStandaloneHosts
 * @param {(groupName: string) => void} [props.onConfigureGroup]
 * @param {(groupName: string, payload: { name: string; attributes: Record<string, string>; metrics: Record<string, number> }) => Promise<void>} [props.onUpdateResourceGroup]
 * @param {() => void} [props.onCloseResourceGroupForm]
 * @param {(dirty: boolean) => void} [props.onFormUnsavedChange]
 * @param {React.ReactNode} [props.editRightPane]
 * @param {(snapshot: object) => void} [props.onSnapshotChange]
 * @param {(path: string, options?: object) => void} [props.onNavigate]
 * @param {(busy: boolean) => void} [props.onBusyChange]
 * @param {boolean} [props.disableNewGroup]
 * @param {boolean} [props.disableNewResource]
 */

const HostsBrowseView = ({
	snapshot,

	loading,

	busy,

	view,

	onViewChange,

	onCreateGroup,

	onCreateHost,

	onAddHostToGroup,

	onDeleteGroup,

	onDeleteGroupedHost,

	onDeleteGroupedHosts,

	onDeleteStandaloneHost,

	onDeleteStandaloneHosts,

	onConfigureGroup,
	onUpdateResourceGroup,
	onCloseResourceGroupForm,
	onFormUnsavedChange,
	pathname = "",
	inlineCreateContext = null,
	onInlineCreateCancel,
	onInlineCreateSubmit,
	editRightPane = null,
	onSnapshotChange,
	onNavigate,
	onBusyChange,
	disableNewGroup = false,
	disableNewResource = false,
	drafts = [],
	onOpenDraft,
}) => {
	const resourceGroups = React.useMemo(
		() => Object.keys(snapshot?.resourceGroups || {}).sort((a, b) => a.localeCompare(b)),
		[snapshot?.resourceGroups],
	);
	// Resource IDs only need to be unique within their placement scope (one
	// resource group, or the standalone section) — not across the whole config.
	const existingHostIdScopes = React.useMemo(
		() => ({
			groups: Object.fromEntries(
				Object.entries(snapshot?.resourceGroups || {}).map(([groupName, groupNode]) => [
					groupName,
					Object.keys(getGroupResources(groupNode)),
				]),
			),
			standalone: Object.keys(snapshot?.resources || {}),
		}),
		[snapshot?.resourceGroups, snapshot?.resources],
	);
	const isInlineCreateOpen = Boolean(inlineCreateContext);
	const isHostFormView = view?.type === "groupedHost" || view?.type === "standaloneHost";
	const isResourceGroupFormView = view?.type === "group";
	const hasEditRightPane = Boolean(editRightPane);
	const isResourceFormOpen = isInlineCreateOpen || isHostFormView || isResourceGroupFormView;
	const isResourceGroupsView = view?.type === "resourceGroups";
	const showGuidedConfigTopBar = !hasEditRightPane && !isResourceFormOpen;

	const createBreadcrumbPathname = React.useMemo(() => {
		const contextView = inlineCreateContext?.returnView ?? view;
		return pathForView(contextView);
	}, [inlineCreateContext?.returnView, view]);

	const renderCreateHostForm = () => (
		<HostConfigEditLayout breadcrumbPathname={createBreadcrumbPathname}>
			<HostConfigPage
				key={`create-host-${pathname}-${inlineCreateContext?.draftId ?? ""}`}
				defaultResourceGroup={inlineCreateContext?.defaultResourceGroup ?? null}
				resourceGroups={resourceGroups}
				existingHostIdScopes={existingHostIdScopes}
				initialState={inlineCreateContext?.initialState}
				draftId={inlineCreateContext?.draftId ?? null}
				busy={busy}
				sessionPathname={pathname}
				onCancel={onInlineCreateCancel}
				onSubmit={onInlineCreateSubmit}
			/>
		</HostConfigEditLayout>
	);

	const renderHostEditForm = () => (
		<HostConfigEditLayout breadcrumbPathname={pathForView(view)}>
			<HostsHostEditPanel
				snapshot={snapshot}
				view={view}
				busy={busy}
				onBusyChange={onBusyChange}
				onSnapshotChange={onSnapshotChange}
				onNavigate={onNavigate}
				onDeleteGroupedHost={onDeleteGroupedHost}
				onDeleteStandaloneHost={onDeleteStandaloneHost}
			/>
		</HostConfigEditLayout>
	);

	const renderResourceGroupForm = () => {
		const groupName = view.groupName;
		const groupNode = snapshot.resourceGroups?.[groupName];
		return (
			<HostConfigEditLayout breadcrumbPathname={pathForView(view)}>
				<ResourceGroupFormPage
					mode="edit"
					groupName={groupName}
					groupNode={groupNode}
					existingGroupNames={resourceGroups}
					busy={busy}
					onUpdate={(payload) => onUpdateResourceGroup?.(groupName, payload)}
					onCancel={onCloseResourceGroupForm}
					onDeleteGroup={() =>
						onDeleteGroup?.(groupName, Object.keys(getGroupResources(groupNode || {})).length)
					}
					onOpenResource={(hostId) => onViewChange(HOSTS_VIEWS.groupedHost(groupName, hostId))}
					onDeleteResources={(hostIds) => onDeleteGroupedHosts?.(groupName, hostIds)}
					onUnsavedChangesChange={onFormUnsavedChange}
				/>
			</HostConfigEditLayout>
		);
	};

	const renderDetail = () => {
		if (loading) {
			return <Skeleton variant="rounded" height={320} />;
		}

		switch (view.type) {
			case "resourceGroups":
				return (
					<ResourceGroupsOverviewView
						snapshot={snapshot}
						busy={busy}
						onOpenGroup={(groupName) => onViewChange(HOSTS_VIEWS.group(groupName))}
						onOpenHost={(groupName, hostId) =>
							onViewChange(HOSTS_VIEWS.groupedHost(groupName, hostId))
						}
						onConfigureGroup={onConfigureGroup}
						onAddHostToGroup={onAddHostToGroup}
						onDeleteGroup={onDeleteGroup}
					/>
				);

			case "group":
			case "groupedHost":
			case "standaloneHost":
				return null;

			case "standalone":
				return (
					<StandaloneHostsView
						embedded
						globalSearch=""
						snapshot={snapshot}
						busy={busy}
						onOpenHost={(hostId) => onViewChange(HOSTS_VIEWS.standaloneHost(hostId))}
						onAddHost={onCreateHost}
						onDeleteHosts={onDeleteStandaloneHosts}
					/>
				);

			default:
				return (
					<Box
						sx={{
							p: 4,

							border: 1,

							borderColor: "divider",

							borderRadius: 1,

							textAlign: "center",

							minHeight: 240,

							display: "flex",

							alignItems: "center",

							justifyContent: "center",
						}}
					>
						<Box>
							<Typography variant="subtitle1" fontWeight={600} gutterBottom>
								Select a resource group or resource
							</Typography>

							<Typography variant="body2" color="text.secondary">
								Use the navigation tree on the left to view group details, resource configuration,
								or {NO_RESOURCE_GROUP}.
							</Typography>
						</Box>
					</Box>
				);
		}
	};

	return (
		<Stack spacing={0} sx={{ height: "100%", minHeight: 0 }}>
			<SplitScreen
				sx={{ height: "100%" }}
				initialLeftPct={28}
				smallScreenHeader={<GuidedConfigSearch snapshot={snapshot} onViewChange={onViewChange} />}
			>
				<Left>
					<GuidedConfigTreePanel
						snapshot={snapshot}
						view={view}
						busy={busy}
						disableNewGroup={disableNewGroup}
						disableNewResource={disableNewResource}
						drafts={drafts}
						onOpenDraft={onOpenDraft}
						onViewChange={onViewChange}
						onCreateGroup={onCreateGroup}
						onCreateHost={onCreateHost}
					/>
				</Left>
				<Right disableScroll={hasEditRightPane || isResourceFormOpen}>
					{hasEditRightPane ? (
						<Box sx={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
							{editRightPane}
						</Box>
					) : isInlineCreateOpen ? (
						<Box sx={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
							{renderCreateHostForm()}
						</Box>
					) : isResourceGroupFormView ? (
						<Box sx={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
							{renderResourceGroupForm()}
						</Box>
					) : isHostFormView ? (
						<Box sx={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
							{renderHostEditForm()}
						</Box>
					) : (
						<>
							{showGuidedConfigTopBar && (
								<HostsGuidedConfigTopBar isResourceGroupsView={isResourceGroupsView} />
							)}
							<Box
								sx={{
									p: 2,
									display: "flex",
									flexDirection: "column",
									gap: 2,
								}}
							>
								{renderDetail()}
							</Box>
						</>
					)}
				</Right>
			</SplitScreen>
		</Stack>
	);
};

export default HostsBrowseView;
