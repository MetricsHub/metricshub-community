import { matchPath } from "react-router-dom";
import { paths } from "../../paths";
import { HOSTS_VIEWS } from "./hosts-navigation";

/** Route patterns under guided-config (most specific first). */
const GUIDED_CONFIG_MATCH_PATTERNS = [
	"/configuration/guided-config/resource-groups/:groupName/resources/:resourceId/edit",
	"/configuration/guided-config/resource-groups/:groupName/resources/:hostId/edit",
	"/configuration/guided-config/resource-groups/:groupName/resources/:resourceId",
	"/configuration/guided-config/no-resource-group/resources/:hostId/edit",
	"/configuration/guided-config/no-resource-group/resources/:hostId",
	"/configuration/guided-config/resource-groups/:groupName/edit",
	"/configuration/guided-config/resource-groups/:groupName",
];

/**
 * @param {string} pathname
 * @returns {boolean}
 */
export const isGuidedConfigPath = (pathname) => Boolean(pathname?.startsWith(paths.guidedConfig));

/**
 * Extracts React Router params from a guided-config pathname (for refresh / deep links).
 *
 * @param {string} pathname
 * @returns {{ groupName?: string; hostId?: string; resourceId?: string }}
 */
export const guidedConfigParamsFromPathname = (pathname) => {
	for (const pattern of GUIDED_CONFIG_MATCH_PATTERNS) {
		const match = matchPath({ path: pattern, end: true }, pathname);
		if (match?.params) {
			return match.params;
		}
	}
	return {};
};

/**
 * @param {string} pathname
 * @returns {ReturnType<typeof HOSTS_VIEWS.resourceGroups>}
 */
export const viewFromPathname = (pathname) =>
	viewFromLocation(pathname, guidedConfigParamsFromPathname(pathname));

/**
 * Derives browse view state from the current URL (tree layout).
 *
 * @param {string} pathname
 * @param {{ groupName?: string; hostId?: string; resourceId?: string }} params
 * @returns {ReturnType<typeof HOSTS_VIEWS.resourceGroups>}
 */
export const viewFromLocation = (pathname, params = {}) => {
	// Default landing page for the guided-config section
	if (
		pathname === paths.hostsResourceGroups() ||
		pathname === paths.hosts ||
		pathname === paths.guidedConfig
	) {
		return HOSTS_VIEWS.resourceGroups();
	}

	if (pathname === paths.hostsStandaloneSection()) {
		return HOSTS_VIEWS.standalone();
	}

	if (pathname === paths.hostsResourceGroupNew) {
		return HOSTS_VIEWS.resourceGroups();
	}

	const groupName = params.groupName ? decodeURIComponent(params.groupName) : null;
	const resourceId = params.resourceId
		? decodeURIComponent(params.resourceId)
		: params.hostId
			? decodeURIComponent(params.hostId)
			: null;

	if (groupName && resourceId && pathname.includes("/resources/")) {
		return HOSTS_VIEWS.groupedHost(groupName, resourceId);
	}

	// Resource group detail: /resource-groups/:group
	if (groupName && !pathname.endsWith("/new") && !pathname.includes("/resources/")) {
		return HOSTS_VIEWS.group(groupName);
	}

	if (
		pathname.startsWith(`${paths.hostsStandaloneSection()}/resources/`) &&
		!pathname.endsWith("/new")
	) {
		const id = pathname.slice(`${paths.hostsStandaloneSection()}/resources/`.length).split("/")[0];
		if (id) {
			return HOSTS_VIEWS.standaloneHost(decodeURIComponent(id));
		}
	}

	return HOSTS_VIEWS.resourceGroups();
};

/**
 * @param {ReturnType<typeof HOSTS_VIEWS.resourceGroups>} view
 * @returns {string}
 */
export const pathForView = (view) => {
	switch (view.type) {
		case "resourceGroups":
			return paths.hostsResourceGroups();
		case "group":
			return paths.hostsResourceGroup(view.groupName);
		case "groupedHost":
			return paths.hostsGroupedResource(view.groupName, view.hostId);
		case "standalone":
			return paths.hostsStandaloneSection();
		case "standaloneHost":
			return paths.hostsStandaloneResource(view.hostId);
		default:
			return paths.hostsResourceGroups();
	}
};
