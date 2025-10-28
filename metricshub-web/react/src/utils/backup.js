import { format } from "date-fns";

export const pad2 = (n) => String(n).padStart(2, "0");

/**
 * Generate a timestamp ID string in 'yyyyMMdd-HHmmss' format.
 * Uses local time by default.
 * @param {Date} [d=new Date()]
 * @returns {string}
 */
export function timestampId(d = new Date()) {
	// Use local time by default to match previous behavior
	return format(d, "yyyyMMdd-HHmmss"); // e.g. 20251016-104205
}
