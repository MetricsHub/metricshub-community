import { createAsyncThunk } from "@reduxjs/toolkit";
import { configApi } from "../../api/config";
import { isBackupFileName } from "../../utils/backup-names";
import { isVmFile } from "../../utils/file-type-utils";
import { createBackupSet, restoreBackupFile } from "../../services/backup-service";

/**
 * Fetch the list of configuration files.
 * @returns {Promise<{name:string,size:number,lastModificationTime:string}[]>} List of configuration files.
 */
export const fetchConfigList = createAsyncThunk(
	"config/fetchList",
	async (_, { rejectWithValue }) => {
		try {
			// Fetch config files and backups separately and merge.
			// We intentionally swallow errors from listBackups():
			// - Backup storage may be unavailable (e.g., missing backups folder, permissions, or disabled in this environment)
			// - Backups are optional; the main config list should still load even if backups are unavailable
			// We log a warning to aid debugging but continue with an empty backups array.
			const [configs, backups] = await Promise.all([
				configApi.list(),
				configApi.listBackups().catch((err) => {
					console.warn(
						"config/fetchList: backup listing failed; continuing without backups:",
						err?.message || err,
					);
					return [];
				}),
			]);
			// Merge and return; server already returns flat names
			return [...(configs || []), ...(backups || [])];
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
			const content = isBackupFileName(name)
				? await configApi.getBackupFileContent(name)
				: await configApi.getContent(name);
			return { name, content };
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
 * Save a configuration file as draft.
 * @param {{name:string,content:string,skipValidation?:boolean}} param0 The configuration file data.
 * @returns {Promise<{name:string,size:number,lastModificationTime:string}>} The saved file metadata.
 */
export const saveDraftConfig = createAsyncThunk(
	"config/saveDraft",
	async ({ name, content, skipValidation = false }, { rejectWithValue }) => {
		try {
			return { meta: await configApi.saveDraft(name, content, { skipValidation }), content };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

/**
 * Validate a configuration file's content.
 * For .vm files: validates script syntax first, then if valid, generates YAML and validates that too.
 * @param {{name:string,content:string}} param0 The configuration file data.
 * @returns {Promise<{name:string,result:{valid: boolean, errors: string[]}}>} The validation result.
 */
export const validateConfig = createAsyncThunk(
	"config/validate",
	async ({ name, content }, { rejectWithValue }) => {
		try {
			// First, validate the script/YAML syntax
			const scriptResult = await configApi.validate(name, content);

			// For .vm files: if script is valid, also validate the generated YAML
			if (isVmFile(name) && scriptResult.valid) {
				try {
					// Generate YAML from the template
					const generatedYaml = await configApi.testVelocityTemplate(name, content);
					// Validate the generated YAML (use synthetic .yaml name to trigger YAML validation)
					const yamlResult = await configApi.validate("generated.yaml", generatedYaml);
					if (!yamlResult.valid) {
						// Generated YAML is invalid - return as validation failure
						// but without line numbers (errors are in generated YAML, not script)
						return {
							name,
							result: {
								valid: false,
								errors: (yamlResult.errors || []).map((e) => ({
									message: e.message || e.msg || String(e),
									// No line/column - errors are in generated YAML, not script
								})),
							},
						};
					}
				} catch {
					// If test fails silently, just return the script validation result
					// (test button will show the actual error when clicked)
				}
			}

			return { name, result: scriptResult };
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
 * Delete a backup file by name via backup endpoint.
 */
export const deleteBackupFile = createAsyncThunk(
	"config/deleteBackupFile",
	async (name, { rejectWithValue }) => {
		try {
			await configApi.deleteBackupFile(name);
			return name;
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

/**
 * Create backups using flat backup filenames generated by encodeBackupFileName.
 * - file: backup-<timestamp>__<original-path-with-sentinels>
 * - all: same for each non-backup entry in the list
 */
export const createConfigBackup = createAsyncThunk(
	"config/createBackup",
	async ({ kind, name } = {}, { getState, dispatch, rejectWithValue }) => {
		try {
			const state = getState().config ?? {};
			const { list = [], filesByName = {}, originalsByName = {}, selected } = state;

			const effectiveName = name ?? selected;
			const { id, count } = await createBackupSet(
				list,
				filesByName,
				originalsByName,
				kind,
				effectiveName,
			);

			await dispatch(fetchConfigList());
			return { id, count };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

/**
 * Restore a config file from a flat backup filename
 * "backup-<timestamp>__<original/path>" (see parseBackupFileName).
 * If overwrite is false and the original exists, we create "<name>.restored-<timestamp>.ext".
 * Returns { originalName, restoredName }.
 * @param {{backupName:string,overwrite?:boolean}} param0
 * @returns {Promise<{originalName:string,restoredName:string}>}
 */
export const restoreConfigFromBackup = createAsyncThunk(
	"config/restoreFromBackup",
	async ({ backupName, overwrite = false }, { getState, dispatch, rejectWithValue }) => {
		try {
			const state = getState();
			const { originalName, restoreName, content } = await restoreBackupFile(
				backupName,
				overwrite,
				state,
			);

			await dispatch(saveConfig({ name: restoreName, content, skipValidation: true })).unwrap();
			await dispatch(fetchConfigList());

			return { originalName, restoredName: restoreName };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);

/**
 * Test a Velocity template and return the generated YAML.
 * After evaluating the template, also validates the generated YAML content.
 * @param {{name:string,content:string}} param0
 * @returns {Promise<{name:string,result:string,yamlValidation?:object}>}
 */
export const testVelocityTemplate = createAsyncThunk(
	"config/testVelocity",
	async ({ name, content }, { rejectWithValue }) => {
		try {
			// Step 1: Evaluate the Velocity template to get generated YAML
			const result = await configApi.testVelocityTemplate(name, content);

			// Step 2: Validate the generated YAML as if it were a .yaml file
			// Use a synthetic .yaml filename to ensure backend performs YAML validation
			let yamlValidation = { valid: true };
			try {
				yamlValidation = await configApi.validate("generated.yaml", result);
			} catch {
				// If validation call fails, continue without failing the whole test
				yamlValidation = { valid: true };
			}

			return { name, result, yamlValidation };
		} catch (e) {
			return rejectWithValue(e.message);
		}
	},
);
