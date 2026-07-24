import { explorerApi } from "../api/explorer";

/**
 * @param {unknown} value
 * @returns {boolean}
 */
const hasNonEmptyRecord = (value) =>
	Boolean(value) && typeof value === "object" && Object.keys(value).length > 0;

/**
 * @param {unknown} value
 * @returns {boolean}
 */
const hasNonEmptyMetrics = (value) => {
	if (!value) {
		return false;
	}
	if (Array.isArray(value)) {
		return value.length > 0;
	}
	if (typeof value === "object") {
		return Object.keys(value).length > 0;
	}
	return false;
};

/**
 * Explorer resource JSON is considered ready once collection has produced a
 * populated resource page — metrics, attributes, and/or monitors. An empty
 * connector list still means the page has loaded (e.g. no monitors matched).
 *
 * @param {unknown} data
 * @returns {boolean}
 */
export const isExplorerResourceDataReady = (data) => {
	if (!data || typeof data !== "object") {
		return false;
	}
	const resource =
		/** @type {{ connectors?: unknown[]; metrics?: unknown; attributes?: unknown }} */ (data);
	if (Array.isArray(resource.connectors) && resource.connectors.length > 0) {
		return true;
	}
	if (hasNonEmptyMetrics(resource.metrics)) {
		return true;
	}
	if (hasNonEmptyRecord(resource.attributes)) {
		return true;
	}
	return false;
};

/**
 * @param {string | null | undefined} resourceGroup
 * @param {string} resourceId
 * @param {{ signal?: AbortSignal }} [opts]
 * @returns {Promise<unknown | null>}
 */
export const fetchExplorerResourceTelemetry = async (resourceGroup, resourceId, opts = {}) => {
	if (!resourceId) {
		return null;
	}
	try {
		return resourceGroup
			? await explorerApi.getGroupedResource(resourceGroup, resourceId, opts)
			: await explorerApi.getTopLevelResource(resourceId, opts);
	} catch {
		return null;
	}
};

/**
 * @param {string | null | undefined} resourceGroup
 * @param {Array<{ resourceId: string }>} links
 * @param {{ signal?: AbortSignal }} [opts]
 * @returns {Promise<boolean>}
 */
export const areExplorerResourceLinksReady = async (resourceGroup, links, opts = {}) => {
	if (!links?.length) {
		return false;
	}
	const readyFlags = await Promise.all(
		links.map(async (link) => {
			const data = await fetchExplorerResourceTelemetry(resourceGroup, link.resourceId, opts);
			return isExplorerResourceDataReady(data);
		}),
	);
	return readyFlags.every(Boolean);
};
