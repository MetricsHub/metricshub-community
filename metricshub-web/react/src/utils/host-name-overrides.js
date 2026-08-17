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
 * Display slots for the mapping table: aligned to the host.name entries, with
 * slots equal to their own host entry normalized back to blank. A saved partial
 * mapping persists blank slots as the host.name entry itself (see
 * {@link buildProtocolHostnamePayload}), so on reload those fallback values must
 * render as blank cells again — the two forms are semantically identical.
 *
 * @param {unknown} value
 * @param {string[]} hostNames resource host.name entries, in resource order
 * @returns {string[]}
 */
export const deriveOverrideSlots = (value, hostNames) => {
	const names = Array.isArray(hostNames) ? hostNames : [];
	return alignHostnameOverrides(value, names.length).map((slot, index) =>
		slot &&
		slot.toLowerCase() ===
			String(names[index] ?? "")
				.trim()
				.toLowerCase()
			? ""
			: slot,
	);
};

/**
 * Groups rows whose EFFECTIVE collection hostname (the override, or the
 * host.name entry when the slot is blank) is used by more than one host.
 * Duplicates are legitimate (several hosts may share one proxy) but usually
 * accidental, so the table surfaces them as a warning, never an error.
 *
 * @param {string[]} slots display slots from {@link deriveOverrideSlots}
 * @param {string[]} hostNames resource host.name entries, in resource order
 * @returns {Record<string, number[]>} lowercased effective hostname -> row indexes (only groups of 2+)
 */
export const getDuplicateOverrideGroups = (slots, hostNames) => {
	const names = Array.isArray(hostNames) ? hostNames : [];
	/** @type {Record<string, number[]>} */
	const groups = {};
	names.forEach((name, index) => {
		const effective = String(slots?.[index] || name || "")
			.trim()
			.toLowerCase();
		if (!effective) {
			return;
		}
		(groups[effective] ??= []).push(index);
	});
	return Object.fromEntries(Object.entries(groups).filter(([, indexes]) => indexes.length > 1));
};

/**
 * Splits pasted text into positional override tokens, preserving intentionally
 * blank entries (blank = "use the host.name entry"). A newline-separated paste
 * (spreadsheet column) keeps empty lines as blank slots; a `,`/`;` list keeps
 * empty entries as blank slots; a space-separated list cannot express blanks.
 * One trailing separator (the copy terminator) is ignored.
 *
 * @param {unknown} text
 * @returns {string[]} sanitized tokens; empty string = blank slot
 */
export const splitPastedOverrides = (text) => {
	const normalized = String(text ?? "").replace(/\r\n?/g, "\n");
	const sanitizeToken = (token) => token.replace(/[;,\s]/g, "");
	if (normalized.includes("\n")) {
		return normalized.replace(/\n$/, "").split("\n").map(sanitizeToken);
	}
	if (/[;,]/.test(normalized)) {
		return normalized.replace(/[;,]$/, "").split(/[;,]/).map(sanitizeToken);
	}
	return normalized.split(/\s+/).map(sanitizeToken).filter(Boolean);
};

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
