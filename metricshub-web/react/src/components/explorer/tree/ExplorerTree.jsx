import * as React from "react";
import { SimpleTreeView, treeItemClasses } from "@mui/x-tree-view";
import { Box, CircularProgress, Typography } from "@mui/material";
import ExplorerTreeItem from "./TreeItem";
import ExplorerSearch from "./ExplorerSearch";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { fetchExplorerHierarchy } from "../../../store/thunks/explorer-thunks";
import {
	selectExplorerHierarchy,
	selectExplorerLoading,
	selectExplorerError,
} from "../../../store/slices/explorer-slice";

/**
 * Normalizes the raw backend hierarchy into a single `ExplorerNode` tree.
 *
 * @param {*} raw Raw hierarchy object from the API.
 * @returns {ExplorerNode | null} Normalized root node or `null` when absent.
 */
const buildTree = (raw) => {
	if (!raw) return null;

	const collectChildren = (node) => {
		// Resources are navigation leaves and must not have tree children.
		if (node.type === "resource") {
			return [];
		}

		const keys = ["children", "resources", "resourceGroups", "nodes", "items"];
		const out = [];
		for (const k of keys) {
			const v = node[k];
			if (Array.isArray(v)) out.push(...v);
		}
		return out;
	};

	const walk = (node, pathParts, parent) => {
		const name = node.name;
		const id = [...pathParts, name].join("/");
		const rawChildren = collectChildren(node);
		const children = Array.isArray(rawChildren)
			? rawChildren.map((c) => walk(c, [...pathParts, name], node))
			: [];
		const isExpandable = children.length > 0;
		return { id, name, type: node.type, children, parent, isExpandable };
	};

	return walk(raw, ["root"], null);
};

/**
 * Find a node by ID in the tree.
 * @param {ExplorerNode} root
 * @param {string} id
 * @returns {ExplorerNode | null}
 */
const findNode = (root, id) => {
	if (!root) return null;
	if (root.id === id) return root;
	if (root.children) {
		for (const child of root.children) {
			const found = findNode(child, id);
			if (found) return found;
		}
	}
	return null;
};

/**
 * Collects all expandable node IDs from the tree (all nodes with children).
 * @param {ExplorerNode} root
 * @returns {string[]} Array of node IDs that can be expanded
 */
const collectAllExpandableIds = (root) => {
	if (!root) return [];
	const ids = [];
	if (root.isExpandable) {
		ids.push(root.id);
	}
	if (root.children) {
		for (const child of root.children) {
			ids.push(...collectAllExpandableIds(child));
		}
	}
	return ids;
};

/**
 * Renders the explorer hierarchy and dispatches focus callbacks for leaf nodes.
 *
 * @param {Object} props
 * @param {string|null} [props.selectedNodeId] The ID of the node to highlight/select in the tree.
 * @param {(name: string) => void} [props.onResourceGroupFocus] Called when a resource group is selected.
 * @param {() => void} [props.onAgentFocus] Called when an agent node is selected.
 * @param {(resource: ExplorerNode, group?: ExplorerNode) => void} [props.onResourceFocus] Called when a resource leaf is selected.
 */
export default function ExplorerTree({
	selectedNodeId,
	onResourceGroupFocus,
	onAgentFocus,
	onResourceFocus,
}) {
	const dispatch = useAppDispatch();
	const hierarchyRaw = useAppSelector(selectExplorerHierarchy);
	const loading = useAppSelector(selectExplorerLoading);
	const error = useAppSelector(selectExplorerError);

	React.useEffect(() => {
		if (!hierarchyRaw && !loading && !error) {
			dispatch(fetchExplorerHierarchy());
		}
	}, [hierarchyRaw, loading, error, dispatch]);

	const treeRoot = React.useMemo(() => buildTree(hierarchyRaw), [hierarchyRaw]);

	// Compute selected items array for SimpleTreeView
	const selectedItems = React.useMemo(() => {
		if (!selectedNodeId || !treeRoot) return [];
		// Verify the node exists in the tree before selecting it
		const node = findNode(treeRoot, selectedNodeId);
		return node ? [selectedNodeId] : [];
	}, [selectedNodeId, treeRoot]);

	// Compute expanded items - expand all expandable nodes by default
	// This ensures the entire tree is visible, and when an item is selected,
	// its path is already expanded
	const expandedItems = React.useMemo(() => {
		if (!treeRoot) return [];
		// Collect all expandable node IDs (all nodes with children)
		return collectAllExpandableIds(treeRoot);
	}, [treeRoot]);

	const handleLabelClick = React.useCallback(
		(node) => {
			if (node.type === "resource-group" && onResourceGroupFocus) {
				onResourceGroupFocus(node.name);
				return;
			}
			if (node.type === "agent" && onAgentFocus) {
				onAgentFocus();
				return;
			}
			if (node.type === "resource" && onResourceFocus) {
				onResourceFocus(node, node.parent);
			}
		},
		[onResourceGroupFocus, onAgentFocus, onResourceFocus],
	);

	const handleItemClick = React.useCallback(
		(event, itemId) => {
			// Prevent focus if clicking on the expansion arrow
			if (event.target.closest(`.${treeItemClasses.iconContainer}`)) {
				return;
			}

			const node = findNode(treeRoot, itemId);
			if (node) {
				handleLabelClick(node);
			}
		},
		[treeRoot, handleLabelClick],
	);

	const renderContent = () => {
		if (loading && !treeRoot) {
			return (
				<Box sx={{ p: 1, display: "flex", alignItems: "center", gap: 1 }}>
					<CircularProgress size={18} />
					<Typography variant="body2">Loading hierarchy…</Typography>
				</Box>
			);
		}

		if (error && !treeRoot) {
			return (
				<Box sx={{ p: 1 }}>
					<Typography variant="body2" color="error">
						Failed to load hierarchy: {error}
					</Typography>
				</Box>
			);
		}

		if (!treeRoot) {
			return (
				<Box sx={{ p: 1 }}>
					<Typography variant="body2" color="text.secondary">
						No hierarchy data.
					</Typography>
				</Box>
			);
		}

		return (
			<SimpleTreeView
				aria-label="Explorer hierarchy"
				defaultExpandedItems={expandedItems}
				selectedItems={selectedItems}
				multiSelect={false}
				// Only the arrow/icon should expand/collapse. Row click just selects + triggers focus logic.
				expansionTrigger="iconContainer"
				onItemClick={handleItemClick}
				sx={{
					[`& .${treeItemClasses.content}`]: { py: 0.25, width: "100%" },
					[`& .${treeItemClasses.label}`]: { flex: 1, minWidth: 0 },
				}}
			>
				<ExplorerTreeItem node={treeRoot} />
			</SimpleTreeView>
		);
	};

	return (
		<Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
			<Box sx={{ p: 1 }}>
				<ExplorerSearch />
			</Box>
			<Box sx={{ flex: 1, overflowY: "auto" }}>{renderContent()}</Box>
		</Box>
	);
}
