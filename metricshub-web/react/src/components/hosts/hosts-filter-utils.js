import { compareLocale } from "../../utils/alphabetic-sort";
import { getGroupResources, getHostDisplayName, getHostProtocolNames } from "./host-config-utils";

/** Property paths that hold secrets in host/protocol configuration tables. */
export const SENSITIVE_CONFIG_KEY_PATTERN = /(?:^|\.)(password|privacyPassword|community|bmcKey)$/i;

/**
 * @param {string} property
 * @returns {boolean}
 */
export const isSensitiveConfigProperty = (property) => SENSITIVE_CONFIG_KEY_PATTERN.test(property);

/**
 * @param {string} value
 * @returns {string}
 */
export const maskSensitiveValue = (value) => {
	const text = value === undefined || value === null ? "" : String(value);
	return text.length > 0 ? "••••••••" : "";
};

/**
 * @param {{ id: string; property: string; value: string }[]} rows
 * @param {boolean} showSensitive
 * @returns {{ id: string; property: string; value: string }[]}
 */
export const applySensitiveMaskToRows = (rows, showSensitive) => {
	if (showSensitive) {
		return rows;
	}
	return rows.map((row) =>
		isSensitiveConfigProperty(row.property)
			? { ...row, value: maskSensitiveValue(row.value) }
			: row,
	);
};

/**
 * @param {object} group entry from summarizeHostsSnapshot
 * @param {string} searchQuery
 * @param {string} protocolFilter empty string = all
 * @returns {boolean}
 */
export const groupMatchesFilters = (group, searchQuery, protocolFilter) => {
	if (protocolFilter && !group.protocols.includes(protocolFilter)) {
		return false;
	}

	const q = searchQuery.trim().toLowerCase();
	if (!q) {
		return true;
	}

	const protoStr = group.protocols.join(" ").toLowerCase();
	const attrs = Object.entries(group.node?.attributes || {})
		.map(([k, v]) => `${k} ${v}`)
		.join(" ")
		.toLowerCase();
	const hostIds = Object.keys(getGroupResources(group.node)).join(" ").toLowerCase();

	return (
		group.name.toLowerCase().includes(q) ||
		protoStr.includes(q) ||
		attrs.includes(q) ||
		hostIds.includes(q) ||
		String(group.hostCount).includes(q)
	);
};

/**
 * @param {object[]} groups
 * @param {string} sortBy
 * @returns {object[]}
 */
export const sortHostGroups = (groups, sortBy) => {
	const sorted = [...groups];
	switch (sortBy) {
		case "name-desc":
			sorted.sort((a, b) => compareLocale(b.name, a.name));
			break;
		case "hosts-desc":
			sorted.sort((a, b) => b.hostCount - a.hostCount || compareLocale(a.name, b.name));
			break;
		case "hosts-asc":
			sorted.sort((a, b) => a.hostCount - b.hostCount || compareLocale(a.name, b.name));
			break;
		default:
			sorted.sort((a, b) => compareLocale(a.name, b.name));
	}
	return sorted;
};

/**
 * @param {[string, Record<string, unknown>][]} hostEntries
 * @param {string} sortBy
 * @returns {[string, Record<string, unknown>][]}
 */
export const sortHostEntries = (hostEntries, sortBy) => {
	const sorted = [...hostEntries];
	switch (sortBy) {
		case "name-desc":
			sorted.sort((a, b) =>
				compareLocale(getHostDisplayName(b[0], b[1]), getHostDisplayName(a[0], a[1])),
			);
			break;
		case "id-desc":
			sorted.sort((a, b) => compareLocale(b[0], a[0]));
			break;
		case "id-asc":
			sorted.sort((a, b) => compareLocale(a[0], b[0]));
			break;
		default:
			sorted.sort((a, b) =>
				compareLocale(getHostDisplayName(a[0], a[1]), getHostDisplayName(b[0], b[1])),
			);
	}
	return sorted;
};

/**
 * @param {string} hostId
 * @param {Record<string, unknown>} hostConfig
 * @param {string} searchQuery
 * @returns {boolean}
 */
export const hostMatchesTreeSearch = (hostId, hostConfig, searchQuery) => {
	const q = searchQuery.trim().toLowerCase();
	if (!q) {
		return true;
	}
	const displayName = getHostDisplayName(hostId, hostConfig).toLowerCase();
	const hostType = String(hostConfig?.attributes?.["host.type"] || "").toLowerCase();
	const protocolStr = getHostProtocolNames(hostConfig).join(" ").toLowerCase();
	return (
		hostId.toLowerCase().includes(q) ||
		displayName.includes(q) ||
		hostType.includes(q) ||
		protocolStr.includes(q)
	);
};
