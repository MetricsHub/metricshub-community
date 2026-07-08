import { objectToKvRows, kvRowsToObject, kvRowsToNumericObject } from "./KeyValueRowsEditor";
import {
	createEmptyResourceAdvancedState,
	WELL_KNOWN_HOST_ATTRIBUTE_KEYS,
} from "./resource-config-fields";

const parseCommaSeparatedList = (value) => {
	if (Array.isArray(value)) {
		return value
			.map(String)
			.filter((entry) => entry.trim())
			.join(", ");
	}
	if (value == null) {
		return "";
	}
	return String(value)
		.split(",")
		.map((entry) => entry.trim())
		.filter(Boolean)
		.join(", ");
};

const parseAlertingSystem = (hostConfig) => {
	const alerting = hostConfig?.alertingSystem || hostConfig?.alertingSystemConfig || {};
	return {
		alertingSystemDisable: Boolean(alerting.disable),
		alertingSystemProblemTemplate:
			alerting.problemTemplate == null ? "" : String(alerting.problemTemplate),
	};
};

const formatDurationValue = (value) => {
	if (value == null || value === "") {
		return "";
	}
	return String(value).trim();
};

/**
 * Maps a host configuration node into form resource advanced settings.
 *
 * @param {Record<string, unknown>} [hostConfig]
 * @returns {ReturnType<typeof createEmptyResourceAdvancedState>}
 */
export const parseResourceAdvancedFromConfig = (hostConfig = {}) => {
	const attrs = hostConfig?.attributes || {};
	const customAttributes = Object.fromEntries(
		Object.entries(attrs).filter(([key]) => !WELL_KNOWN_HOST_ATTRIBUTE_KEYS.has(key)),
	);

	const enableSelfMonitoring =
		hostConfig?.enableSelfMonitoring === true
			? "true"
			: hostConfig?.enableSelfMonitoring === false
				? "false"
				: "";

	return {
		...createEmptyResourceAdvancedState(),
		loggerLevel: hostConfig?.loggerLevel == null ? "" : String(hostConfig.loggerLevel),
		outputDirectory: hostConfig?.outputDirectory == null ? "" : String(hostConfig.outputDirectory),
		collectPeriod: formatDurationValue(hostConfig?.collectPeriod),
		discoveryCycle:
			hostConfig?.discoveryCycle == null || hostConfig.discoveryCycle === ""
				? ""
				: String(hostConfig.discoveryCycle),
		sequential: Boolean(hostConfig?.sequential),
		enableSelfMonitoring,
		logFileSourceDetails: Boolean(hostConfig?.logFileSourceDetails),
		resolveHostnameToFqdn: Boolean(hostConfig?.resolveHostnameToFqdn),
		monitorFilters: parseCommaSeparatedList(hostConfig?.monitorFilters),
		jobTimeout: formatDurationValue(hostConfig?.jobTimeout),
		stateSetCompression:
			hostConfig?.stateSetCompression == null ? "" : String(hostConfig.stateSetCompression),
		enrichments: parseCommaSeparatedList(hostConfig?.enrichments),
		customAttributeRows: objectToKvRows(customAttributes),
		metricRows: objectToKvRows(hostConfig?.metrics),
		...parseAlertingSystem(hostConfig),
	};
};

const setIfPresent = (target, key, value) => {
	if (value !== undefined && value !== null && value !== "") {
		target[key] = value;
	}
};

const parseListField = (value) => {
	const items = String(value ?? "")
		.split(",")
		.map((entry) => entry.trim())
		.filter(Boolean);
	return items.length > 0 ? items : undefined;
};

/**
 * Builds optional resource-level fields for the add-host API payload.
 *
 * @param {ReturnType<typeof createEmptyResourceAdvancedState>} resourceAdvanced
 * @returns {Record<string, unknown>}
 */
export const buildResourceAdvancedPayload = (resourceAdvanced = {}) => {
	/** @type {Record<string, unknown>} */
	const payload = {};

	setIfPresent(payload, "loggerLevel", String(resourceAdvanced.loggerLevel ?? "").trim());
	setIfPresent(payload, "outputDirectory", String(resourceAdvanced.outputDirectory ?? "").trim());
	setIfPresent(payload, "collectPeriod", String(resourceAdvanced.collectPeriod ?? "").trim());
	setIfPresent(payload, "jobTimeout", String(resourceAdvanced.jobTimeout ?? "").trim());
	setIfPresent(
		payload,
		"stateSetCompression",
		String(resourceAdvanced.stateSetCompression ?? "").trim(),
	);

	const discoveryCycle = String(resourceAdvanced.discoveryCycle ?? "").trim();
	if (discoveryCycle !== "") {
		const parsed = Number.parseInt(discoveryCycle, 10);
		if (Number.isFinite(parsed)) {
			payload.discoveryCycle = parsed;
		}
	}

	if (resourceAdvanced.sequential) {
		payload.sequential = true;
	}
	if (resourceAdvanced.logFileSourceDetails) {
		payload.logFileSourceDetails = true;
	}
	if (resourceAdvanced.resolveHostnameToFqdn) {
		payload.resolveHostnameToFqdn = true;
	}

	const enableSelfMonitoring = String(resourceAdvanced.enableSelfMonitoring ?? "").trim();
	if (enableSelfMonitoring === "true") {
		payload.enableSelfMonitoring = true;
	} else if (enableSelfMonitoring === "false") {
		payload.enableSelfMonitoring = false;
	}

	const monitorFilters = parseListField(resourceAdvanced.monitorFilters);
	if (monitorFilters) {
		payload.monitorFilters = monitorFilters;
	}

	const enrichments = parseListField(resourceAdvanced.enrichments);
	if (enrichments) {
		payload.enrichments = enrichments;
	}

	const customAttributes = kvRowsToObject(resourceAdvanced.customAttributeRows);
	if (Object.keys(customAttributes).length > 0) {
		payload.customAttributes = customAttributes;
	}

	const metrics = kvRowsToNumericObject(resourceAdvanced.metricRows);
	if (Object.keys(metrics).length > 0) {
		payload.metrics = metrics;
	}

	const alertingDisable = Boolean(resourceAdvanced.alertingSystemDisable);
	const problemTemplate = String(resourceAdvanced.alertingSystemProblemTemplate ?? "").trim();
	if (alertingDisable || problemTemplate) {
		/** @type {Record<string, unknown>} */
		const alertingSystem = {};
		if (alertingDisable) {
			alertingSystem.disable = true;
		}
		if (problemTemplate) {
			alertingSystem.problemTemplate = problemTemplate;
		}
		payload.alertingSystem = alertingSystem;
	}

	return payload;
};
