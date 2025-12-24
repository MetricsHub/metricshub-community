import { configureStore } from "@reduxjs/toolkit";
import { applicationStatusReducer } from "./slices/application-status-slice";
import { configReducer } from "./slices/config-slice";
import { explorerReducer } from "./slices/explorer-slice";
import { chatReducer } from "./slices/chat-slice";

/**
 * Main Redux store configuration
 */
export const store = configureStore({
	reducer: {
		applicationStatus: applicationStatusReducer,
		config: configReducer,
		explorer: explorerReducer,
		chat: chatReducer,
	},
	devTools: true,
});
