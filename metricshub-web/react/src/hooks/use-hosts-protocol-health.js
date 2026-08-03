import * as React from "react";
import { fetchHostsProtocolHealth } from "../utils/host-protocol-health";

/**
 * Loads metricshub.host.up{protocol="…"} values for a set of hosts.
 *
 * @param {Array<{ hostId: string; resourceGroup?: string | null }>} hosts
 * @param {boolean} [enabled]
 * @returns {{ healthByHostId: Record<string, Record<string, 0 | 1 | null>>; loading: boolean }}
 */
export function useHostsProtocolHealth(hosts, enabled = true) {
	const [healthByHostId, setHealthByHostId] = React.useState({});
	const [loading, setLoading] = React.useState(false);

	const hostsKey = React.useMemo(
		() =>
			hosts
				.map((h) => `${h.resourceGroup || ""}:${h.hostId}`)
				.sort()
				.join("|"),
		[hosts],
	);

	const hostsRef = React.useRef(hosts);
	hostsRef.current = hosts;

	React.useEffect(() => {
		const hostsList = hostsRef.current;
		if (!enabled || hostsList.length === 0) {
			setHealthByHostId({});
			setLoading(false);
			return undefined;
		}

		const controller = new AbortController();
		setLoading(true);

		fetchHostsProtocolHealth(hostsList, { signal: controller.signal })
			.then((map) => {
				if (!controller.signal.aborted) {
					setHealthByHostId(map);
				}
			})
			.catch(() => {
				if (!controller.signal.aborted) {
					setHealthByHostId({});
				}
			})
			.finally(() => {
				if (!controller.signal.aborted) {
					setLoading(false);
				}
			});

		return () => controller.abort();
	}, [enabled, hostsKey]);

	return { healthByHostId, loading };
}
