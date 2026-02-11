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

/**
 * Thunk to delete a specific log file
 */
export const deleteLogFile = createAsyncThunk(
	"logFiles/deleteLogFile",
	async ({ fileName }, { rejectWithValue }) => {
		try {
			await logFilesApi.deleteFile(fileName);
			return { fileName };
		} catch (err) {
			return rejectWithValue(err?.message || "Failed to delete log file");
		}
	},
);

/**
 * Thunk to delete all log files
 */
export const deleteAllLogFiles = createAsyncThunk(
	"logFiles/deleteAllLogFiles",
	async (_, { rejectWithValue }) => {
		try {
			const count = await logFilesApi.deleteAllFiles();
			return { count };
		} catch (err) {
			return rejectWithValue(err?.message || "Failed to delete log files");
		}
	},
);
