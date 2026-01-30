import { createAsyncThunk } from "@reduxjs/toolkit";
import { logFilesApi } from "../../api/logs";

/**
 * Thunk to fetch all log files from backend
 */
export const fetchLogFiles = createAsyncThunk(
	"logFiles/fetchLogFiles",
	async (_, { rejectWithValue }) => {
		try {
			const data = await logFilesApi.list();
			return data;
		} catch (err) {
			return rejectWithValue(err?.message || "Failed to fetch log files");
		}
	},
);

/**
 * Thunk to fetch log file content (tail) from backend
 */
export const fetchLogContent = createAsyncThunk(
	"logFiles/fetchLogContent",
	async ({ fileName, maxBytes = 1048576 }, { rejectWithValue }) => {
		try {
			const content = await logFilesApi.getTail(fileName, { maxBytes });
			return { fileName, content };
		} catch (err) {
			return rejectWithValue(err?.message || "Failed to fetch log content");
		}
	},
);
