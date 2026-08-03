import { paths } from "../../paths";
import { getHostNames, isMultiHostConfig, buildMultiHostDerivedIds } from "./host-config-utils";

/**
 * Explorer entries for a guided-config resource (one per monitored host when multi-host).
 *
 * @typedef {{ label: string; path: string; resourceId: string }} ExplorerResourceLink
 */

/**
 * @param {object} options
 * @param {string | null | undefined} options.resourceGroup `null` for standalone resources
 * @param {string} options.hostId configuration resource id
 * @param {Record<string, unknown>} [options.hostConfig]
 * @returns {ExplorerResourceLink[]}
 */
export const getExplorerResourceLinks = ({ resourceGroup, hostId, hostConfig }) => {
	if (!hostId) {
		return [];
	}

	if (hostConfig && isMultiHostConfig(hostConfig)) {
		const hostNames = getHostNames(hostConfig?.attributes?.["host.name"]);
		return buildMultiHostDerivedIds(hostId, hostNames).map((resourceId, index) => ({
			label: hostNames[index] || resourceId,
			resourceId,
			path: paths.explorerResource(resourceGroup ?? null, resourceId),
		}));
	}

	return [
		{
			label: hostId,
			resourceId: hostId,
			path: paths.explorerResource(resourceGroup ?? null, hostId),
		},
	];
};

/**
 * Explorer resource path segments for a guided-config resource.
 * Multi-host configs yield one path per monitored host (derived IDs).
 *
 * @param {object} options
 * @param {string | null | undefined} options.resourceGroup `null` for standalone resources
 * @param {string} options.hostId configuration resource id
 * @param {Record<string, unknown>} [options.hostConfig]
 * @returns {string[]} absolute app paths (no origin), e.g. `/explorer/resource-groups/G/resources/id`
 */
export const getExplorerResourcePaths = (options) =>
	getExplorerResourceLinks(options).map((link) => link.path);

/**
 * @param {string[]} resourcePaths
 * @param {string} [origin]
 * @returns {string[]}
 */
export const explorerResourceUrls = (resourcePaths, origin = "") => {
	const base = origin || (typeof window !== "undefined" ? window.location.origin : "");
	return resourcePaths.map((path) => `${base}${path}`);
};

/**
 * Opens an explorer resource URL in a new browser tab.
 *
 * @param {string} resourcePath
 */
export const openExplorerResourcePath = (resourcePath) => {
	openExplorerResourcePaths([resourcePath]);
};

/**
 * Opens each explorer resource URL in a new browser tab.
 * Call synchronously from a user gesture so popup blockers allow multiple tabs.
 *
 * @param {string[]} resourcePaths
 */
export const openExplorerResourcePaths = (resourcePaths) => {
	const urls = explorerResourceUrls(resourcePaths);
	for (const url of urls) {
		window.open(url, "_blank", "noopener,noreferrer");
	}
};
