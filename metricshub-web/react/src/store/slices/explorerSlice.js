import { createSlice, createSelector } from "@reduxjs/toolkit";
import { fetchExplorerHierarchy } from "../thunks/explorerThunks";

/**
 * Explorer slice state
 * @typedef {Object} ExplorerState
 * @property {any|null} hierarchy - The fetched hierarchy tree
 * @property {boolean} loading - Loading flag for hierarchy fetch
 * @property {string|null} error - Error message when fetch fails
 * @property {number|null} lastUpdatedAt - Epoch millis when hierarchy last updated
 */

/** @type {ExplorerState} */
const initialState = {
    /** @type {any|null} */
    hierarchy: null,
    loading: false,
    error: null,
    lastUpdatedAt: null,
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
            state.lastUpdatedAt = null;
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
                s.lastUpdatedAt = Date.now();
            })
            .addCase(fetchExplorerHierarchy.rejected, (s, a) => {
                s.loading = false;
                s.error = a.payload || a.error?.message || "Unable to fetch explorer hierarchy";
            });
    },
});

export const { clearHierarchy } = explorerSlice.actions;
export const explorerReducer = explorerSlice.reducer;

// Basic selectors
const base = (state) => state.explorer ?? initialState;
export const selectExplorerHierarchy = createSelector([base], (s) => s.hierarchy);
export const selectExplorerLoading = createSelector([base], (s) => s.loading);
export const selectExplorerError = createSelector([base], (s) => s.error);
export const selectExplorerLastUpdatedAt = createSelector([base], (s) => s.lastUpdatedAt);
