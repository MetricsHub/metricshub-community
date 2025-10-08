// src/api/config/index.js
import { httpRequest } from "../../utils/axios-request";

const BASE = "/api/config-files";

const normalizeError = (e, fallback = "Request failed") => {
	const r = e?.response;
	if (!r) return new Error(e?.message || fallback);
	// Prefer backend-provided text or JSON message
	if (typeof r.data === "string" && r.data.trim()) return new Error(r.data);
	if (r.data?.message) return new Error(r.data.message);
	return new Error(`${r.status} ${r.statusText}`);
};

// Small helper to unwrap axios responses and map 204 -> null
const unwrap = async (promise) => {
	try {
		const res = await promise;
		return res?.status === 204 ? null : res?.data;
	} catch (e) {
		throw normalizeError(e);
	}
};

export const configApi = {
	list: ({ signal } = {}) =>
		unwrap(
			httpRequest({
				url: BASE,
				method: "GET",
				withCredentials: true,
				signal,
				headers: { Accept: "application/json" },
			}),
		),

	getContent: (name, { signal } = {}) =>
		unwrap(
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "GET",
				withCredentials: true,
				signal,
				responseType: "text",
				headers: { Accept: "text/plain" },
			}),
		),

	save: (name, content, { skipValidation = false, signal } = {}) =>
		unwrap(
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "PUT",
				withCredentials: true,
				signal,
				params: { skipValidation },
				data: content ?? "",
				headers: { "Content-Type": "text/plain", Accept: "application/json" },
			}),
		),

	validate: (name, content, { signal } = {}) =>
		unwrap(
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "POST",
				withCredentials: true,
				signal,
				data: content ?? "",
				headers: { "Content-Type": "text/plain", Accept: "application/json" },
			}),
		),

	remove: (name, { signal } = {}) =>
		unwrap(
			httpRequest({
				url: `${BASE}/${encodeURIComponent(name)}`,
				method: "DELETE",
				withCredentials: true,
				signal,
				headers: { Accept: "application/json" },
			}),
		),

	rename: (oldName, newName, { signal } = {}) =>
		unwrap(
			httpRequest({
				url: `${BASE}/${encodeURIComponent(oldName)}`,
				method: "PATCH",
				withCredentials: true,
				signal,
				data: { newName },
				headers: { "Content-Type": "application/json", Accept: "application/json" },
			}),
		),
};
