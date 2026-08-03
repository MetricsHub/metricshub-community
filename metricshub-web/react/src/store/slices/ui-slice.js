import { createSlice } from "@reduxjs/toolkit";

/**
 * Initial state for UI slice
 */
const initialState = {
	writeProtectionModalOpen: false,
	/**
	 * Last pathname+search visited under /configuration/guided-config (the Hosts
	 * UI). The Navbar "Configuration" button targets this instead of always
	 * jumping to the resource-groups root, mirroring Explorer's own nav button.
	 * @type {string | null}
	 */
	lastVisitedGuidedConfigPath: null,
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
		setLastVisitedGuidedConfigPath(state, action) {
			state.lastVisitedGuidedConfigPath = action.payload;
		},
	},
});

export const {
	openWriteProtectionModal,
	closeWriteProtectionModal,
	setLastVisitedGuidedConfigPath,
} = uiSlice.actions;
export const uiReducer = uiSlice.reducer;
