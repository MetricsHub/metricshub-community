import { httpRequest } from "../../utils/axios-request";
import { normalizeAxiosError } from "../../utils/http-errors";
// Explorer endpoints are rooted at /api (no /explorer prefix)
const BASE = "/api";

/**
 * Explorer API client.
 * Provides methods to retrieve the hierarchy tree.
 */
class ExplorerApi {
	/**
	 * Get the explorer hierarchy.
	 * @param {{ signal?: AbortSignal }} [opts]
	 * @returns {Promise<any>} Hierarchy JSON structure
	 */
	getHierarchy(opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/hierarchy`,
				method: "GET",
				signal,
				headers: { Accept: "application/json" },
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeAxiosError(e)));
		});
	}

	/**
	 * Get a top-level resource (not in a resource-group).
	 * @param {string} resourceName
	 * @param {{ signal?: AbortSignal }} [opts]
	 * @returns {Promise<any>} Resource JSON structure
	 */
	getTopLevelResource(resourceName, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/resources/${encodeURIComponent(resourceName)}`,
				method: "GET",
				signal,
				headers: { Accept: "application/json" },
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeAxiosError(e)));
		});
	}

	/**
	 * Get a resource that belongs to a resource-group.
	 * @param {string} groupName
	 * @param {string} resourceName
	 * @param {{ signal?: AbortSignal }} [opts]
	 * @returns {Promise<any>} Resource JSON structure
	 */
	getGroupedResource(groupName, resourceName, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/resource-groups/${encodeURIComponent(groupName)}/resources/${encodeURIComponent(resourceName)}`,
				method: "GET",
				signal,
				headers: { Accept: "application/json" },
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeAxiosError(e)));
		});
	}

	/**
	 * Search for resources.
	 * @param {string} query
	 * @param {{ signal?: AbortSignal }} [opts]
	 * @returns {Promise<any[]>} List of matches
	 */
	search(query, opts = {}) {
		const { signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/search`,
				method: "GET",
				params: { q: query },
				signal,
				headers: { Accept: "application/json" },
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeAxiosError(e)));
		});
	}
}

export const explorerApi = new ExplorerApi();
