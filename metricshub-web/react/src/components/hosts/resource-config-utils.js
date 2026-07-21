import { objectToKvRows, kvRowsToObject, kvRowsToNumericObject } from "./KeyValueRowsEditor";
import {
	createEmptyResourceAdvancedState,
	DEFAULT_ENABLE_SELF_MONITORING,
	DEFAULT_ENRICHMENT,
	DEFAULT_STATE_SET_COMPRESSION,
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

/**
 * Reduces the configured enrichments list into the single-choice select value.
 * "BMC Helix" is stored in YAML as {@code bmchelix}; anything else falls back to "none".
 *
 * @param {unknown} enrichments
 * @returns {string}
 */
const parseEnrichmentChoice = (enrichments) => {
	const items = Array.isArray(enrichments)
		? enrichments.map((entry) => String(entry).trim().toLowerCase())
		: String(enrichments ?? "")
				.split(",")
				.map((entry) => entry.trim().toLowerCase())
				.filter(Boolean);
	return items.includes("bmchelix") ? "bmchelix" : DEFAULT_ENRICHMENT;
};

const formatDurationValue = (value) => {
	if (value == null || value === "") {
		return "";
	}
	return String(value).trim();
};

/**
 * Inheritance-aware advanced keys gated by "Apply advanced options from the parent".
 * Kept in sync with the backend's RESOURCE_GROUP_MANAGED_KEYS. Custom attributes and
 * metrics are deliberately excluded — they are the resource's own data, not inherited.
 */
const MANAGED_ADVANCED_KEYS = [
	"loggerLevel",
	"outputDirectory",
	"collectPeriod",
	"discoveryCycle",
	"jobTimeout",
	"stateSetCompression",
	"sequential",
	"enableSelfMonitoring",
	"resolveHostnameToFqdn",
	"monitorFilters",
	"enrichments",
];

/**
 * @param {Record<string, unknown>} [config]
 * @returns {boolean} true when the config explicitly sets any inheritance-aware key
 */
const hasAnyManagedAdvancedKey = (config = {}) =>
	MANAGED_ADVANCED_KEYS.some((key) => {
		const value = config?.[key];
		if (value == null || value === "") {
			return false;
		}
		return !(Array.isArray(value) && value.length === 0);
	});

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
				: DEFAULT_ENABLE_SELF_MONITORING;

	// The resource's OWN logger level is the single source of truth for the log section:
	//   ""    → no explicit level on the resource; it inherits the effective default
	//           (resource group → agent), which is what "Enable Debug" is derived from.
	//   "off" → logging explicitly disabled on this resource.
	//   any level → explicitly set on this resource.
	// Whether the "Enable Debug" switch shows on/off is derived in the UI from the
	// effective level (own → inherited → built-in), so it round-trips through a save.
	const hasLoggerLevel = hostConfig?.loggerLevel != null && hostConfig.loggerLevel !== "";
	const hasOutputDirectory =
		hostConfig?.outputDirectory != null && hostConfig.outputDirectory !== "";

	return {
		...createEmptyResourceAdvancedState(),
		// Inherit everything when the config carries none of the inheritance-aware keys;
		// as soon as one override is present the form opens with inheritance turned off.
		inheritAdvanced: !hasAnyManagedAdvancedKey(hostConfig),
		loggerLevel: hasLoggerLevel ? String(hostConfig.loggerLevel).trim() : "",
		outputDirectory: hasOutputDirectory ? String(hostConfig.outputDirectory) : "",
		collectPeriod: formatDurationValue(hostConfig?.collectPeriod),
		discoveryCycle:
			hostConfig?.discoveryCycle == null || hostConfig.discoveryCycle === ""
				? ""
				: String(hostConfig.discoveryCycle),
		sequential: Boolean(hostConfig?.sequential),
		enableSelfMonitoring,
		resolveHostnameToFqdn: Boolean(hostConfig?.resolveHostnameToFqdn),
		monitorFilters: parseCommaSeparatedList(hostConfig?.monitorFilters),
		jobTimeout: formatDurationValue(hostConfig?.jobTimeout),
		stateSetCompression:
			hostConfig?.stateSetCompression == null || hostConfig.stateSetCompression === ""
				? DEFAULT_STATE_SET_COMPRESSION
				: String(hostConfig.stateSetCompression),
		enrichments: parseEnrichmentChoice(hostConfig?.enrichments),
		customAttributeRows: objectToKvRows(customAttributes),
		metricRows: objectToKvRows(hostConfig?.metrics),
	};
};

const setIfPresent = (target, key, value) => {
	if (value !== undefined && value !== null && value !== "") {
		target[key] = value;
	}
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

	// While "Apply advanced options from the parent" is on, none of the inheritance-aware
	// keys are written — the resource/group inherits them entirely. Only the resource's own
	// custom attributes / metrics (handled below) are still emitted.
	if (!resourceAdvanced.inheritAdvanced) {
		// The resource's own logger level is written verbatim when set (""  means "inherit",
		// so it is omitted; "off" is written explicitly so a disabled resource round-trips).
		// The output directory only applies while logging is on (level is not "off").
		const loggerLevel = String(resourceAdvanced.loggerLevel ?? "").trim();
		if (loggerLevel !== "") {
			payload.loggerLevel = loggerLevel;
		}
		if (loggerLevel.toLowerCase() !== "off") {
			setIfPresent(
				payload,
				"outputDirectory",
				String(resourceAdvanced.outputDirectory ?? "").trim(),
			);
		}

		setIfPresent(payload, "collectPeriod", String(resourceAdvanced.collectPeriod ?? "").trim());
		setIfPresent(payload, "jobTimeout", String(resourceAdvanced.jobTimeout ?? "").trim());

		// Only persist a non-default state-set compression.
		const stateSetCompression = String(resourceAdvanced.stateSetCompression ?? "").trim();
		if (stateSetCompression !== "" && stateSetCompression !== DEFAULT_STATE_SET_COMPRESSION) {
			payload.stateSetCompression = stateSetCompression;
		}

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
		if (resourceAdvanced.resolveHostnameToFqdn) {
			payload.resolveHostnameToFqdn = true;
		}

		// Self monitoring is written only when it differs from the built-in default.
		const enableSelfMonitoring = String(resourceAdvanced.enableSelfMonitoring ?? "").trim();
		if (
			(enableSelfMonitoring === "true" || enableSelfMonitoring === "false") &&
			enableSelfMonitoring !== DEFAULT_ENABLE_SELF_MONITORING
		) {
			payload.enableSelfMonitoring = enableSelfMonitoring === "true";
		}

		const monitorFilters = String(resourceAdvanced.monitorFilters ?? "")
			.split(",")
			.map((entry) => entry.trim())
			.filter(Boolean);
		if (monitorFilters.length > 0) {
			payload.monitorFilters = monitorFilters;
		}

		if (String(resourceAdvanced.enrichments ?? "").trim() === "bmchelix") {
			payload.enrichments = ["bmchelix"];
		}
	}

	const customAttributes = kvRowsToObject(resourceAdvanced.customAttributeRows);
	if (Object.keys(customAttributes).length > 0) {
		payload.customAttributes = customAttributes;
	}

	const metrics = kvRowsToNumericObject(resourceAdvanced.metricRows);
	if (Object.keys(metrics).length > 0) {
		payload.metrics = metrics;
	}

	return payload;
};

/**
 * Advanced options that apply to a resource group: the inheritance-aware settings
 * only, without the custom attributes / metrics (resource groups manage those as
 * their own top-level sections).
 *
 * @param {ReturnType<typeof createEmptyResourceAdvancedState>} [resourceAdvanced]
 * @returns {Record<string, unknown>}
 */
export const buildResourceGroupAdvancedPayload = (resourceAdvanced = {}) => {
	// eslint-disable-next-line no-unused-vars
	const { customAttributes, metrics, ...resourceOptions } =
		buildResourceAdvancedPayload(resourceAdvanced);
	return resourceOptions;
};
