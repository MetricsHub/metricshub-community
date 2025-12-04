import { createSlice, createSelector } from "@reduxjs/toolkit";
import {
	fetchExplorerHierarchy,
	fetchTopLevelResource,
	fetchGroupedResource,
} from "../thunks/explorer-thunks";

/**
 * Explorer slice state
 * @typedef {Object} ExplorerState
 * @property {any|null} hierarchy - The fetched hierarchy tree
 * @property {boolean} loading - Loading flag for hierarchy fetch
 * @property {string|null} error - Error message when fetch fails
 * @property {any|null} currentResource - The currently focused resource subtree
 * @property {boolean} resourceLoading - Loading flag for resource fetch
 * @property {string|null} resourceError - Error message when resource fetch fails
 */

/** @type {ExplorerState} */
const initialState = {
	/** @type {any|null} */
	hierarchy: null,
	loading: false,
	error: null,
	/** @type {any|null} */
	currentResource: null,
	resourceLoading: false,
	resourceError: null,
	/**
	 * UI state persistence (scroll position, expanded items)
	 * Keyed by resource ID (or composite key)
	 * @type {Record<string, {
	 *   scrollTop: number,
	 *   monitors: Record<string, boolean>,
	 *   pivotGroups: Record<string, boolean>
	 * }>}
	 */
	uiState: {},
	/** @type {string|null} */
	lastVisitedPath: null,
};

const explorerSlice = createSlice({
	name: "explorer",
	initialState,
	reducers: {
		/** Reset the hierarchy and loading/error flags */
		clearHierarchy(state) {
			state.hierarchy = null;
			state.loading = false;
			state.error = null;
			state.currentResource = null;
			state.resourceLoading = false;
			state.resourceError = null;
			// We might want to keep uiState or clear it. Keeping it is better for user experience.
		},
		setResourceScrollTop(state, action) {
			const { resourceId, scrollTop } = action.payload;
			if (!state.uiState[resourceId]) {
				state.uiState[resourceId] = { scrollTop: 0, monitors: {}, pivotGroups: {} };
			}
			state.uiState[resourceId].scrollTop = scrollTop;
		},
		setMonitorExpanded(state, action) {
			const { resourceId, monitorName, expanded } = action.payload;
			if (!state.uiState[resourceId]) {
				state.uiState[resourceId] = { scrollTop: 0, monitors: {}, pivotGroups: {} };
			}
			if (!state.uiState[resourceId].monitors) {
				state.uiState[resourceId].monitors = {};
			}
			state.uiState[resourceId].monitors[monitorName] = expanded;
		},
		setPivotGroupExpanded(state, action) {
			const { resourceId, groupKey, expanded } = action.payload;
			if (!state.uiState[resourceId]) {
				state.uiState[resourceId] = { scrollTop: 0, monitors: {}, pivotGroups: {} };
			}
			if (!state.uiState[resourceId].pivotGroups) {
				state.uiState[resourceId].pivotGroups = {};
			}
			state.uiState[resourceId].pivotGroups[groupKey] = expanded;
		},
		setLastVisitedPath(state, action) {
			state.lastVisitedPath = action.payload;
		},
	},
	extraReducers: (b) => {
		b.addCase(fetchExplorerHierarchy.pending, (s) => {
			s.loading = true;
			s.error = null;
		})
			.addCase(fetchExplorerHierarchy.fulfilled, (s, a) => {
				s.loading = false;
				s.hierarchy = a.payload ?? null;
			})
			.addCase(fetchExplorerHierarchy.rejected, (s, a) => {
				s.loading = false;
				s.error = a.payload || a.error?.message || "Unable to fetch explorer hierarchy";
			})
			.addCase(fetchTopLevelResource.pending, (s) => {
				s.resourceLoading = true;
				s.resourceError = null;
			})
			.addCase(fetchTopLevelResource.fulfilled, (s, a) => {
				s.resourceLoading = false;
				s.currentResource = a.payload ?? null;
			})
			.addCase(fetchTopLevelResource.rejected, (s, a) => {
				s.resourceLoading = false;
				s.resourceError = a.payload || a.error?.message || "Unable to fetch resource details";
			})
			.addCase(fetchGroupedResource.pending, (s) => {
				s.resourceLoading = true;
				s.resourceError = null;
			})
			.addCase(fetchGroupedResource.fulfilled, (s, a) => {
				s.resourceLoading = false;
				s.currentResource = a.payload ?? null;
			})
			.addCase(fetchGroupedResource.rejected, (s, a) => {
				s.resourceLoading = false;
				s.resourceError = a.payload || a.error?.message || "Unable to fetch resource details";
			});
	},
});

export const {
	clearHierarchy,
	setResourceScrollTop,
	setMonitorExpanded,
	setPivotGroupExpanded,
	setLastVisitedPath,
} = explorerSlice.actions;
export const explorerReducer = explorerSlice.reducer;

// Basic selectors
const base = (state) => state.explorer ?? initialState;
export const selectExplorerHierarchy = createSelector([base], (s) => s.hierarchy);
export const selectExplorerLoading = createSelector([base], (s) => s.loading);
export const selectExplorerError = createSelector([base], (s) => s.error);
export const selectCurrentResource = createSelector([base], (s) => s.currentResource);
export const selectResourceLoading = createSelector([base], (s) => s.resourceLoading);
export const selectResourceError = createSelector([base], (s) => s.resourceError);
export const selectLastVisitedPath = createSelector([base], (s) => s.lastVisitedPath);

export const selectResourceUiState = (resourceId) =>
	createSelector(
		[base],
		(s) => s.uiState[resourceId] || { scrollTop: 0, monitors: {}, pivotGroups: {} },
	);
