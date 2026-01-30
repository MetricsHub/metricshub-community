import { createSlice } from "@reduxjs/toolkit";
import { fetchLogFiles, fetchLogContent } from "../thunks/log-files-thunks";

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
			});
	},
});

export const { clearLogFiles, clearLogContent } = logFilesSlice.actions;
export const logFilesReducer = logFilesSlice.reducer;
