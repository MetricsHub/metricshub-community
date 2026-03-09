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
		const matches = insideBraces.match(/"([^"]+)"/g);
		if (matches && matches.length > 0) {
			return matches.map((m) => m.slice(1, -1)).join(", ");
		}
		return insideBraces;
	}
	return key;
};

/**
 * Checks if a metric should be displayed as a utilization bar.
 * Returns true if unit === "1" and the metric name does not contain ".limit".
 * Metrics with ".limit" in their name are threshold values, not utilization percentages.
 *
 * @param {string} unit - The unit of the metric
 * @param {string} [metricName] - The name of the metric (optional)
 * @returns {boolean}
 */
export const isUtilizationUnit = (unit, metricName) => {
	if (unit !== "1") return false;
	if (metricName && metricName.includes(".limit")) return false;
	return true;
};

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

/**
 * Comparator for metric entries [key, value].
 * Uses compareMetricNames on the keys.
 *
 * @param {[string, any]} a
 * @param {[string, any]} b
 * @returns {number}
 */
export const compareMetricEntries = (a, b) => compareMetricNames(a[0], b[0]);

/**
 * Priority-ordered list of attribute keys to use for instance display names.
 * The first available attribute value will be used as the display name.
 */
export const INSTANCE_DISPLAY_NAME_KEYS = [
	"system.device",
	"name",
	"network.interface.name",
	"process.name",
	"system.service.name",
];

/**
 * Gets the display name for an instance based on its attributes.
 * Searches through INSTANCE_DISPLAY_NAME_KEYS in priority order.
 * Falls back to id attribute or instance name if no matching attribute is found.
 *
 * @param {any} instance - The instance object with attributes
 * @param {string} [fallbackId] - Optional fallback ID if instance.id or instance.name is not available
 * @returns {string} The display name for the instance
 */
export const getInstanceDisplayName = (instance, fallbackId) => {
	const attrs = instance?.attributes ?? {};

	// Search through priority keys
	for (const key of INSTANCE_DISPLAY_NAME_KEYS) {
		if (attrs[key]) {
			return attrs[key];
		}
	}

	// Fallback to id or instance name
	const id = attrs.id || instance?.name || fallbackId;
	return id || "Unknown";
};
