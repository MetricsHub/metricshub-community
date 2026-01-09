import { createSlice } from "@reduxjs/toolkit";
import { isBackupFileName } from "../../utils/backup-names";
import {
	fetchConfigList,
	fetchConfigContent,
	saveConfig,
	validateConfig,
	deleteConfig,
	renameConfig,
} from "../thunks/config-thunks";

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
	name: "config",
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
				// Do not mark backups as dirty; they are read-only in the editor
				if (!isBackupFileName(name)) {
					state.dirtyByName[name] = next !== (state.originalsByName[name] ?? "");
				}
			}
		},
		clearError(state) {
			state.error = null;
		},

		// locally add a file (not saved to backend yet)
		addLocalFile(state, action) {
			const { name, content } = action.payload;
			if (!state.list.some((f) => f.name === name)) {
				state.list.push({ name, localOnly: true });
			} else {
				// if it already exists in list, keep localOnly if not saved yet
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

			// only handle local files here
			if (!isLocal) return;

			// update list entry
			state.list[i] = { ...state.list[i], name: newName, localOnly: true };

			// move cached content
			if (state.filesByName[oldName]) {
				state.filesByName[newName] = { ...state.filesByName[oldName], localOnly: true };
				delete state.filesByName[oldName];
			}

			// move dirty status
			if (state.dirtyByName[oldName]) {
				state.dirtyByName[newName] = state.dirtyByName[oldName];
				delete state.dirtyByName[oldName];
			}

			// update current selection
			if (state.selected === oldName) {
				state.selected = newName;
				state.content = state.filesByName[newName]?.content ?? state.content;
			}
		},

		deleteLocalFile(state, action) {
			const name = action.payload;
			const meta = state.list.find((f) => f.name === name);
			if (!meta?.localOnly) return; // only delete locally if it's unsaved
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
				const { name, content = "" } = a.payload;
				s.selected = name;
				s.content = content;

				const prev = s.filesByName[name] || {};
				s.filesByName[name] = { ...prev, content };
				// Do not treat backups as editor originals (not editable in place)
				if (!isBackupFileName(name)) {
					s.originalsByName[name] = content;
					s.dirtyByName[name] = false;
				}
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

				if (s.selected === name) {
					s.content = savedContent;
				}
			})
			.addCase(saveConfig.rejected, (s, a) => {
				s.saving = false;
				s.error = a.payload || a.error?.message;
			})

			.addCase(validateConfig.fulfilled, (s, a) => {
				// global selected validation
				const result = a.payload?.result ?? null;
				s.validation = result;
				// also store per-file validation so file list/tree can show errors per file
				const name = a.payload?.name;
				if (name) {
					const prev = s.filesByName[name] || {};
					s.filesByName[name] = { ...prev, validation: result };
				}
			})
			.addCase(validateConfig.rejected, (s, a) => {
				const val = { valid: false, error: a.payload || a.error?.message };
				s.validation = val;
				// store per-file if name available in meta args
				const name = a?.meta?.arg?.name;
				if (name) {
					const prev = s.filesByName[name] || {};
					s.filesByName[name] = { ...prev, validation: val };
				}
			})

			.addCase(deleteConfig.fulfilled, (s, a) => {
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

			.addCase(renameConfig.fulfilled, (s, a) => {
				const { oldName, meta } = a.payload;
				s.list = s.list.filter((f) => f.name === oldName).concat(meta);
				if (s.selected === oldName) s.selected = meta.name;
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
export const configReducer = slice.reducer;
