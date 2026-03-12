import { httpRequest } from "../../utils/axios-request";
import { normalizeAxiosError } from "../../utils/http-errors";

const BASE = "/api/otel/collector";

/**
 * OTEL Collector control API (restart, logs).
 */
export const otelCollectorApi = {
	/**
	 * Restart the OpenTelemetry Collector process.
	 * @returns {Promise<{ message: string }>}
	 */
	restart() {
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/restart`,
				method: "POST",
			})
				.then(({ data }) => resolve(data))
				.catch((e) => reject(normalizeAxiosError(e, "Restart failed")));
		});
	},

	/**
	 * Get the last N lines of the collector log file.
	 * @param {number} [tailLines=200]
	 * @returns {Promise<string>} Plain text log tail
	 */
	getLogs(tailLines = 200) {
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/logs`,
				method: "GET",
				params: { tailLines },
				responseType: "text",
				headers: { Accept: "text/plain" },
			})
				.then(({ data }) => resolve(typeof data === "string" ? data : String(data)))
				.catch((e) => reject(normalizeAxiosError(e, "Failed to load logs")));
		});
	},
};
