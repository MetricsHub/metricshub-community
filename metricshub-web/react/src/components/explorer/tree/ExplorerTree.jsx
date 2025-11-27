import * as React from "react";
import { SimpleTreeView, treeItemClasses } from "@mui/x-tree-view";
import { Box, CircularProgress, Typography } from "@mui/material";
import ExplorerTreeItem from "./TreeItem";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { fetchExplorerHierarchy } from "../../../store/thunks/explorer-thunks";
import {
	selectExplorerHierarchy,
	selectExplorerLoading,
	selectExplorerError,
} from "../../../store/slices/explorer-slice";

/**
 * @typedef {Object} ExplorerNode
 * @property {string} id Unique path-based identifier.
 * @property {string} name Display name.
 * @property {string} type Backend node type (e.g. "resource").
 * @property {ExplorerNode[]} children Normalized child nodes.
 * @property {ExplorerNode | null} [parent] Parent node if available.
 * @property {boolean} [isExpandable] Whether the node can be expanded.
 */

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
 * Renders the explorer hierarchy and dispatches focus callbacks for leaf nodes.
 *
 * @param {Object} props
 * @param {(name: string) => void} [props.onResourceGroupFocus] Called when a resource group is selected.
 * @param {() => void} [props.onAgentFocus] Called when an agent node is selected.
 * @param {(resource: ExplorerNode, group?: ExplorerNode) => void} [props.onResourceFocus] Called when a resource leaf is selected.
 */
export default function ExplorerTree({ onResourceGroupFocus, onAgentFocus }) {
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

	const handleLabelClick = React.useCallback(
		(node) => {
			if (node.type === "resource-group" && onResourceGroupFocus) {
				onResourceGroupFocus(node.name);
				return;
			}
			if (node.type === "agent" && onAgentFocus) {
				onAgentFocus();
			}
		},
		[onResourceGroupFocus, onAgentFocus],
	);

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
			defaultExpandedItems={[treeRoot.id]}
			multiSelect={false}
			// Only the arrow/icon should expand/collapse. Row click just selects + triggers focus logic.
			expansionTrigger="iconContainer"
			onItemClick={handleItemClick}
			sx={{
				[`& .${treeItemClasses.content}`]: { py: 0.25, width: "100%" },
				[`& .${treeItemClasses.label}`]: { flex: 1, minWidth: 0 },
			}}
		>
			<ExplorerTreeItem node={treeRoot} onLabelClick={handleLabelClick} />
		</SimpleTreeView>
	);
}
