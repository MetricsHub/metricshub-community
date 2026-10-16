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

/**
 * Host names that contain an environment expression, in entry order.
 *
 * @param {unknown} value
 * @returns {string[]}
 */
export const getEnvironmentExpressionNames = (value) =>
	getHostNames(value).filter((name) => /\$\{env::/i.test(name));

export const hasEnvironmentExpression = (value) => getEnvironmentExpressionNames(value).length > 0;

/**
 * Whether the value is exactly one host name and that name is an environment expression.
 *
 * @param {unknown} value
 * @returns {boolean}
 */
export const isSingleEnvironmentExpression = (value) => {
	const names = getHostNames(value);
	return names.length === 1 && hasEnvironmentExpression(names[0]);
};

/**
 * Only checks presence. Any other value, including an environment expression such as
 * ${env::NAME} (resolved or not), is treated as a hostname and validated by the backend.
 */
export const validateHostNameValue = (value, { required = false } = {}) => {
	if (required && getHostNames(value).length === 0) {
		return "host.name is required";
	}
	return null;
};
