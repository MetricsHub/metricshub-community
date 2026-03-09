import { createSlice } from "@reduxjs/toolkit";
import {
	fetchLogFiles,
	fetchLogContent,
	deleteLogFile,
	deleteAllLogFiles,
} from "../thunks/log-files-thunks";

/**
 * Initial state for log files slice
 */
const initialState = {
	list: [],
	listLoading: false,
	listError: null,
	currentFileName: null,
	content: "",
	contentLoading: false,
	contentError: null,
	lastUpdatedAt: null,
	deleting: false,
	deleteError: null,
};

/**
 * Slice for managing log files state
 */
const logFilesSlice = createSlice({
	name: "logFiles",
	initialState,
	reducers: {
		clearLogFiles(state) {
			state.list = [];
			state.listError = null;
			state.listLoading = false;
		},
		clearLogContent(state) {
			state.content = "";
			state.currentFileName = null;
			state.contentError = null;
			state.contentLoading = false;
		},
	},
	extraReducers: (builder) => {
		builder
			// Fetch log files list
			.addCase(fetchLogFiles.pending, (state) => {
				state.listLoading = true;
				state.listError = null;
			})
			.addCase(fetchLogFiles.fulfilled, (state, action) => {
				state.listLoading = false;
				state.list = action.payload || [];
				state.lastUpdatedAt = Date.now();
			})
			.addCase(fetchLogFiles.rejected, (state, action) => {
				state.listLoading = false;
				state.listError = action.payload || "Unable to fetch log files";
			})
			// Fetch log content
			.addCase(fetchLogContent.pending, (state) => {
				state.contentLoading = true;
				state.contentError = null;
			})
			.addCase(fetchLogContent.fulfilled, (state, action) => {
				state.contentLoading = false;
				state.content = action.payload.content || "";
				state.currentFileName = action.payload.fileName;
			})
			.addCase(fetchLogContent.rejected, (state, action) => {
				state.contentLoading = false;
				state.contentError = action.payload || "Unable to fetch log content";
			})
			// Delete single log file
			.addCase(deleteLogFile.pending, (state) => {
				state.deleting = true;
				state.deleteError = null;
			})
			.addCase(deleteLogFile.fulfilled, (state, action) => {
				state.deleting = false;
				state.list = state.list.filter((f) => f.name !== action.payload.fileName);
				// Clear content if the deleted file was being viewed
				if (state.currentFileName === action.payload.fileName) {
					state.content = "";
					state.currentFileName = null;
				}
			})
			.addCase(deleteLogFile.rejected, (state, action) => {
				state.deleting = false;
				state.deleteError = action.payload || "Unable to delete log file";
			})
			// Delete all log files
			.addCase(deleteAllLogFiles.pending, (state) => {
				state.deleting = true;
				state.deleteError = null;
			})
			.addCase(deleteAllLogFiles.fulfilled, (state) => {
				state.deleting = false;
				state.list = [];
				state.content = "";
				state.currentFileName = null;
			})
			.addCase(deleteAllLogFiles.rejected, (state, action) => {
				state.deleting = false;
				state.deleteError = action.payload || "Unable to delete log files";
			});
	},
});

export const { clearLogFiles, clearLogContent } = logFilesSlice.actions;
export const logFilesReducer = logFilesSlice.reducer;
