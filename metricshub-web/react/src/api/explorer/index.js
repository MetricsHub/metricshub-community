import { httpRequest } from "../../utils/axios-request";
import { normalizeAxiosError } from "../../utils/http-errors";

const BASE = "/api/explorer";

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
}

export const explorerApi = new ExplorerApi();
