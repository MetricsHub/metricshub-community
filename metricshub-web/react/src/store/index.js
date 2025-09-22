import { configureStore } from "@reduxjs/toolkit";
import { applicationStatusReducer } from "./slices/applicationStatusSlice";

/**
 * Main Redux store configuration
 */
export const store = configureStore({
	reducer: {
		applicationStatus: applicationStatusReducer,
	},
	devTools: true,
});
