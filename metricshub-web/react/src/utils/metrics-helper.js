/**
 * Extracts the base metric key by removing tags.
 * E.g. "system.cpu.utilization{core=0}" -> "system.cpu.utilization"
 *
 * @param {string} key
 * @returns {string}
 */
export const getBaseMetricKey = (key) => {
	if (!key) return "";
	return key.split("{")[0];
};

/**
 * Retrieves metadata for a given metric key, handling tagged keys.
 *
 * @param {string} key - The full metric key (potentially with tags).
 * @param {Record<string, { unit?: string, description?: string, type?: string }>} [metaMetrics]
 * @returns {{ unit?: string, description?: string, type?: string }}
 */
export const getMetricMetadata = (key, metaMetrics) => {
	if (!metaMetrics) return {};
	const baseKey = getBaseMetricKey(key);
	return metaMetrics[baseKey] || {};
};

/**
 * Extracts a display label from a metric key, preferring the content inside braces/quotes.
 *
 * @param {string} key
 * @returns {string}
 */
export const getMetricLabel = (key) => {
	const braceStart = key.indexOf("{");
	const braceEnd = key.lastIndexOf("}");

	if (braceStart !== -1 && braceEnd > braceStart) {
		const insideBraces = key.slice(braceStart + 1, braceEnd);
		const quoteStart = insideBraces.indexOf('"');
		const quoteEnd = insideBraces.lastIndexOf('"');
		if (quoteStart !== -1 && quoteEnd > quoteStart) {
			return insideBraces.slice(quoteStart + 1, quoteEnd);
		}
		return insideBraces;
	}
	return key;
};

/**
 * Checks if a metric is a utilization metric (unit === "1").
 *
 * @param {string} unit
 * @returns {boolean}
 */
export const isUtilizationUnit = (unit) => unit === "1";
