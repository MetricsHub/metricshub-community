import { createAsyncThunk } from "@reduxjs/toolkit";
import { configApi } from "../../api/config";

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

export const deleteConfig = createAsyncThunk("config/delete", async (name, { rejectWithValue }) => {
	try {
		await configApi.remove(name);
		return name;
	} catch (e) {
		return rejectWithValue(e.message);
	}
});

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
