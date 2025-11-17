import { createSlice } from "@reduxjs/toolkit";
import { fetchApplicationStatus } from "../thunks/application-status-thunks";

/**
 * Initial state for application status slice
 */
const initialState = {
	data: null,
	loading: false,
	error: null,
	lastUpdatedAt: null,
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
			});
	},
});

export const { clearStatus } = applicationStatusSlice.actions;
export const applicationStatusReducer = applicationStatusSlice.reducer;
