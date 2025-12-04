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

/**
 * Helper to get the value of a metric, handling both primitive values and objects with a 'value' property.
 *
 * @param {any} metric
 * @returns {any}
 */
export const getMetricValue = (metric) => {
	if (metric && typeof metric === "object" && "value" in metric) {
		return metric.value;
	}
	return metric;
};

/**
 * Natural sort comparator for metric names.
 * Handles cases like "cpu0", "cpu1", "cpu10" correctly.
 *
 * @param {string} nameA
 * @param {string} nameB
 * @returns {number}
 */
export const compareMetricNames = (nameA, nameB) => {
	const re = /(.*?)(\d+)$/;
	const ma = nameA.match(re);
	const mb = nameB.match(re);

	if (!ma || !mb || ma[1] !== mb[1]) {
		// Fallback to plain string compare when no common prefix+index
		return nameA.localeCompare(nameB);
	}

	const idxA = parseInt(ma[2], 10);
	const idxB = parseInt(mb[2], 10);

	if (Number.isNaN(idxA) || Number.isNaN(idxB)) {
		return nameA.localeCompare(nameB);
	}

	if (idxA === idxB) return 0;
	return idxA < idxB ? -1 : 1;
};
