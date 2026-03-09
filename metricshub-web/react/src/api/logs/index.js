import { httpRequest } from "../../utils/axios-request";
import { normalizeAxiosError } from "../../utils/http-errors";

const BASE = "/api/log-files";

const normalizeError = (e, fallback = "Request failed") => normalizeAxiosError(e, fallback);

/**
 * Log Files API
 */
class LogFilesApi {
	/**
	 * List all log files.
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
	 * Get the tail (last N bytes) of a log file.
	 * @param {string} name The file name
	 * @param {{ maxBytes?: number, signal?: AbortSignal }} opts Options
	 * @returns {Promise<string>}
	 */
	getTail(name, opts = {}) {
		const { maxBytes = 1048576, signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}/tail`,
				method: "GET",
				params: { maxBytes },
				signal,
				responseType: "text",
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeError(e)));
		});
	}

	/**
	 * Get the download URL for a log file.
	 * @param {string} name The file name
	 * @returns {string}
	 */
	getDownloadUrl(name) {
		return `${BASE}/${encodeURIComponent(name)}/download`;
	}

	/**
	 * Delete a specific log file.
	 * @param {string} name The file name
	 * @param {{ signal?: AbortSignal }} opts Options
	 * @returns {Promise<void>}
	 */
	deleteFile(name, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "DELETE",
				signal,
			})
				.then(() => resolve())
				.catch((e) => reject(normalizeError(e, "Failed to delete log file")));
		});
	}

	/**
	 * Delete all log files.
	 * @param {{ signal?: AbortSignal }} opts Options
	 * @returns {Promise<number>} The number of files deleted
	 */
	deleteAllFiles(opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: BASE,
				method: "DELETE",
				signal,
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeError(e, "Failed to delete log files")));
		});
	}
}

export const logFilesApi = new LogFilesApi();
