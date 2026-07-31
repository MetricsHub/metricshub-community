import * as React from "react";
import { SimpleTreeView, treeItemClasses } from "@mui/x-tree-view";
import { Box } from "@mui/material";
import ExplorerTreeItem from "../explorer/tree/TreeItem";
import {
	getGroupResources,
	getHostNames,
	isMultiHostConfig,
	hostMatchesFilters,
	summarizeHostsSnapshot,
} from "./host-config-utils";
import { groupMatchesFilters, sortHostGroups } from "./hosts-filter-utils";
import { NO_RESOURCE_GROUP } from "./hosts-labels";
import { compareLocale } from "../../utils/alphabetic-sort";

export const HOSTS_TREE_STANDALONE_ID = "hosts-tree/standalone-section";
export const HOSTS_TREE_ROOT_ID = "hosts-tree/root";

const DRAFT_TREE_ID_PREFIX = "hosts-tree/draft/";

/**
 * @param {string} draftId
 * @returns {string}
 */
export const draftTreeId = (draftId) => `${DRAFT_TREE_ID_PREFIX}${encodeURIComponent(draftId)}`;

/** @param {{ id: string; name: string; state?: object }} draft */
const draftTreeNode = (draft) => {
	const draftState = draft.state || {};
	return {
		id: draftTreeId(draft.id),
		// Drafts are labeled by their resource ID, like saved resources.
		name: String(draftState.hostId || "").trim() || draft.name,
		type: getHostNames(draftState.hostName).length > 1 ? "multi-host-resource" : "resource",
		badge: "draft",
		children: [],
		isExpandable: false,
	};
};

/**
 * @param {string} groupName
 * @param {string} hostId
 * @returns {string}
 */
export const groupedHostTreeId = (groupName, hostId) =>
	`hosts-tree/group/${encodeURIComponent(groupName)}/host/${encodeURIComponent(hostId)}`;

/**
 * @param {string} hostId
 * @returns {string}
 */
export const standaloneHostTreeId = (hostId) =>
	`hosts-tree/standalone/host/${encodeURIComponent(hostId)}`;

/**
 * @param {string} groupName
 * @returns {string}
 */
export const groupTreeId = (groupName) => `hosts-tree/group/${encodeURIComponent(groupName)}`;

/** Read-only nodes coming from other YAML files (not metricshub-ui.yaml). */
const EXTERNAL_ID_PREFIX = "hosts-tree/external/";
const EXTERNAL_DISABLED_TITLE =
	"Not configured through the web UI — likely defined in another configuration file (e.g. metricshub.yaml). Read-only here.";

/** Read-only top-level node grouping everything configured outside metricshub-ui.yaml. */
const EXTERNAL_ROOT_ID = `${EXTERNAL_ID_PREFIX}root`;
/** "No resource group" section inside the External tree (mirrors the editable one). */
const EXTERNAL_STANDALONE_ID = `${EXTERNAL_ID_PREFIX}standalone-section`;

const externalGroupTreeId = (groupName) =>
	`${EXTERNAL_ID_PREFIX}group/${encodeURIComponent(groupName)}`;
const externalGroupedHostTreeId = (groupName, hostId) =>
	`${EXTERNAL_ID_PREFIX}group/${encodeURIComponent(groupName)}/host/${encodeURIComponent(hostId)}`;
const externalStandaloneHostTreeId = (hostId) =>
	`${EXTERNAL_ID_PREFIX}standalone/host/${encodeURIComponent(hostId)}`;

/**
 * @param {string} id tree item id
 * @param {string} hostId resource id
 * @param {object} hostConfig raw resource config node
 * @returns {object} a dimmed, read-only tree node
 */
const externalResourceNode = (id, hostId, hostConfig) => ({
	id,
	name: hostId,
	type: isMultiHostConfig(hostConfig) ? "multi-host-resource" : "resource",
	children: [],
	isExpandable: false,
	disabled: true,
	disabledTitle: EXTERNAL_DISABLED_TITLE,
});

/** @param {unknown} groupValue external resource-group node @returns {Record<string, object>} */
const externalGroupResources = (groupValue) =>
	groupValue && typeof groupValue === "object" && groupValue.resources ? groupValue.resources : {};

/**
 * @param {object} view
 * @returns {string | null}
 */
export const hostsViewToTreeItemId = (view) => {
	switch (view?.type) {
		case "resourceGroups":
			return HOSTS_TREE_ROOT_ID;
		case "group":
			return groupTreeId(view.groupName);
		case "groupedHost":
			return groupedHostTreeId(view.groupName, view.hostId);
		case "standalone":
			return HOSTS_TREE_STANDALONE_ID;
		case "standaloneHost":
			return standaloneHostTreeId(view.hostId);
		default:
			return null;
	}
};

/**
 * @param {object} node
 * @param {string} itemId
 * @param {(view: object) => void} onViewChange
 */
const handleTreeNodeSelect = (node, itemId, onViewChange) => {
	if (itemId === HOSTS_TREE_ROOT_ID) {
		onViewChange({ type: "resourceGroups" });
		return;
	}
	if (itemId === HOSTS_TREE_STANDALONE_ID) {
		onViewChange({ type: "standalone" });
		return;
	}
	if (itemId.startsWith("hosts-tree/group/") && itemId.includes("/host/")) {
		const match = itemId.match(/^hosts-tree\/group\/([^/]+)\/host\/(.+)$/);
		if (match) {
			onViewChange({
				type: "groupedHost",
				groupName: decodeURIComponent(match[1]),
				hostId: decodeURIComponent(match[2]),
			});
		}
		return;
	}
	if (itemId.startsWith("hosts-tree/standalone/host/")) {
		const hostId = decodeURIComponent(itemId.slice("hosts-tree/standalone/host/".length));
		onViewChange({ type: "standaloneHost", hostId });
		return;
	}
	if (itemId.startsWith("hosts-tree/group/")) {
		const groupName = decodeURIComponent(itemId.slice("hosts-tree/group/".length));
		onViewChange({ type: "group", groupName });
	}
};

/**
 * Explorer-style navigation tree for resources (no protocol health indicators).
 *
 * @param {object} props
 * @param {{ resources?: Record<string, unknown>; resourceGroups?: Record<string, unknown> }} props.snapshot
 * @param {object} props.view
 * @param {(view: object) => void} props.onViewChange
 * @param {string} [props.filterSearch]
 * @param {string} [props.filterProtocol]
 * @param {string} [props.filterSortBy]
 */
const HostsResourcesTree = ({
	snapshot,
	view,
	onViewChange,
	drafts = [],
	onOpenDraft,
	filterSearch = "",
	filterProtocol = "",
	filterSortBy = "name-asc",
}) => {
	const summary = React.useMemo(() => summarizeHostsSnapshot(snapshot), [snapshot]);
	const protocolFilterForHosts = filterProtocol || "all";

	const visibleGroups = React.useMemo(() => {
		const list = summary.groups.filter((group) => {
			if (!filterSearch.trim() && !filterProtocol) {
				return true;
			}
			if (groupMatchesFilters(group, filterSearch, filterProtocol)) {
				return true;
			}
			const resources = getGroupResources(group.node);
			return Object.entries(resources).some(([hostId, hostConfig]) =>
				hostMatchesFilters(hostId, hostConfig, filterSearch, protocolFilterForHosts),
			);
		});
		return sortHostGroups(list, filterSortBy);
	}, [summary.groups, filterSearch, filterProtocol, filterSortBy, protocolFilterForHosts]);

	const visibleStandaloneHosts = React.useMemo(
		() =>
			summary.standaloneHosts.filter((host) =>
				hostMatchesFilters(host.hostId, host.hostConfig, filterSearch, protocolFilterForHosts),
			),
		[summary.standaloneHosts, filterSearch, protocolFilterForHosts],
	);

	const externalResources = React.useMemo(
		() => snapshot?.externalResources || {},
		[snapshot?.externalResources],
	);
	const externalResourceGroups = React.useMemo(
		() => snapshot?.externalResourceGroups || {},
		[snapshot?.externalResourceGroups],
	);

	const treeRoots = React.useMemo(() => {
		// A draft belongs to its target resource group; drafts without a (still
		// existing) group land in the "No resource group" section.
		const groupNames = new Set(visibleGroups.map((group) => group.name));
		/** @type {Map<string, object[]>} */
		const draftsByGroup = new Map();
		/** @type {object[]} */
		const ungroupedDrafts = [];
		for (const draft of drafts) {
			const draftState = draft.state || {};
			const groupName =
				draftState.targetType === "group" ? String(draftState.resourceGroup || "").trim() : "";
			if (groupName && groupNames.has(groupName)) {
				const list = draftsByGroup.get(groupName) || [];
				list.push(draftTreeNode(draft));
				draftsByGroup.set(groupName, list);
			} else {
				ungroupedDrafts.push(draftTreeNode(draft));
			}
		}

		/** @type {Array<{ id: string; name: string; type: string; children: object[]; isExpandable: boolean }>} */
		const groupNodes = visibleGroups.map((group) => {
			const resources = getGroupResources(group.node);
			const hostNodes = Object.entries(resources)
				.filter(([hostId, hostConfig]) =>
					hostMatchesFilters(hostId, hostConfig, filterSearch, protocolFilterForHosts),
				)
				.sort(([hostIdA], [hostIdB]) => compareLocale(hostIdA, hostIdB))
				.map(([hostId]) => ({
					id: groupedHostTreeId(group.name, hostId),
					name: hostId,
					type: isMultiHostConfig(resources[hostId]) ? "multi-host-resource" : "resource",
					children: [],
					isExpandable: false,
				}));

			// Read-only resources of this (editable) group that live in other YAML files.
			const externalHostNodes = Object.entries(
				externalGroupResources(externalResourceGroups[group.name]),
			)
				.sort(([a], [b]) => compareLocale(a, b))
				.map(([hostId, hostConfig]) =>
					externalResourceNode(externalGroupedHostTreeId(group.name, hostId), hostId, hostConfig),
				);

			const children = [
				...hostNodes,
				...(draftsByGroup.get(group.name) || []).sort((a, b) => compareLocale(a.name, b.name)),
				...externalHostNodes,
			];
			return {
				id: groupTreeId(group.name),
				name: group.name,
				type: "resource-group",
				children,
				isExpandable: children.length > 0,
			};
		});

		// Resource groups defined only in other YAML files — shown read-only (dimmed) under "External".
		const editableGroupNames = new Set(visibleGroups.map((group) => group.name));
		const externalGroupNodes = Object.entries(externalResourceGroups)
			.filter(([name]) => !editableGroupNames.has(name))
			.sort(([a], [b]) => compareLocale(a, b))
			.map(([name, value]) => {
				const children = Object.entries(externalGroupResources(value))
					.sort(([a], [b]) => compareLocale(a, b))
					.map(([hostId, hostConfig]) =>
						externalResourceNode(externalGroupedHostTreeId(name, hostId), hostId, hostConfig),
					);
				return {
					id: externalGroupTreeId(name),
					name,
					type: "resource-group",
					children,
					isExpandable: children.length > 0,
					disabled: true,
					disabledTitle: EXTERNAL_DISABLED_TITLE,
				};
			});

		// Standalone resources defined only in other YAML files — shown read-only (dimmed) under "External".
		const externalStandaloneNodes = Object.entries(externalResources)
			.sort(([a], [b]) => compareLocale(a, b))
			.map(([hostId, hostConfig]) =>
				externalResourceNode(externalStandaloneHostTreeId(hostId), hostId, hostConfig),
			);

		const standaloneChildren = [
			...visibleStandaloneHosts.map((host) => ({
				id: standaloneHostTreeId(host.hostId),
				name: host.hostId,
				type: isMultiHostConfig(host.hostConfig) ? "multi-host-resource" : "resource",
				children: [],
				isExpandable: false,
			})),
			...ungroupedDrafts,
		].sort((a, b) => a.name.localeCompare(b.name));

		const standaloneSection = {
			id: HOSTS_TREE_STANDALONE_ID,
			name: NO_RESOURCE_GROUP,
			type: "resource-group",
			children: standaloneChildren,
			isExpandable: true,
		};

		const resourceGroupsRoot = {
			id: HOSTS_TREE_ROOT_ID,
			name: "Resource Groups",
			type: "agent",
			children: [...groupNodes, standaloneSection],
			isExpandable: true,
		};

		// Everything configured outside metricshub-ui.yaml lives under a read-only "External" root,
		// a sibling of "Resource Groups" that mirrors its structure: external resource groups, then a
		// "No resource group" section holding external standalone resources.
		const hasExternal = externalGroupNodes.length > 0 || externalStandaloneNodes.length > 0;
		const externalStandaloneSection = {
			id: EXTERNAL_STANDALONE_ID,
			name: NO_RESOURCE_GROUP,
			type: "resource-group",
			children: externalStandaloneNodes,
			isExpandable: true,
			disabled: true,
			disabledTitle: EXTERNAL_DISABLED_TITLE,
		};
		const externalRoot = hasExternal
			? {
					id: EXTERNAL_ROOT_ID,
					name: "External",
					type: "external",
					children: [...externalGroupNodes, externalStandaloneSection],
					isExpandable: true,
					disabled: true,
					disabledTitle: EXTERNAL_DISABLED_TITLE,
				}
			: null;

		return externalRoot ? [resourceGroupsRoot, externalRoot] : [resourceGroupsRoot];
	}, [
		visibleGroups,
		visibleStandaloneHosts,
		drafts,
		filterSearch,
		protocolFilterForHosts,
		externalResources,
		externalResourceGroups,
	]);

	const selectedNodeId = hostsViewToTreeItemId(view);

	const selectedItems = React.useMemo(() => {
		if (!selectedNodeId) {
			return [];
		}
		return [selectedNodeId];
	}, [selectedNodeId]);

	const expandedItems = React.useMemo(() => {
		const ids = new Set([HOSTS_TREE_ROOT_ID]);
		const walk = (node) => {
			if (node.isExpandable) {
				ids.add(node.id);
			}
			for (const child of node.children || []) {
				walk(child);
			}
		};
		treeRoots.forEach(walk);
		if (view?.type === "group" || view?.type === "groupedHost") {
			ids.add(groupTreeId(view.groupName));
		}
		if (view?.type === "standalone" || view?.type === "standaloneHost") {
			ids.add(HOSTS_TREE_STANDALONE_ID);
		}
		return [...ids];
	}, [treeRoots, view]);

	const [controlledExpandedItems, setControlledExpandedItems] = React.useState(expandedItems);

	React.useEffect(() => {
		setControlledExpandedItems(expandedItems);
	}, [expandedItems]);

	const handleItemClick = React.useCallback(
		(event, itemId) => {
			if (event.target.closest(`.${treeItemClasses.iconContainer}`)) {
				return;
			}
			// External (read-only) items are not editable from the UI — clicking does nothing
			// (the expand arrow still works so their contents remain visible).
			if (itemId.startsWith(EXTERNAL_ID_PREFIX)) {
				return;
			}
			if (itemId.startsWith(DRAFT_TREE_ID_PREFIX)) {
				onOpenDraft?.(decodeURIComponent(itemId.slice(DRAFT_TREE_ID_PREFIX.length)));
				return;
			}
			handleTreeNodeSelect(null, itemId, onViewChange);
		},
		[onOpenDraft, onViewChange],
	);

	return (
		<Box sx={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
			<Box sx={{ flex: 1, overflowY: "auto", py: 0.5 }}>
				<SimpleTreeView
					aria-label="Resources"
					expandedItems={controlledExpandedItems}
					onExpandedItemsChange={(_event, itemIds) => setControlledExpandedItems(itemIds)}
					selectedItems={selectedItems}
					multiSelect={false}
					expansionTrigger="iconContainer"
					onItemClick={handleItemClick}
					sx={{
						[`& .${treeItemClasses.content}`]: {
							py: 0.25,
							width: "100%",
							transition: "background-color 0.4s ease",
						},
						[`& .${treeItemClasses.label}`]: { flex: 1, minWidth: 0 },
					}}
				>
					{treeRoots.map((root) => (
						<ExplorerTreeItem key={root.id} node={root} selectedNodeId={selectedNodeId} />
					))}
				</SimpleTreeView>
			</Box>
		</Box>
	);
};

export default React.memo(HostsResourcesTree);
