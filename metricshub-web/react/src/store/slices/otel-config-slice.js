import { createSlice } from "@reduxjs/toolkit";
import { isBackupFileName } from "../../utils/backup-names";
import {
	fetchOtelConfigList,
	fetchOtelConfigContent,
	saveOtelConfig,
	saveDraftOtelConfig,
	validateOtelConfig,
	deleteOtelConfig,
	renameOtelConfig,
} from "../thunks/otel-config-thunks";

const initialState = {
	list: [],
	selected: null,
	content: "",
	filesByName: {},
	validation: null,
	loadingList: false,
	loadingContent: false,
	saving: false,
	error: null,
	dirtyByName: {},
	originalsByName: {},
	backups: {
		order: [],
		setsById: {},
	},
};

const slice = createSlice({
	name: "otelConfig",
	initialState,
	reducers: {
		select(state, action) {
			state.selected = action.payload;
			state.validation = null;
		},
		setContent(state, action) {
			const next = action.payload ?? "";
			state.content = next;
			const name = state.selected;
			if (name) {
				const prev = state.filesByName[name] || {};
				state.filesByName[name] = { ...prev, content: next };
				if (!isBackupFileName(name)) {
					state.dirtyByName[name] = next !== (state.originalsByName[name] ?? "");
				}
			}
		},
		clearError(state) {
			state.error = null;
		},
		addLocalFile(state, action) {
			const { name, content } = action.payload;
			if (!state.list.some((f) => f.name === name)) {
				state.list.push({ name, localOnly: true });
			} else {
				const i = state.list.findIndex((f) => f.name === name);
				state.list[i] = { ...state.list[i], localOnly: true };
			}
			state.filesByName[name] = { content, localOnly: true };
			state.selected = name;
			state.content = content;
			state.validation = null;
			state.dirtyByName[name] = true;
		},
		renameLocalFile(state, action) {
			const { oldName, newName } = action.payload;
			if (!state.list.some((f) => f.name === oldName)) return;
			const i = state.list.findIndex((f) => f.name === oldName);
			const isLocal = !!state.list[i].localOnly;
			if (!isLocal) return;

			state.list[i] = { ...state.list[i], name: newName, localOnly: true };
			if (state.filesByName[oldName]) {
				state.filesByName[newName] = { ...state.filesByName[oldName], localOnly: true };
				delete state.filesByName[oldName];
			}
			if (state.dirtyByName[oldName]) {
				state.dirtyByName[newName] = state.dirtyByName[oldName];
				delete state.dirtyByName[oldName];
			}
			if (state.selected === oldName) {
				state.selected = newName;
				state.content = state.filesByName[newName]?.content ?? state.content;
			}
		},
		deleteLocalFile(state, action) {
			const name = action.payload;
			const meta = state.list.find((f) => f.name === name);
			if (!meta?.localOnly) return;
			state.list = state.list.filter((f) => f.name !== name);
			delete state.filesByName[name];
			delete state.dirtyByName[name];
			delete state.originalsByName[name];
			if (state.selected === name) {
				state.selected = null;
				state.content = "";
				state.validation = null;
			}
		},
		deleteBackupSet(state, action) {
			const id = action.payload;
			if (!id) return;
			delete state.backups.setsById[id];
			state.backups.order = state.backups.order.filter((x) => x !== id);
		},
		clearBackups(state) {
			state.backups.order = [];
			state.backups.setsById = {};
		},
	},
	extraReducers: (b) => {
		b.addCase(fetchOtelConfigList.pending, (s) => {
			s.loadingList = true;
			s.error = null;
		})
			.addCase(fetchOtelConfigList.fulfilled, (s, a) => {
				s.loadingList = false;
				s.list = a.payload || [];
			})
			.addCase(fetchOtelConfigList.rejected, (s, a) => {
				s.loadingList = false;
				s.error = a.payload || a.error?.message;
			})

			.addCase(fetchOtelConfigContent.pending, (s) => {
				s.loadingContent = true;
				s.error = null;
				s.validation = null;
			})
			.addCase(fetchOtelConfigContent.fulfilled, (s, a) => {
				s.loadingContent = false;
				const { name, content = "" } = a.payload;
				s.selected = name;
				s.content = content;
				const prev = s.filesByName[name] || {};
				s.filesByName[name] = { ...prev, content };
				if (!isBackupFileName(name)) {
					s.originalsByName[name] = content;
					s.dirtyByName[name] = false;
				}
			})
			.addCase(fetchOtelConfigContent.rejected, (s, a) => {
				s.loadingContent = false;
				s.error = a.payload || a.error?.message;
			})

			.addCase(saveOtelConfig.pending, (s) => {
				s.saving = true;
				s.error = null;
			})
			.addCase(saveOtelConfig.fulfilled, (s, a) => {
				s.saving = false;
				const meta = a.payload.meta;
				const name = meta.name;
				const i = s.list.findIndex((f) => f.name === name);
				if (i >= 0) s.list[i] = meta;
				else s.list.push(meta);
				const cur = s.filesByName[name] || {};
				const savedContent = a.payload?.content ?? cur.content ?? s.content ?? "";
				s.filesByName[name] = { ...cur, content: savedContent, localOnly: false };
				if (!isBackupFileName(name)) {
					s.originalsByName[name] = savedContent;
					s.dirtyByName[name] = false;
				}
				if (s.selected === name) s.content = savedContent;
			})
			.addCase(saveOtelConfig.rejected, (s, a) => {
				s.saving = false;
				s.error = a.payload || a.error?.message;
			})

			.addCase(saveDraftOtelConfig.pending, (s) => {
				s.saving = true;
				s.error = null;
			})
			.addCase(saveDraftOtelConfig.fulfilled, (s, a) => {
				s.saving = false;
				const meta = a.payload.meta;
				const name = meta.name;
				const i = s.list.findIndex((f) => f.name === name);
				if (i >= 0) s.list[i] = meta;
				else s.list.push(meta);
				const cur = s.filesByName[name] || {};
				const savedContent = a.payload?.content ?? cur.content ?? s.content ?? "";
				s.filesByName[name] = { ...cur, content: savedContent, localOnly: false };
				if (!isBackupFileName(name)) {
					s.originalsByName[name] = savedContent;
					s.dirtyByName[name] = false;
				}
				if (s.selected === name) s.content = savedContent;
			})
			.addCase(saveDraftOtelConfig.rejected, (s, a) => {
				s.saving = false;
				s.error = a.payload || a.error?.message;
			})

			.addCase(validateOtelConfig.fulfilled, (s, a) => {
				const result = a.payload?.result ?? null;
				const name = a.payload?.name;
				s.validation = result;
				if (name) {
					const prev = s.filesByName[name] || {};
					s.filesByName[name] = { ...prev, validation: result };
				}
			})
			.addCase(validateOtelConfig.rejected, (s, a) => {
				const val = { valid: false, error: a.payload || a.error?.message };
				const name = a?.meta?.arg?.name;
				s.validation = val;
				if (name) {
					const prev = s.filesByName[name] || {};
					s.filesByName[name] = { ...prev, validation: val };
				}
			})

			.addCase(deleteOtelConfig.fulfilled, (s, a) => {
				const n = a.payload;
				s.list = s.list.filter((f) => f.name !== n);
				delete s.filesByName[n];
				delete s.originalsByName[n];
				delete s.dirtyByName[n];
				if (s.selected === n) {
					s.selected = null;
					s.content = "";
					s.validation = null;
				}
			})

			.addCase(renameOtelConfig.fulfilled, (s, a) => {
				const { oldName, meta } = a.payload;
				const newName = meta.name;
				const idx = s.list.findIndex((f) => f.name === oldName);
				if (idx !== -1) s.list[idx] = { ...s.list[idx], ...meta };
				else s.list.push(meta);
				if (s.filesByName[oldName]) {
					s.filesByName[newName] = { ...s.filesByName[oldName] };
					delete s.filesByName[oldName];
				}
				if (s.dirtyByName[oldName] !== undefined) {
					s.dirtyByName[newName] = s.dirtyByName[oldName];
					delete s.dirtyByName[oldName];
				}
				if (s.originalsByName[oldName] !== undefined) {
					s.originalsByName[newName] = s.originalsByName[oldName];
					delete s.originalsByName[oldName];
				}
			});
	},
});

export const {
	select,
	setContent,
	clearError,
	addLocalFile,
	renameLocalFile,
	deleteLocalFile,
	deleteBackupSet,
	clearBackups,
} = slice.actions;
export const otelConfigReducer = slice.reducer;
