import { createSlice } from "@reduxjs/toolkit";
import { isBackupFileName } from "../../utils/backup-names";
import {
	fetchConfigList,
	fetchConfigContent,
	saveConfig,
	saveDraftConfig,
	validateConfig,
	deleteConfig,
	renameConfig,
	testVelocityTemplate,
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
	velocityTestResult: null,
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
		clearVelocityTestResult(state) {
			state.velocityTestResult = null;
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

			.addCase(saveDraftConfig.pending, (s) => {
				s.saving = true;
				s.error = null;
			})
			.addCase(saveDraftConfig.fulfilled, (s, a) => {
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
			.addCase(saveDraftConfig.rejected, (s, a) => {
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
				const newName = meta.name;

				// Update list
				const idx = s.list.findIndex((f) => f.name === oldName);
				if (idx !== -1) {
					s.list[idx] = { ...s.list[idx], ...meta };
				} else {
					// Fallback if not found in list (shouldn't happen)
					s.list.push(meta);
				}

				// Move cached content
				if (s.filesByName[oldName]) {
					s.filesByName[newName] = { ...s.filesByName[oldName] };
					delete s.filesByName[oldName];
				}

				// Move dirty status
				if (s.dirtyByName[oldName] !== undefined) {
					s.dirtyByName[newName] = s.dirtyByName[oldName];
					delete s.dirtyByName[oldName];
				}

				// Move original content
				if (s.originalsByName[oldName] !== undefined) {
					s.originalsByName[newName] = s.originalsByName[oldName];
					delete s.originalsByName[oldName];
				}

				// Do NOT update s.selected here.
				// Callers navigate after rename; the URL-sync effect
				// will update selected from the new route.
				// Updating selected here causes a race: Redux triggers a
				// synchronous re-render before navigate() runs, so the
				// URL-sync effect sees stale routeName ≠ new selected
				// and fires a spurious fetch for the old (deleted) file.
			})

			.addCase(testVelocityTemplate.pending, (s, a) => {
				s.velocityTestResult = {
					name: a.meta.arg.name,
					result: null,
					error: null,
					loading: true,
					yamlValidation: null,
				};
			})
			.addCase(testVelocityTemplate.fulfilled, (s, a) => {
				const name = a.payload.name;
				s.velocityTestResult = {
					name,
					result: a.payload.result,
					error: null,
					loading: false,
					yamlValidation: a.payload.yamlValidation || { valid: true },
				};
				// Clear per-file validation errors on successful test
				if (name) {
					const prev = s.filesByName[name] || {};
					s.filesByName[name] = { ...prev, validation: { valid: true, errors: [] } };
				}
			})
			.addCase(testVelocityTemplate.rejected, (s, a) => {
				const name = a.meta?.arg?.name;
				const errorMsg = a.payload || a.error?.message;
				s.velocityTestResult = {
					name,
					result: null,
					error: errorMsg,
					loading: false,
				};
				// Parse line/column from Velocity error messages such as:
				// 'Encountered "#end" at file.vm[line 23, column 1]'
				let line = null;
				let column = null;
				if (typeof errorMsg === "string") {
					const m = errorMsg.match(/\[line\s+(\d+),\s*column\s+(\d+)\]/);
					if (m) {
						line = Number(m[1]);
						column = Number(m[2]);
					}
				}
				// Store error as per-file validation so the header shows the error
				if (name) {
					const prev = s.filesByName[name] || {};
					s.filesByName[name] = {
						...prev,
						validation: {
							valid: false,
							errors: [{ message: errorMsg, line, column }],
						},
					};
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
	clearVelocityTestResult,
} = slice.actions;
export const configReducer = slice.reducer;
