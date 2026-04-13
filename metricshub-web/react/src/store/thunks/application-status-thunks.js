import { createAsyncThunk } from "@reduxjs/toolkit";
import { statusApi } from "../../api/status";
import { httpRequest } from "../../utils/axios-request";

/**
 * Thunk to fetch application status from backend
 */
export const fetchApplicationStatus = createAsyncThunk(
	"applicationStatus/fetchApplicationStatus",
	async (_, { rejectWithValue }) => {
		try {
			const data = await statusApi.getStatus();
			return data;
		} catch (err) {
			return rejectWithValue(err?.message || "Failed to fetch application status");
		}
	},
);

/**
 * Thunk to restart the agent
 */
export const restartAgent = createAsyncThunk(
	"applicationStatus/restartAgent",
	async (_, { dispatch, rejectWithValue }) => {
		try {
			await httpRequest({
				url: "/api/agent/restart",
				method: "POST",
			});
			// Wait a bit and then refresh status
			setTimeout(() => {
				dispatch(fetchApplicationStatus());
			}, 3000);
			return { success: true };
		} catch (err) {
			return rejectWithValue(err?.message || "Failed to restart agent");
		}
	},
);
