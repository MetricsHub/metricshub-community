import { uiConfigApi } from "../../api/ui-config";
import { validateHostNameValue } from "../../utils/host-names";
import { buildProtocolConfigFromForm, collectProtocolConfigErrors } from "./protocol-definitions";

/**
 * @param {unknown} error
 * @returns {boolean}
 */
export const isAbortError = (error) =>
	error?.code === "ERR_CANCELED" || error?.name === "CanceledError" || error?.name === "AbortError";

/**
 * Runs an on-demand protocol connection check for a single hostname.
 *
 * Validates the protocol form values first, then posts the check with the chosen
 * hostname pinned in the protocol config (a multi-host config lists one hostname
 * per host.name entry; the test targets a single host).
 *
 * @param {object} options
 * @param {string} options.protocol protocol id (e.g. ssh)
 * @param {Record<string, unknown>} options.protocolValues current protocol form values
 * @param {string} options.hostname hostname to contact
 * @param {string | string[]} [options.hostName] resource host.name form value
 * @param {AbortSignal} [options.signal]
 * @returns {Promise<{ severity: "success" | "error" | "warning", message: string } | null>}
 *          the display result, or {@code null} when the request was aborted
 */
export const runProtocolCheck = async ({
	protocol,
	protocolValues,
	hostname,
	hostName,
	signal,
}) => {
	const hostnameError = validateHostNameValue(hostname, { required: true });
	if (hostnameError) return { severity: "warning", message: hostnameError };
	const validationErrors = collectProtocolConfigErrors(protocol, protocolValues, {
		hostName,
	});
	const firstError = Object.values(validationErrors)[0];
	if (firstError) {
		return { severity: "warning", message: firstError };
	}

	try {
		const response = await uiConfigApi.checkProtocol(
			{
				hostname,
				protocol,
				protocolConfig: {
					[protocol]: { ...buildProtocolConfigFromForm(protocol, protocolValues), hostname },
				},
			},
			{ signal },
		);

		if (signal?.aborted) {
			return null;
		}

		if (response.timedOut) {
			return { severity: "warning", message: "Connection timed out." };
		}

		if (response.errorMessage) {
			return { severity: "error", message: response.errorMessage };
		}

		if (response.hostUp === 1) {
			const suffix =
				response.responseTimeMs != null ? ` (${Math.round(response.responseTimeMs)} ms)` : "";
			return { severity: "success", message: `Connection successful${suffix}` };
		}

		return { severity: "error", message: "Protocol test failed." };
	} catch (error) {
		if (signal?.aborted || isAbortError(error)) {
			return null;
		}
		return { severity: "error", message: error?.message || "Protocol test failed." };
	}
};
