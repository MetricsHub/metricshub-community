/** Well-known host attributes managed outside Advanced options. */
export const WELL_KNOWN_HOST_ATTRIBUTE_KEYS = new Set(["host.name", "host.type"]);

/** MetricsHub built-in defaults, pre-selected directly instead of a sentinel "— Default —" option. */
export const DEFAULT_LOGGER_LEVEL = "error";
export const DEFAULT_STATE_SET_COMPRESSION = "suppressZeros";
export const DEFAULT_ENABLE_SELF_MONITORING = "true";
export const DEFAULT_ENRICHMENT = "none";

export const LOGGER_LEVEL_OPTIONS = [
	{ value: "all", label: "All" },
	{ value: "trace", label: "Trace" },
	{ value: "debug", label: "Debug" },
	{ value: "info", label: "Info" },
	{ value: "warn", label: "Warn" },
	{ value: "error", label: "Error" },
	{ value: "fatal", label: "Fatal" },
];

export const STATE_SET_COMPRESSION_OPTIONS = [
	{ value: "none", label: "None" },
	{ value: "suppressZeros", label: "Suppress zeros" },
];

export const ENABLE_SELF_MONITORING_OPTIONS = [
	{ value: "true", label: "Enabled" },
	{ value: "false", label: "Disabled" },
];

/** Enrichment choices. The YAML value differs from the display label (e.g. "BMC Helix" → "bmchelix"). */
export const ENRICHMENT_OPTIONS = [
	{ value: "none", label: "None" },
	{ value: "bmchelix", label: "BMC Helix" },
];

/** Default empty resource advanced settings in the host config form. */
export const createEmptyResourceAdvancedState = () => ({
	// When true, every inheritance-aware option below is inherited from the parent
	// (resource group → agent) and nothing is written to the config. Turning it off
	// unlocks the fields; only values that differ from the inherited ones are persisted.
	inheritAdvanced: true,
	// "" = inherit the effective logger level (resource group → agent default);
	// "off" = logging explicitly disabled. The "Enable Debug" switch is derived from
	// the effective level, so a new resource inherits (and shows) the default level.
	loggerLevel: "",
	outputDirectory: "",
	collectPeriod: "",
	discoveryCycle: "",
	sequential: false,
	enableSelfMonitoring: DEFAULT_ENABLE_SELF_MONITORING,
	resolveHostnameToFqdn: false,
	monitorFilters: "",
	jobTimeout: "",
	stateSetCompression: DEFAULT_STATE_SET_COMPRESSION,
	enrichments: DEFAULT_ENRICHMENT,
	customAttributeRows: [],
	metricRows: [],
});
