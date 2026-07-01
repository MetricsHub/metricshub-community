import * as React from "react";
import { getExplorerResourceLinks } from "../components/hosts/host-explorer-links";
import { areExplorerResourceLinksReady } from "../utils/explorer-resource-readiness";

const DEFAULT_POLL_INTERVAL_MS = 10_000;

/**
 * Polls Explorer until every monitored host page for a guided-config resource has
 * telemetry (metrics, attributes, and/or monitors).
 *
 * @param {object} params
 * @param {string | null | undefined} params.resourceGroup
 * @param {string} params.hostId
 * @param {Record<string, unknown>} [params.hostConfig]
 * @param {boolean} [params.enabled]
 * @param {number} [params.pollIntervalMs]
 * @returns {{ ready: boolean; resourceLinks: import("../components/hosts/host-explorer-links").ExplorerResourceLink[] }}
 */
export function useExplorerResourceReadiness({
	resourceGroup,
	hostId,
	hostConfig,
	enabled = true,
	pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
}) {
	const resourceLinks = React.useMemo(
		() => getExplorerResourceLinks({ resourceGroup, hostId, hostConfig }),
		[hostConfig, hostId, resourceGroup],
	);

	const linksKey = React.useMemo(
		() => resourceLinks.map((link) => link.resourceId).join("\u0001"),
		[resourceLinks],
	);

	const [ready, setReady] = React.useState(false);

	React.useEffect(() => {
		if (!enabled || resourceLinks.length === 0) {
			setReady(false);
			return undefined;
		}

		const controller = new AbortController();
		let intervalId = null;
		let stopped = false;

		const check = async () => {
			const allReady = await areExplorerResourceLinksReady(resourceGroup, resourceLinks, {
				signal: controller.signal,
			});
			if (controller.signal.aborted || stopped) {
				return;
			}
			setReady(allReady);
			if (allReady && intervalId != null) {
				clearInterval(intervalId);
				intervalId = null;
			}
		};

		void check();
		intervalId = setInterval(() => void check(), pollIntervalMs);

		return () => {
			stopped = true;
			controller.abort();
			if (intervalId != null) {
				clearInterval(intervalId);
			}
		};
	}, [enabled, linksKey, pollIntervalMs, resourceGroup, resourceLinks]);

	return { ready, resourceLinks };
}
