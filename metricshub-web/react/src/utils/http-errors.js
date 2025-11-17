/**
 * Normalize any Axios-style error into a plain Error with a readable message.
 * Keeps a consistent error surface across API clients.
 *
 * @param {any} e - The error to normalize (Axios error or other)
 * @param {string} [fallback="Request failed"] - Fallback message if nothing else is available
 * @returns {Error} Normalized error instance
 */
export const normalizeAxiosError = (e, fallback = "Request failed") => {
	const r = e?.response;
	if (!r) return new Error(e?.message || fallback);
	if (typeof r.data === "string" && r.data.trim()) return new Error(r.data);
	if (r.data?.message) return new Error(r.data.message);
	const code = r.status ?? "";
	const text = r.statusText ?? "";
	return new Error(`${code} ${text}`.trim() || fallback);
};
