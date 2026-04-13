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

/**
 * Fetch a single resource by name, for resources that are not attached
 * to a resource-group.
 *
 * @param {{ resourceName: string }} params
 * @returns {Promise<any>} The resource JSON
 */
export const fetchTopLevelResource = createAsyncThunk(
	"explorer/fetchTopLevelResource",
	async ({ resourceName }, { rejectWithValue }) => {
		try {
			const data = await explorerApi.getTopLevelResource(resourceName);
			return data;
		} catch (e) {
			return rejectWithValue(e?.message || "Failed to fetch top-level resource");
		}
	},
);

/**
 * Fetch a resource that belongs to a resource-group.
 *
 * @param {{ groupName: string, resourceName: string }} params
 * @returns {Promise<any>} The resource JSON
 */
export const fetchGroupedResource = createAsyncThunk(
	"explorer/fetchGroupedResource",
	async ({ groupName, resourceName }, { rejectWithValue }) => {
		try {
			const data = await explorerApi.getGroupedResource(groupName, resourceName);
			return data;
		} catch (e) {
			return rejectWithValue(e?.message || "Failed to fetch grouped resource");
		}
	},
);

/**
 * Search for resources.
 *
 * @param {string} query
 * @returns {Promise<any[]>} List of matches
 */
export const searchExplorer = createAsyncThunk(
	"explorer/search",
	async (query, { rejectWithValue }) => {
		try {
			const data = await explorerApi.search(query);
			return data;
		} catch (e) {
			return rejectWithValue(e?.message || "Failed to search explorer");
		}
	},
);
