import * as React from "react";
import { SimpleTreeView, treeItemClasses } from "@mui/x-tree-view";
import { Box, CircularProgress, Typography } from "@mui/material";
import ExplorerTreeItem from "./TreeItem";
import { useAppDispatch, useAppSelector } from "../../../hooks/store";
import { fetchExplorerHierarchy } from "../../../store/thunks/explorerThunks";
import {
	selectExplorerHierarchy,
	selectExplorerLoading,
	selectExplorerError,
} from "../../../store/slices/explorerSlice";

/**
 * @typedef {Object} ExplorerNode
 * @property {string} id - Stable unique id for the tree item
 * @property {string} name - Display name for the node
 * @property {string=} type - Optional type for iconography and styling
 * @property {ExplorerNode[]=} children - Optional child nodes
 */

/**
 * Convert backend hierarchy shape into a normalized tree node with stable ids.
 * Accepts either an object root or an array of top-level nodes.
 * Tries multiple conventional field names and merges known arrays (e.g., resources + resourceGroups).
 *
 * @param {any} raw - Raw data returned by the backend hierarchy endpoint
 * @returns {ExplorerNode|null} Normalized root node or null when input is empty
 */
const buildTree = (raw) => {
	if (!raw) return null;

	const getName = (n, fallbackIndex) =>
		n?.name ??
		n?.label ??
		n?.id ??
		(typeof fallbackIndex === "number" ? `item-${fallbackIndex}` : "(unnamed)");
	const getType = (n) => n?.type ?? n?.kind ?? n?.category ?? undefined;
	const getChildren = (n) => {
		const candidates = [n?.children, n?.nodes, n?.items, n?.resources, n?.resourceGroups];
		const merged = [];
		for (const c of candidates) if (Array.isArray(c)) merged.push(...c);
		return merged.length ? merged : null;
	};

	const walk = (node, pathParts, pos) => {
		const name = getName(node, pos);
		const id = pathParts.concat([name]).join("/");
		const rawChildren = getChildren(node);
		const children = Array.isArray(rawChildren)
			? rawChildren.map((c, i) => walk(c, pathParts.concat([name]), i))
			: [];
		return { id, name, type: getType(node), children };
	};

	// If backend returns an array at the root
	if (Array.isArray(raw)) {
		// If it contains a single element, treat that element as the root
		if (raw.length === 1) {
			return walk(raw[0], ["root"], 0);
		}
		// Otherwise create a synthetic grouping node and attach each item
		return {
			id: "__explorer_root__",
			name: "Explorer",
			type: "group",
			children: raw.map((c, i) => walk(c, ["Explorer"], i)),
		};
	}

	return walk(raw, ["root"], 0);
};

/**
 * ExplorerTree renders the hierarchy fetched from the explorer endpoint.
 * Recursively builds tree item nodes similar in style to ConfigTree.
 */
export default function ExplorerTree() {
	const dispatch = useAppDispatch();
	const hierarchyRaw = useAppSelector(selectExplorerHierarchy);
	const loading = useAppSelector(selectExplorerLoading);
	const error = useAppSelector(selectExplorerError);

	// Fetch hierarchy on mount (and when first empty) – avoid re-fetch if already loaded.
	React.useEffect(() => {
		if (!hierarchyRaw && !loading && !error) {
			dispatch(fetchExplorerHierarchy());
		}
	}, [hierarchyRaw, loading, error, dispatch]);

	const treeRoot = React.useMemo(() => buildTree(hierarchyRaw), [hierarchyRaw]);

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
			<ExplorerTreeItem node={treeRoot} />
		</SimpleTreeView>
	);
}
