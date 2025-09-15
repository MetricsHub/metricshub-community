import { configureStore } from "@reduxjs/toolkit";
import { applicationStatusReducer } from "./slices/applicationStatusSlice";
//import { machinesReducer } from "./slices/machinesSlice";

export const store = configureStore({
	reducer: {
		applicationStatus: applicationStatusReducer,
		/*machines: machinesReducer,*/
	},
	devTools: true,
});
