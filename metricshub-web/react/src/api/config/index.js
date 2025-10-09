// src/api/config/index.js
import { httpRequest } from "../../utils/axios-request";

const BASE = "/api/config-files";

/**
 * Normalize any Axios-style error into a plain Error with a readable message.
 * @param {unknown} e
 * @param {string} [fallback="Request failed"]
 * @returns {Error}
 */
const normalizeError = (e, fallback = "Request failed") => {
	const r = e?.response;
	if (!r) return new Error(e?.message || fallback);
	if (typeof r.data === "string" && r.data.trim()) return new Error(r.data);
	if (r.data?.message) return new Error(r.data.message);
	return new Error(`${r.status ?? ""} ${r.statusText ?? ""}`.trim() || fallback);
};

/**
 * Axios unwrap helper: returns response.data (or null for 204),
 * throws a normalized Error on failure.
 * @param {Promise<any>} promise
 */
const unwrap = async (promise) => {
	try {
		const res = await promise;
		return res?.status === 204 ? null : res?.data;
	} catch (e) {
		throw normalizeError(e);
	}
};

/**
 * Configuration Files API
 * Mirrors the style used in AuthApi/StatusApi (class + exported singleton).
 */
class ConfigApi {
	/**
	 * List configuration files.
	 * @param {{ signal?: AbortSignal }} [opts]
	 * @returns {Promise<Array<{name:string,size?:number,lastModificationTime?:string}>>}
	 */
	list(opts = {}) {
		const { signal } = opts;
		return unwrap(
			httpRequest({
				url: BASE,
				method: "GET",
				withCredentials: true,
				signal,
				headers: { Accept: "application/json" },
			}),
		);
	}

	/**
	 * Get the raw YAML content of a file.
	 * @param {string} name
	 * @param {{ signal?: AbortSignal }} [opts]
	 * @returns {Promise<string>}
	 */
	getContent(name, opts = {}) {
		const { signal } = opts;
		return unwrap(
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "GET",
				withCredentials: true,
				signal,
				responseType: "text",
				headers: { Accept: "text/plain" },
			}),
		);
	}

	/**
	 * Save a file (PUT). Returns the file metadata from backend.
	 * @param {string} name
	 * @param {string} content
	 * @param {{ skipValidation?: boolean, signal?: AbortSignal }} [opts]
	 * @returns {Promise<Object>} // ConfigurationFile
	 */
	save(name, content, opts = {}) {
		const { skipValidation = false, signal } = opts;
		return unwrap(
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "PUT",
				withCredentials: true,
				signal,
				params: { skipValidation },
				data: content ?? "",
				headers: { "Content-Type": "text/plain", Accept: "application/json" },
			}),
		);
	}

	/**
	 * Validate a file (POST). Returns { valid:boolean, error?:string }.
	 * @param {string} name
	 * @param {string} content
	 * @param {{ signal?: AbortSignal }} [opts]
	 * @returns {Promise<{valid:boolean,error?:string}>}
	 */
	validate(name, content, opts = {}) {
		const { signal } = opts;
		return unwrap(
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "POST",
				withCredentials: true,
				signal,
				data: content ?? "",
				headers: { "Content-Type": "text/plain", Accept: "application/json" },
			}),
		);
	}

	/**
	 * Delete a file.
	 * @param {string} name
	 * @param {{ signal?: AbortSignal }} [opts]
	 * @returns {Promise<null|any>} // null on 204, or backend payload
	 */
	remove(name, opts = {}) {
		const { signal } = opts;
		return unwrap(
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "DELETE",
				withCredentials: true,
				signal,
				headers: { Accept: "application/json" },
			}),
		);
	}

	/**
	 * Rename a file. Returns the updated file metadata.
	 * @param {string} oldName
	 * @param {string} newName
	 * @param {{ signal?: AbortSignal }} [opts]
	 * @returns {Promise<Object>} // ConfigurationFile
	 */
	rename(oldName, newName, opts = {}) {
		const { signal } = opts;
		return unwrap(
			httpRequest({
				url: `${BASE}/${encodeURIComponent(oldName)}`,
				method: "PATCH",
				withCredentials: true,
				signal,
				data: { newName },
				headers: { "Content-Type": "application/json", Accept: "application/json" },
			}),
		);
	}
}

export const configApi = new ConfigApi();
