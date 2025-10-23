import { httpRequest } from "../../utils/axios-request";

const BASE = "/api/config-files";

/**
 * Normalize any Axios-style error into a plain Error with a readable message.
 * @param {unknown} e the error to normalize
 * @param {string} fallback fallback message if nothing else is available. Default: "Request failed"
 * @returns {Error} Normalized error
 */
const normalizeError = (e, fallback = "Request failed") => {
	const r = e?.response;
	if (!r) return new Error(e?.message || fallback);
	if (typeof r.data === "string" && r.data.trim()) return new Error(r.data);
	if (r.data?.message) return new Error(r.data.message);
	return new Error(`${r.status ?? ""} ${r.statusText ?? ""}`.trim() || fallback);
};

/**
 * Configuration Files API
 */
class ConfigApi {
	/**
	 * List configuration files.
	 * @param {{ signal?: AbortSignal }} opts Axios request options
	 * @returns {Promise<Array<{name:string,size?:number,lastModificationTime?:string}>>}
	 */
	list(opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: BASE,
				method: "GET",
				signal,
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeError(e)));
		});
	}

	/**
	 * List backup files.
	 * @param {{ signal?: AbortSignal }} opts Axios request options
	 * @returns {Promise<Array<{name:string,size?:number,lastModificationTime?:string}>>}
	 */
	listBackups(opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/backup`,
				method: "GET",
				signal,
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeError(e)));
		});
	}

	/**
	 * Get the raw YAML content of a file.
	 * @param {string} name  The file name
	 * @param {{ signal?: AbortSignal }} opts Axios request options
	 * @returns {Promise<string>}
	 */
	getContent(name, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "GET",
				signal,
				responseType: "text",
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeError(e)));
		});
	}

	/**
	 * Save a file (PUT). Returns the file metadata from backend.
	 * @param {string} name    The file name
	 * @param {string} content The raw file content
	 * @param {{ skipValidation?: boolean, signal?: AbortSignal }} opts Axios request options
	 * @returns {Promise<Object>} // ConfigurationFile
	 */
	save(name, content, opts = {}) {
		const { skipValidation = false, signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "PUT",
				signal,
				params: { skipValidation },
				data: content,
				headers: { "Content-Type": "text/plain", Accept: "application/json" },
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeError(e)));
		});
	}

	/**
	 * Validate a file (POST). Returns { valid:boolean, error?:string }.
	 * @param {string} name    The file name
	 * @param {string} content The raw file content
	 * @param {{ signal?: AbortSignal }} opts Axios request options
	 * @returns {Promise<{valid:boolean,error?:string}>}
	 */
	validate(name, content, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "POST",
				signal,
				data: content,
				headers: { "Content-Type": "text/plain", Accept: "application/json" },
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeError(e)));
		});
	}

	/**
	 * Delete a file.
	 * @param {string} name The file name
	 * @param {{ signal?: AbortSignal }} opts Axios request options
	 * @returns {Promise<null|any>} no payload on success
	 */
	remove(name, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "DELETE",
				signal,
			})
				.then((res) => resolve(res))
				.catch((e) => reject(normalizeError(e)));
		});
	}

	/**
	 * Rename a file. Returns the updated file metadata.
	 * @param {string} oldName The current file name
	 * @param {string} newName The new file name
	 * @param {{ signal?: AbortSignal }} opts Axios request options
	 * @returns {Promise<Object>} // ConfigurationFile
	 */
	rename(oldName, newName, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/${encodeURIComponent(oldName)}`,
				method: "PATCH",
				signal,
				data: { newName },
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeError(e)));
		});
	}

	/**
	 * Save or update a backup file (PUT /api/config-files/backup/{fileName})
	 * @param {string} fileName
	 * @param {string} content
	 * @param {{ signal?: AbortSignal }} opts
	 * @returns {Promise<Object>} // ConfigurationFile
	 */
	saveOrUpdateBackupFile(fileName, content, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/backup/${encodeURIComponent(fileName)}`,
				method: "PUT",
				signal,
				data: content ?? "",
				headers: { "Content-Type": "text/plain", Accept: "application/json" },
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeError(e)));
		});
	}

	/**
	 * Get the content of a backup file (GET /api/config-files/backup/{fileName})
	 * @param {string} fileName
	 * @param {{ signal?: AbortSignal }} opts
	 * @returns {Promise<string>}
	 */
	getBackupFileContent(fileName, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/backup/${encodeURIComponent(fileName)}`,
				method: "GET",
				signal,
				responseType: "text",
				headers: { Accept: "text/plain" },
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeError(e)));
		});
	}

	/**
	 * Delete a backup file (DELETE /api/config-files/backup/{fileName})
	 * @param {string} fileName
	 * @param {{ signal?: AbortSignal }} opts
	 * @returns {Promise<null|any>}
	 */
	deleteBackupFile(fileName, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/backup/${encodeURIComponent(fileName)}`,
				method: "DELETE",
				signal,
			})
				.then((res) => resolve(res))
				.catch((e) => reject(normalizeError(e)));
		});
	}
}

export const configApi = new ConfigApi();
