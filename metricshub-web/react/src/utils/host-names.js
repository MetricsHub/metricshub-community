/**
 * Splits a hostname value (string with `,`/`;` separators, or array) into a
 * deduplicated list of host names, preserving the entry order. Order is
 * significant: multi-host protocol hostnames are matched to host.name entries
 * by position (see PostConfigDeserializer.normalizeProtocolHostnames).
 *
 * @param {unknown} hostName
 * @returns {string[]}
 */
export const getHostNames = (hostName) => {
	const rawNames = Array.isArray(hostName)
		? hostName
		: String(hostName ?? "")
				.split(/[;,]/)
				.map((name) => name.trim());
	const seen = new Set();
	return rawNames
		.map((name) => String(name ?? "").trim())
		.filter(Boolean)
		.filter((name) => {
			const key = name.toLowerCase();
			if (seen.has(key)) {
				return false;
			}
			seen.add(key);
			return true;
		});
};

/**
 * Canonical form written to the configuration: an array for multiple host
 * names, a plain string for a single one.
 *
 * @param {unknown} hostName
 * @returns {string | string[]}
 */
export const normalizeHostNameValue = (hostName) => {
	const names = getHostNames(hostName);
	if (names.length > 1) {
		return names;
	}
	return names[0] || "";
};
