import { compareLocale } from "../../utils/alphabetic-sort";
import { getGroupResources, getHostDisplayName } from "./host-config-utils";

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
