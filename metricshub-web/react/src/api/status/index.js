import { httpRequest } from "../../utils/axios-request";

/**
 * Simple API client for application status
 * GETs the backend status (expects JSON)
 */
class StatusApi {
	/**
	 * Retrieve application status from backend
	 * @returns {Promise<Object>}
	 */
	getStatus() {
		return new Promise((resolve, reject) => {
			httpRequest({
				url: "/api/status",
				method: "GET",
			})
				.then(({ data }) => resolve(data))
				.catch((error) => reject(error));
		});
	}
}

export const statusApi = new StatusApi();
