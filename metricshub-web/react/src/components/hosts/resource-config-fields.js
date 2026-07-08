/** Well-known host attributes managed outside Advanced options. */
export const WELL_KNOWN_HOST_ATTRIBUTE_KEYS = new Set(["host.name", "host.type"]);

export const LOGGER_LEVEL_OPTIONS = [
	{ value: "", label: "— Default —" },
	{ value: "all", label: "All" },
	{ value: "trace", label: "Trace" },
	{ value: "debug", label: "Debug" },
	{ value: "info", label: "Info" },
	{ value: "warn", label: "Warn" },
	{ value: "error", label: "Error" },
	{ value: "fatal", label: "Fatal" },
];

export const STATE_SET_COMPRESSION_OPTIONS = [
	{ value: "", label: "— Default —" },
	{ value: "none", label: "None" },
	{ value: "suppressZeros", label: "Suppress zeros" },
];

export const ENABLE_SELF_MONITORING_OPTIONS = [
	{ value: "", label: "— Default —" },
	{ value: "true", label: "Enabled" },
	{ value: "false", label: "Disabled" },
];

/** Default empty resource advanced settings in the host config form. */
export const createEmptyResourceAdvancedState = () => ({
	loggerLevel: "",
	outputDirectory: "",
	collectPeriod: "",
	discoveryCycle: "",
	sequential: false,
	enableSelfMonitoring: "",
	logFileSourceDetails: false,
	resolveHostnameToFqdn: false,
	monitorFilters: "",
	jobTimeout: "",
	stateSetCompression: "",
	alertingSystemDisable: false,
	alertingSystemProblemTemplate: "",
	enrichments: "",
	customAttributeRows: [],
	metricRows: [],
});
