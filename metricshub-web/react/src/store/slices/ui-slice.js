import { createSlice } from "@reduxjs/toolkit";

/**
 * Initial state for UI slice
 */
const initialState = {
	writeProtectionModalOpen: false,
};

/**
 * Slice for managing global UI state
 */
const uiSlice = createSlice({
	name: "ui",
	initialState,
	reducers: {
		openWriteProtectionModal(state) {
			state.writeProtectionModalOpen = true;
		},
		closeWriteProtectionModal(state) {
			state.writeProtectionModalOpen = false;
		},
	},
});

export const { openWriteProtectionModal, closeWriteProtectionModal } = uiSlice.actions;
export const uiReducer = uiSlice.reducer;
