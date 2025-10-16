import { createAsyncThunk } from "@reduxjs/toolkit";
import { configApi } from "../../api/config";
import { timestampId } from "../../utils/backup";

/**
 * Fetch the list of configuration files.
 * @returns {Promise<{name:string,size:number,lastModificationTime:string}[]>} List of configuration files.
 */
export const fetchConfigList = createAsyncThunk(
	"config/fetchList",
	async (_, { rejectWithValue }) => {
		try {
			return await configApi.list();
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

/**
 * Fetch the content of a configuration file.
 * @param {string} name The name of the configuration file.
 * @returns {Promise<{name:string,content:string}>} The configuration file content.
 */
export const fetchConfigContent = createAsyncThunk(
	"config/fetchContent",
	async (name, { rejectWithValue }) => {
		try {
			return { name, content: await configApi.getContent(name) };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

/**
 * Save a configuration file.
 * @param {{name:string,content:string,skipValidation?:boolean}} param0 The configuration file data.
 * @returns {Promise<{name:string,size:number,lastModificationTime:string}>} The saved file metadata.
 */
export const saveConfig = createAsyncThunk(
	"config/save",
	async ({ name, content, skipValidation = false }, { rejectWithValue }) => {
		try {
			return { meta: await configApi.save(name, content, { skipValidation }), content };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

/**
 * Validate a configuration file's content.
 * @param {{name:string,content:string}} param0 The configuration file data.
 * @returns {Promise<{name:string,result:{valid: boolean, errors: string[]}}>} The validation result.
 */
export const validateConfig = createAsyncThunk(
	"config/validate",
	async ({ name, content }, { rejectWithValue }) => {
		try {
			return { name, result: await configApi.validate(name, content) };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

/**
 * Delete a configuration file.
 * @param {string} name The name of the configuration file to delete.
 * @returns {Promise<string>} The name of the deleted configuration file.
 */
export const deleteConfig = createAsyncThunk("config/delete", async (name, { rejectWithValue }) => {
	try {
		await configApi.remove(name);
		return name;
	} catch (e) {
		return rejectWithValue(e.message);
	}
});

/**
 * Rename a configuration file.
 * @param {{oldName:string,newName:string}} param0 The old and new names of the configuration file.
 * @returns {Promise<{oldName:string,meta:{name:string,size:number,lastModificationTime:string}}>} The old name and new file metadata.
 */
export const renameConfig = createAsyncThunk(
	"config/rename",
	async ({ oldName, newName }, { rejectWithValue }) => {
		try {
			return { oldName, meta: await configApi.rename(oldName, newName) };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

/**
 * Create a backup directly in the backend under:
 * - file:   backup-<timestamp>__<original-filename>
 * - folder: backup-<timestamp>__<original files>
 * @param {{kind:"file"|"all", name?:string}} args
 * @returns {Promise<{id:string,count:number}>}
 */
export const createConfigBackup = createAsyncThunk(
	"config/createBackup",
	async ({ kind, name } = {}, { getState, dispatch, rejectWithValue }) => {
		try {
			if (kind !== "file" && kind !== "all") throw new Error("kind must be 'file' or 'all'");

			const state = getState();
			const cfg = state.config ?? {};
			const filesByName = cfg.filesByName ?? {};
			const originalsByName = cfg.originalsByName ?? {};
			const list = Array.isArray(cfg.list) ? cfg.list : [];

			const id = timestampId(new Date()); // e.g. 20251016-150220

			const baseName = (full) => {
				const only = String(full || "")
					.split("/")
					.pop();
				return only || String(full || "");
			};

			// resolve content: prefer editor cache, then originals, else fetch from backend
			const resolveContent = async (fname) => {
				const cached = filesByName[fname]?.content;
				if (cached != null) return String(cached);
				const orig = originalsByName[fname];
				if (orig != null) return String(orig);
				const fetched = await configApi.getContent(fname);
				return String(fetched ?? "");
			};

			if (kind === "file") {
				const target = name ?? cfg.selected;
				if (!target) throw new Error("No file selected/name provided for file backup");

				const content = await resolveContent(target);
				const flatName = `backup-${id}__${baseName(target)}`;
				await dispatch(saveConfig({ name: flatName, content, skipValidation: true })).unwrap();
			} else {
				const rxBackup = /^backup-\d{8}-\d{6}__/;
				const originalsOnly = list.filter((meta) => !rxBackup.test(meta?.name || ""));

				if (originalsOnly.length === 0) {
					await dispatch(fetchConfigList());
					return { id, count: 0 };
				}

				const prefix = `backup-${id}__`;
				await Promise.all(
					originalsOnly.map(async (meta) => {
						const fname = meta.name;
						const content = await resolveContent(fname);
						const flatName = `${prefix}${baseName(fname)}`;
						await dispatch(saveConfig({ name: flatName, content, skipValidation: true })).unwrap();
					}),
				);
			}

			await dispatch(fetchConfigList());
			return {
				id,
				count:
					kind === "file" ? 1 : list.filter((f) => !/^backup-\d{8}-\d{6}__/.test(f.name)).length,
			};
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

/**
 * Restore a config file from a flat backup name "backup-<timestamp>__<original-filename>".
 * If overwrite is false and the original exists, we create "<name>.restored-<timestamp>.ext".
 * Returns { originalName, restoredName }.
 * @param {{backupName:string,overwrite?:boolean}} param0
 * @returns {Promise<{originalName:string,restoredName:string}>}
 */
export const restoreConfigFromBackup = createAsyncThunk(
	"config/restoreFromBackup",
	async ({ backupName, overwrite = false }, { getState, dispatch, rejectWithValue }) => {
		try {
			if (!backupName) throw new Error("Missing backup file name");
			const rx = /^backup-(\d{8}-\d{6})__(.+)$/;
			const m = rx.exec(backupName);
			if (!m) throw new Error("Not a backup file");
			const originalName = m[2]; // filename as it was before backup (no prefix)

			// Get backup content: prefer cache else fetch
			const state = getState();
			const cached = state?.config?.filesByName?.[backupName]?.content;
			const content =
				cached != null ? String(cached) : String(await configApi.getContent(backupName));

			// Determine target name
			let restoreName = originalName;
			if (!overwrite) {
				const list = Array.isArray(state?.config?.list) ? state.config.list : [];
				const exists = list.some((f) => f.name === originalName);
				if (exists) {
					const id = timestampId();
					const parts = originalName.split("/");
					const base = parts.pop();
					const dot = base.lastIndexOf(".");
					const withSuffix =
						dot > 0
							? `${base.slice(0, dot)}.restored-${id}${base.slice(dot)}`
							: `${base}.restored-${id}`;
					restoreName = [...parts, withSuffix].join("/");
				}
			}

			await dispatch(saveConfig({ name: restoreName, content, skipValidation: true })).unwrap();

			await dispatch(fetchConfigList());

			return { originalName, restoredName: restoreName };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);
