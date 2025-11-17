import { configureStore } from "@reduxjs/toolkit";
import { applicationStatusReducer } from "./slices/applicationStatusSlice";
import { configReducer } from "./slices/configSlice";
import { explorerReducer } from "./slices/explorerSlice";

/**
 * Main Redux store configuration
 */
export const store = configureStore({
	reducer: {
		applicationStatus: applicationStatusReducer,
		config: configReducer,
		explorer: explorerReducer,
	},
	devTools: true,
});
