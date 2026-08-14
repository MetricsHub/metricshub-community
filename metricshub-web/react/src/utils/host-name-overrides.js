/**
 * Positional helpers for the protocol-level hostname override of a multi-host
 * resource. Unlike host.name (see host-names.js), the override list is matched
 * to the host.name entries strictly by position
 * (PostConfigDeserializer.normalizeProtocolHostnames), so values here are never
 * deduplicated (two hosts may share one proxy) and blank slots are preserved
 * (blank means "use the host.name entry itself").
 */

/**
 * Splits an override value into a raw positional list: an array is taken
 * verbatim (trimmed, blanks kept), a string is split on `,`/`;` (trimmed,
 * blanks kept).
 *
 * @param {unknown} value
 * @returns {string[]}
 */
export const splitHostnameOverrides = (value) => {
	if (value == null) {
		return [];
	}
	const rawValues = Array.isArray(value) ? value : String(value).split(/[;,]/);
	return rawValues.map((entry) => String(entry ?? "").trim());
};

/**
 * Aligns an override value to exactly `hostCount` slots, mirroring the agent's
 * positional semantics: a shorter list repeats its last value (backend
 * clamp-to-last), a longer list keeps the first `hostCount` entries, an empty
 * value yields all-blank slots.
 *
 * @param {unknown} value
 * @param {number} hostCount
 * @returns {string[]}
 */
export const alignHostnameOverrides = (value, hostCount) => {
	const count = Math.max(0, Number(hostCount) || 0);
	const list = splitHostnameOverrides(value);
	// A blank-only list means "not configured", not "clamp blank everywhere".
	const hasContent = list.some(Boolean);
	return Array.from({ length: count }, (_, index) => {
		if (!hasContent) {
			return "";
		}
		return index < list.length ? list[index] : list[list.length - 1];
	});
};

/**
 * Counts the slots (among the first `hostCount`) holding an explicit override.
 *
 * @param {unknown} value
 * @param {number} hostCount
 * @returns {number}
 */
export const countConfiguredOverrides = (value, hostCount) =>
	alignHostnameOverrides(value, hostCount).filter(Boolean).length;

/**
 * Canonical payload form of the override. Multi-host resources always get a
 * full-length array (one entry per host.name entry, blanks replaced by the
 * host.name entry itself) so the agent's index mapping never has to clamp;
 * when no slot is configured the field is omitted entirely (undefined).
 * `,`/`;` are stripped defensively: the stored value is split on them.
 *
 * @param {unknown} value
 * @param {string[]} hostNames resource host.name entries, in resource order
 * @returns {string | string[] | undefined}
 */
export const buildProtocolHostnamePayload = (value, hostNames) => {
	const names = Array.isArray(hostNames) ? hostNames : [];
	if (names.length <= 1) {
		const single = splitHostnameOverrides(value).find(Boolean) || "";
		return single || undefined;
	}
	const slots = alignHostnameOverrides(value, names.length);
	if (!slots.some(Boolean)) {
		return undefined;
	}
	return slots.map((slot, index) => (slot || names[index] || "").replace(/[;,]/g, "").trim());
};

/**
 * Remaps an array-valued override after the host.name list changed, so each
 * override follows its host (matched case-insensitively by name): removed
 * hosts drop their override, added or renamed hosts start blank. Non-array
 * values are returned untouched (a plain string applies to every host).
 *
 * @param {string[]} prevHostNames
 * @param {string[]} nextHostNames
 * @param {unknown} value
 * @returns {unknown}
 */
export const realignHostnameOverrides = (prevHostNames, nextHostNames, value) => {
	if (!Array.isArray(value)) {
		return value;
	}
	const prevNames = Array.isArray(prevHostNames) ? prevHostNames : [];
	const nextNames = Array.isArray(nextHostNames) ? nextHostNames : [];
	const slots = alignHostnameOverrides(value, prevNames.length);
	const overrideByHost = new Map();
	prevNames.forEach((name, index) => {
		overrideByHost.set(String(name).trim().toLowerCase(), slots[index] || "");
	});
	return nextNames.map((name) => overrideByHost.get(String(name).trim().toLowerCase()) || "");
};
