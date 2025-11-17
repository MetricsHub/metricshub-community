import { createAsyncThunk } from "@reduxjs/toolkit";
import { explorerApi } from "../../api/explorer";

/**
 * Fetch the explorer hierarchy from backend.
 * @returns {Promise<any>} The hierarchy JSON
 */
export const fetchExplorerHierarchy = createAsyncThunk(
    "explorer/fetchHierarchy",
    async (_, { rejectWithValue }) => {
        try {
            const data = await explorerApi.getHierarchy();
            return data;
        } catch (e) {
            return rejectWithValue(e?.message || "Failed to fetch explorer hierarchy");
        }
    },
);
