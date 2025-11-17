import { createAsyncThunk } from "@reduxjs/toolkit";
import { statusApi } from "../../api/status";

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
