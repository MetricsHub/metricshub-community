import { createAsyncThunk } from "@reduxjs/toolkit";
import { otelConfigApi } from "../../api/config/otel-config-api";
import { isBackupFileName } from "../../utils/backup-names";
import { createOtelBackupSet, restoreOtelBackupFile } from "../../services/otel-backup-service";

export const fetchOtelConfigList = createAsyncThunk(
	"otelConfig/fetchList",
	async (_, { rejectWithValue }) => {
		try {
			const [configs, backups] = await Promise.all([
				otelConfigApi.list(),
				otelConfigApi.listBackups().catch((err) => {
					console.warn(
						"otelConfig/fetchList: backup listing failed; continuing without backups:",
						err?.message || err,
					);
					return [];
				}),
			]);
			return [...(configs || []), ...(backups || [])];
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

export const fetchOtelConfigContent = createAsyncThunk(
	"otelConfig/fetchContent",
	async (name, { rejectWithValue }) => {
		try {
			const content = isBackupFileName(name)
				? await otelConfigApi.getBackupFileContent(name)
				: await otelConfigApi.getContent(name);
			return { name, content };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

export const saveOtelConfig = createAsyncThunk(
	"otelConfig/save",
	async ({ name, content, skipValidation = false }, { rejectWithValue }) => {
		try {
			return { meta: await otelConfigApi.save(name, content, { skipValidation }), content };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

export const saveDraftOtelConfig = createAsyncThunk(
	"otelConfig/saveDraft",
	async ({ name, content, skipValidation = false }, { rejectWithValue }) => {
		try {
			return { meta: await otelConfigApi.saveDraft(name, content, { skipValidation }), content };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

export const validateOtelConfig = createAsyncThunk(
	"otelConfig/validate",
	async ({ name, content }, { rejectWithValue }) => {
		try {
			const result = await otelConfigApi.validate(name, content);
			const valid = result.valid ?? result.isValid ?? false;
			return { name, result: { valid, errors: result.errors || [] } };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

export const deleteOtelConfig = createAsyncThunk(
	"otelConfig/delete",
	async (name, { rejectWithValue }) => {
		try {
			await otelConfigApi.remove(name);
			return name;
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

export const renameOtelConfig = createAsyncThunk(
	"otelConfig/rename",
	async ({ oldName, newName }, { rejectWithValue }) => {
		try {
			return { oldName, meta: await otelConfigApi.rename(oldName, newName) };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

export const deleteOtelBackupFile = createAsyncThunk(
	"otelConfig/deleteBackupFile",
	async (name, { rejectWithValue }) => {
		try {
			await otelConfigApi.deleteBackupFile(name);
			return name;
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

export const createOtelConfigBackup = createAsyncThunk(
	"otelConfig/createBackup",
	async ({ kind, name } = {}, { getState, dispatch, rejectWithValue }) => {
		try {
			const state = getState().otelConfig ?? {};
			const { list = [], filesByName = {}, originalsByName = {}, selected } = state;
			const effectiveName = name ?? selected;
			const { id, count } = await createOtelBackupSet(
				list,
				filesByName,
				originalsByName,
				kind,
				effectiveName,
			);
			await dispatch(fetchOtelConfigList());
			return { id, count };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

export const restoreOtelConfigFromBackup = createAsyncThunk(
	"otelConfig/restoreFromBackup",
	async ({ backupName, overwrite = false }, { getState, dispatch, rejectWithValue }) => {
		try {
			const state = getState();
			const { originalName, restoreName, content } = await restoreOtelBackupFile(
				backupName,
				overwrite,
				state,
			);
			await dispatch(saveOtelConfig({ name: restoreName, content, skipValidation: true })).unwrap();
			await dispatch(fetchOtelConfigList());
			return { originalName, restoredName: restoreName };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);
