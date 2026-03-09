import { createSlice } from "@reduxjs/toolkit";
import { fetchApplicationStatus, restartAgent } from "../thunks/application-status-thunks";

/**
 * Initial state for application status slice
 */
const initialState = {
	data: null,
	loading: false,
	error: null,
	lastUpdatedAt: null,
	restarting: false,
	restartError: null,
};

/**
 * Slice for managing application status state
 */
const applicationStatusSlice = createSlice({
	name: "applicationStatus",
	initialState,
	reducers: {
		clearStatus(state) {
			state.data = null;
			state.error = null;
			state.loading = false;
			state.lastUpdatedAt = null;
		},
		clearRestartError(state) {
			state.restartError = null;
		},
	},
	extraReducers: (builder) => {
		builder
			.addCase(fetchApplicationStatus.pending, (state) => {
				state.loading = true;
				state.error = null;
			})
			.addCase(fetchApplicationStatus.fulfilled, (state, action) => {
				state.loading = false;
				state.data = action.payload;
				state.lastUpdatedAt = Date.now();
			})
			.addCase(fetchApplicationStatus.rejected, (state, action) => {
				state.loading = false;
				state.error = action.payload || "Unable to fetch application status";
			})
			.addCase(restartAgent.pending, (state) => {
				state.restarting = true;
				state.restartError = null;
			})
			.addCase(restartAgent.fulfilled, (state) => {
				state.restarting = false;
			})
			.addCase(restartAgent.rejected, (state, action) => {
				state.restarting = false;
				state.restartError = action.payload || "Failed to restart agent";
			});
	},
});

export const { clearStatus, clearRestartError } = applicationStatusSlice.actions;
export const applicationStatusReducer = applicationStatusSlice.reducer;
