import { createSlice } from "@reduxjs/toolkit";
import {
	fetchConfigList,
	fetchConfigContent,
	saveConfig,
	validateConfig,
	deleteConfig,
	renameConfig,
} from "../thunks/configThunks";

const initialState = {
	list: [],
	selected: null,
	content: "",
	validation: null,
	loadingList: false,
	loadingContent: false,
	saving: false,
	error: null,
};

const slice = createSlice({
	name: "config",
	initialState,
	reducers: {
		select(state, action) {
			state.selected = action.payload;
			state.validation = null;
		},
		setContent(state, action) {
			state.content = action.payload ?? "";
		},
		clearError(state) {
			state.error = null;
		},
	},
	extraReducers: (b) => {
		b.addCase(fetchConfigList.pending, (s) => {
			s.loadingList = true;
			s.error = null;
		})
			.addCase(fetchConfigList.fulfilled, (s, a) => {
				s.loadingList = false;
				s.list = a.payload || [];
			})
			.addCase(fetchConfigList.rejected, (s, a) => {
				s.loadingList = false;
				s.error = a.payload || a.error?.message;
			})

			.addCase(fetchConfigContent.pending, (s) => {
				s.loadingContent = true;
				s.error = null;
				s.validation = null;
			})
			.addCase(fetchConfigContent.fulfilled, (s, a) => {
				s.loadingContent = false;
				s.selected = a.payload.name;
				s.content = a.payload.content || "";
			})
			.addCase(fetchConfigContent.rejected, (s, a) => {
				s.loadingContent = false;
				s.error = a.payload || a.error?.message;
			})

			.addCase(saveConfig.pending, (s) => {
				s.saving = true;
				s.error = null;
			})
			.addCase(saveConfig.fulfilled, (s, a) => {
				s.saving = false;
				const meta = a.payload.meta;
				const i = s.list.findIndex((f) => f.name === meta.name);
				if (i >= 0) s.list[i] = meta;
				else s.list.push(meta);
			})
			.addCase(saveConfig.rejected, (s, a) => {
				s.saving = false;
				s.error = a.payload || a.error?.message;
			})

			.addCase(validateConfig.fulfilled, (s, a) => {
				s.validation = a.payload.result || null;
			})
			.addCase(validateConfig.rejected, (s, a) => {
				s.validation = { valid: false, error: a.payload || a.error?.message };
			})

			.addCase(deleteConfig.fulfilled, (s, a) => {
				const n = a.payload;
				s.list = s.list.filter((f) => f.name !== n);
				if (s.selected === n) {
					s.selected = null;
					s.content = "";
					s.validation = null;
				}
			})

			.addCase(renameConfig.fulfilled, (s, a) => {
				const { oldName, meta } = a.payload;
				s.list = s.list.filter((f) => f.name !== oldName).concat(meta);
				if (s.selected === oldName) s.selected = meta.name;
			});
	},
});

export const { select, setContent, clearError } = slice.actions;
export const configReducer = slice.reducer;
