import { httpRequest } from "../../utils/axios-request";
import { normalizeAxiosError } from "../../utils/http-errors";

const BASE = "/api/ui-config";

class UiConfigApi {
	/**
	 * @param {object} [options]
	 * @param {string} [options.hostType]
	 * @param {string[]} [options.protocols]
	 * @param {boolean} [options.includeAll]
	 */
	async listConnectors({ hostType, protocols = [], includeAll = false } = {}) {
		try {
			const params = new URLSearchParams();
			if (hostType) {
				params.set("hostType", hostType);
			}
			for (const protocol of protocols) {
				if (protocol) {
					params.append("protocols", protocol);
				}
			}
			if (includeAll) {
				params.set("includeAll", "true");
			}
			const query = params.toString();
			const { data } = await httpRequest({
				url: `${BASE}/connectors${query ? `?${query}` : ""}`,
				method: "GET",
				headers: { Accept: "application/json" },
			});
			return data;
		} catch (e) {
			throw normalizeAxiosError(e);
		}
	}

	async getConnectorCatalog() {
		try {
			const { data } = await httpRequest({
				url: `${BASE}/connectors/catalog`,
				method: "GET",
				headers: { Accept: "application/json" },
			});
			return data;
		} catch (e) {
			throw normalizeAxiosError(e);
		}
	}

	async getHosts() {
		try {
			const { data } = await httpRequest({
				url: `${BASE}/hosts`,
				method: "GET",
				headers: { Accept: "application/json" },
			});
			return data;
		} catch (e) {
			throw normalizeAxiosError(e);
		}
	}

	async createResourceGroup(payload) {
		try {
			const { data } = await httpRequest({
				url: `${BASE}/resource-groups`,
				method: "POST",
				headers: { "Content-Type": "application/json" },
				data: payload,
			});
			return data;
		} catch (e) {
			throw normalizeAxiosError(e);
		}
	}

	async updateResourceGroup(groupName, payload) {
		try {
			const { data } = await httpRequest({
				url: `${BASE}/resource-groups/${encodeURIComponent(groupName)}`,
				method: "PUT",
				headers: { "Content-Type": "application/json" },
				data: payload,
			});
			return data;
		} catch (e) {
			throw normalizeAxiosError(e);
		}
	}

	async addHost(payload) {
		try {
			const { data } = await httpRequest({
				url: `${BASE}/hosts`,
				method: "POST",
				headers: { "Content-Type": "application/json" },
				data: payload,
			});
			return data;
		} catch (e) {
			throw normalizeAxiosError(e);
		}
	}

	async deleteResourceGroup(groupName) {
		try {
			const { data } = await httpRequest({
				url: `${BASE}/resource-groups/${encodeURIComponent(groupName)}`,
				method: "DELETE",
				headers: { Accept: "application/json" },
			});
			return data;
		} catch (e) {
			throw normalizeAxiosError(e);
		}
	}

	async deleteGroupedHost(groupName, hostId) {
		try {
			const { data } = await httpRequest({
				url: `${BASE}/resource-groups/${encodeURIComponent(groupName)}/hosts/${encodeURIComponent(hostId)}`,
				method: "DELETE",
				headers: { Accept: "application/json" },
			});
			return data;
		} catch (e) {
			throw normalizeAxiosError(e);
		}
	}

	async deleteStandaloneHost(hostId) {
		try {
			const { data } = await httpRequest({
				url: `${BASE}/hosts/${encodeURIComponent(hostId)}`,
				method: "DELETE",
				headers: { Accept: "application/json" },
			});
			return data;
		} catch (e) {
			throw normalizeAxiosError(e);
		}
	}

	/**
	 * Run an on-demand protocol health check (metricshub.host.up).
	 *
	 * @param {{ hostname: string; protocol: string; protocolConfig?: Record<string, unknown> }} payload
	 * @param {{ signal?: AbortSignal }} [options]
	 * @returns {Promise<{ hostUp?: number; timedOut?: boolean; errorMessage?: string; responseTimeMs?: number }>}
	 */
	async checkProtocol(payload, { signal } = {}) {
		try {
			const { data } = await httpRequest({
				url: `${BASE}/protocol-check`,
				method: "POST",
				headers: { "Content-Type": "application/json" },
				data: payload,
				signal,
			});
			return data;
		} catch (e) {
			throw normalizeAxiosError(e);
		}
	}
}

export const uiConfigApi = new UiConfigApi();
