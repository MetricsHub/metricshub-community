import { getMetricValue } from "./metrics-helper";
import { explorerApi } from "../api/explorer";

const HOST_UP_METRIC_PREFIX = "metricshub.host.up";

/**
 * @param {string} metricKey
 * @returns {string | null}
 */
export const parseProtocolFromHostUpMetric = (metricKey) => {
	if (!metricKey?.startsWith(HOST_UP_METRIC_PREFIX)) {
		return null;
	}
	const match = metricKey.match(/protocol="([^"]+)"/);
	return match ? match[1] : null;
};

/**
 * @param {Record<string, unknown>} [metrics]
 * @returns {Record<string, 0 | 1 | null>}
 */
export const parseProtocolUpMetrics = (metrics = {}) => {
	/** @type {Record<string, 0 | 1 | null>} */
	const result = {};
	for (const [key, raw] of Object.entries(metrics || {})) {
		const protocol = parseProtocolFromHostUpMetric(key);
		if (!protocol) {
			continue;
		}
		const value = getMetricValue(raw);
		if (value === 1 || value === 1.0) {
			result[protocol] = 1;
		} else if (value === 0 || value === 0.0) {
			result[protocol] = 0;
		} else {
			result[protocol] = null;
		}
	}
	return result;
};

/**
 * @param {string} hostId
 * @param {string | null | undefined} resourceGroup
 * @returns {Promise<Record<string, 0 | 1 | null>>}
 */
export async function fetchHostProtocolHealth(hostId, resourceGroup = null) {
	try {
		const data = resourceGroup
			? await explorerApi.getGroupedResource(resourceGroup, hostId)
			: await explorerApi.getTopLevelResource(hostId);
		return parseProtocolUpMetrics(data?.metrics);
	} catch {
		return {};
	}
}

/**
 * @param {Array<{ hostId: string; resourceGroup?: string | null }>} hosts
 * @param {{ signal?: AbortSignal }} [opts]
 * @returns {Promise<Record<string, Record<string, 0 | 1 | null>>>}
 */
export async function fetchHostsProtocolHealth(hosts, opts = {}) {
	const { signal } = opts;
	/** @type {Record<string, Record<string, 0 | 1 | null>>} */
	const map = {};
	const unique = hosts.filter((h) => h?.hostId);

	await Promise.all(
		unique.map(async ({ hostId, resourceGroup }) => {
			if (signal?.aborted) {
				return;
			}
			map[hostId] = await fetchHostProtocolHealth(hostId, resourceGroup || null);
		}),
	);

	return map;
}
