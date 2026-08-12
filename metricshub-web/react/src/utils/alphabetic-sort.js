/**
 * Case-insensitive locale-aware string comparison (A–Z).
 *
 * @param {string} [a]
 * @param {string} [b]
 * @returns {number}
 */
export const compareLocale = (a, b) =>
	String(a ?? "").localeCompare(String(b ?? ""), undefined, {
		sensitivity: "base",
		numeric: true,
	});

/**
 * @param {Array<{ displayName?: string; id?: string }>} items
 * @returns {Array<{ displayName?: string; id?: string }>}
 */
export const sortConnectorSummaries = (items = []) =>
	[...items].sort((a, b) => compareLocale(a.displayName || a.id, b.displayName || b.id));
