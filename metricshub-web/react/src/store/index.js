import { configureStore } from "@reduxjs/toolkit";
import { applicationStatusReducer } from "./slices/applicationStatusSlice";
import { configReducer } from "./slices/configSlice";

/**
 * Main Redux store configuration
 */
export const store = configureStore({
	reducer: {
		applicationStatus: applicationStatusReducer,
		config: configReducer,
	},
	devTools: true,
});
