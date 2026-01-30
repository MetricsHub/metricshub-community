/** Keys to exclude from status details table (already displayed elsewhere) */
export const EXCLUDED_STATUS_KEYS = [
	"status",
	"agentInfo",
	"numberOfMonitors",
	"numberOfConfiguredResources",
	"memoryUsageBytes",
	"memoryUsagePercent",
	"cpuUsage",
];

/**
 * Returns a color based on usage percentage thresholds.
 * @param {number | undefined} percentage - The usage percentage
 * @returns {string} Hex color code
 */
export const getUsageColor = (percentage) => {
	if (typeof percentage !== "number") return "#1976d2";
	if (percentage < 50) return "#2e7d32";
	if (percentage < 80) return "#ed6c02";
	return "#d32f2f";
};

/**
 * Converts a value to a display string for the table.
 * @param {unknown} value - The value to format
 * @returns {string} Formatted string representation
 */
export const formatTableValue = (value) => {
	if (value === null || value === undefined) return "—";
	if (typeof value === "object") return JSON.stringify(value);
	return String(value);
};

/**
 * Transforms an object into DataGrid rows.
 * @param {Record<string, unknown>} obj - The object to transform
 * @param {string[]} [excludeKeys=[]] - Keys to exclude from transformation
 * @returns {Array<{id: string, property: string, value: string}>} DataGrid rows
 */
export const objectToRows = (obj, excludeKeys = []) =>
	Object.entries(obj)
		.filter(([key]) => !excludeKeys.includes(key))
		.map(([key, value]) => ({
			id: key,
			property: key,
			value: formatTableValue(value),
		}));
