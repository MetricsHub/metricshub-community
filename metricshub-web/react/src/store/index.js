import { configureStore } from "@reduxjs/toolkit";
import { applicationStatusReducer } from "./slices/applicationStatusSlice";
import configEditorReducer from "./slices/configEditorSlice";

/**
 * Main Redux store configuration
 */
export const store = configureStore({
	reducer: {
		applicationStatus: applicationStatusReducer,
		configEditor: configEditorReducer,
	},
	devTools: true,
});
