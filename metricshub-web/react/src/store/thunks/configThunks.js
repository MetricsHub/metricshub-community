import { createAsyncThunk } from "@reduxjs/toolkit";
import { configApi } from "../../api/config";

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
