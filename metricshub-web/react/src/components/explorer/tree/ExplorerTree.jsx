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
 * @property {string} id - Stable unique id for the tree item (derived)
 * @property {string} name - Display name for the node (backend provided)
 * @property {string} type - Node type (backend provided)
 * @property {ExplorerNode[]} children - Child nodes (empty array when no children)
 */

/**
 * Normalize backend hierarchy (single root object).
 * Backend guarantees `name` & `type`. Child collections may appear under
 * one of several keys: `children`, `resources`, `resourceGroups`, `nodes`, `items`.
 * We merge them in a stable order to produce a unified children array.
 * @param {any} raw
 * @returns {ExplorerNode|null}
 */
const buildTree = (raw) => {
	if (!raw) return null;

	const collectChildren = (node) => {
		// Resources are navigation leaves and must not have tree children,
		// even if the backend sends nested collections under them.
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
		const children = rawChildren.map((c) => walk(c, [...pathParts, name], node));
		// Now that resources never collect children, expandability is equivalent
		// to "has children".
		const isExpandable = children.length > 0;
		return { id, name, type: node.type, children, parent, isExpandable };
	};

	return walk(raw, ["root"], null);
};

/**
 * ExplorerTree renders the hierarchy fetched from the explorer endpoint.
 * Recursively builds tree item nodes.
 *
 * @param {{
 *   onResourceGroupFocus?: (name: string) => void,
 *   onAgentFocus?: () => void,
 *   onResourceFocus?: (resource: any, group?: any) => void,
 * }} props
 */
export default function ExplorerTree({ onResourceGroupFocus, onAgentFocus, onResourceFocus }) {
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
			if (node.type === "resource" && onResourceFocus) {
				const resource = node.raw || node;
				const group = node.parentRaw && node.parentRaw.type === "resource-group" ? node.parentRaw : undefined;
				onResourceFocus(resource, group);
				return;
			}
			if (node.type === "agent" && onAgentFocus) {
				onAgentFocus();
			}
		},
		[onResourceGroupFocus, onAgentFocus, onResourceFocus],
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
			sx={{
				[`& .${treeItemClasses.content}`]: { py: 0.25, width: "100%" },
				[`& .${treeItemClasses.label}`]: { flex: 1, minWidth: 0 },
			}}
		>
			<ExplorerTreeItem node={treeRoot} onLabelClick={handleLabelClick} />
		</SimpleTreeView>
	);
}
