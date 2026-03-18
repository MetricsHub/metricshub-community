import { httpRequest } from "../../utils/axios-request";
import { normalizeAxiosError } from "../../utils/http-errors";

const BASE = "/api/otel/config-files";

const normalizeError = (e, fallback = "Request failed") => normalizeAxiosError(e, fallback);

/**
 * OTEL Configuration Files API
 */
class OtelConfigApi {
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

	saveDraft(name, content, opts = {}) {
		const { skipValidation = false, signal } = opts;
		return new Promise((resolve, reject) => {
			httpRequest({
				url: `${BASE}/draft/${encodeURIComponent(name)}`,
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

export const otelConfigApi = new OtelConfigApi();
