// src/api/config/index.js
const BASE = "/api/config-files";

const ensureOk = async (res, parse = "json") => {
	if (!res.ok) {
		const message = `${res.status} ${res.statusText}`;
		// Try to surface Spring's ResponseStatusException.reason if present
		try {
			const asText = await res.text();
			throw new Error(asText || message);
		} catch {
			throw new Error(message);
		}
	}
	return parse === "text" ? res.text() : res.status === 204 ? null : res.json();
};

export const configApi = {
	list: async () => {
		const res = await fetch(`${BASE}`, { credentials: "include" });
		return ensureOk(res, "json"); // Array<ConfigurationFile>
	},
	getContent: async (name) => {
		const res = await fetch(`${BASE}/${encodeURIComponent(name)}`, {
			credentials: "include",
			headers: { Accept: "text/plain" },
		});
		return ensureOk(res, "text");
	},
	save: async (name, content, { skipValidation = false } = {}) => {
		const res = await fetch(
			`${BASE}/${encodeURIComponent(name)}?skipValidation=${skipValidation}`,
			{
				method: "PUT",
				credentials: "include",
				headers: { "Content-Type": "text/plain" },
				body: content ?? "",
			},
		);
		return ensureOk(res, "json"); // ConfigurationFile
	},
	validate: async (name, content) => {
		const res = await fetch(`${BASE}/${encodeURIComponent(name)}`, {
			method: "POST",
			credentials: "include",
			headers: { "Content-Type": "text/plain", Accept: "application/json" },
			body: content ?? "",
		});
		return ensureOk(res, "json"); // { valid:boolean, error?:string }
	},
	remove: async (name) => {
		const res = await fetch(`${BASE}/${encodeURIComponent(name)}`, {
			method: "DELETE",
			credentials: "include",
		});
		return ensureOk(res, "json");
	},
	rename: async (oldName, newName) => {
		const res = await fetch(`${BASE}/${encodeURIComponent(oldName)}`, {
			method: "PATCH",
			credentials: "include",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ newName }),
		});
		return ensureOk(res, "json"); // ConfigurationFile
	},
};
