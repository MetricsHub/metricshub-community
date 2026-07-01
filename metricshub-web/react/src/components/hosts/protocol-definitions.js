import { compareLocale } from "../../utils/alphabetic-sort";

/** @typedef {'text' | 'password' | 'number' | 'boolean' | 'select' | 'radio' | 'authChoice' | 'modeChoice'} FieldType */

/**
 * @typedef {object} AuthChoiceCredentialField
 * @property {string} fieldName
 * @property {string} fieldLabel
 * @property {'text' | 'password' | 'textarea'} fieldType
 * @property {boolean} [required]
 * @property {string} [placeholder]
 */

/**
 * @typedef {object} AuthChoiceOption
 * @property {string} value toggle value (e.g. password | privateKey | skipAuth)
 * @property {string} label toggle label
 * @property {string} [fieldName] single credential field (SSH password / private key)
 * @property {string} [fieldLabel]
 * @property {'text' | 'password' | 'textarea'} [fieldType]
 * @property {string} [placeholder]
 * @property {AuthChoiceCredentialField[]} [fields] multiple credential fields (IPMI username + password)
 * @property {boolean} [skipAuth] selects IPMI skip-authentication mode (no credential fields)
 */

/**
 * @typedef {object} ProtocolField
 * @property {string} name
 * @property {string} label
 * @property {FieldType} type
 * @property {boolean} [required]
 * @property {boolean} [advanced]
 * @property {string} [helperText]
 * @property {string} [helpTooltip]
 * @property {(values: Record<string, unknown>) => boolean} [showIf]
 * @property {{ value: string | number | boolean; label: string; clears?: string[] }[]} [options]
 * @property {AuthChoiceOption[]} [authOptions]
 */

/** Help tooltip for IPMI authentication mode. */
export const IPMI_AUTH_TOOLTIP = `IPMI-over-LAN supports two access modes:

• BMC credentials — sign in with a BMC username and password. Add a BMC key under Advanced options for two-key authentication.

• Open BMC — connect without credentials when the BMC allows unauthenticated access.`;

/** Help tooltip for SNMPv3 context name. */
export const SNMPV3_CONTEXT_NAME_TOOLTIP = `SNMPv3 context name identifies which logical SNMP agent to query on the device.

Leave blank to use the default context. Set it only when your agent or vendor documentation requires a specific context (for example multi-VRF routers or modular chassis).`;

/** Help tooltip for JDBC connection mode. */
export const JDBC_CONNECTION_MODE_TOOLTIP = `Choose how to reach the database:

• Connection URL — paste a full JDBC URL. Optionally pick a driver type below the URL.

• Database and port — provide the database name and port; MetricsHub builds the connection.`;

/** Canonical host.type keys aligned with {@link DeviceKind} detection. */
export const HOST_TYPE_LABELS = {
	aix: "Aix",
	hpux: "HP-UX",
	linux: "Linux",
	network: "Network",
	oob: "OOB",
	solaris: "Solaris",
	storage: "Storage",
	windows: "Windows",
};

export const HOST_TYPES = Object.keys(HOST_TYPE_LABELS).sort(compareLocale);

/**
 * @param {string} [hostType]
 * @returns {string}
 */
export const formatHostTypeLabel = (hostType = "") =>
	HOST_TYPE_LABELS[String(hostType ?? "").trim()] || String(hostType ?? "").trim() || "—";

/** Empty value for host.type until the user picks an option in the wizard. */
export const HOST_TYPE_UNSELECTED = "";

/** User-facing copy for the host.type field (wizard and forms). */
export const HOST_NAME_UI = {
	fieldLabel: "Hostname",
	attributeName: "host.name",
	fieldHelper: "Target hostname or IP used for collection.",
};

/** User-facing copy for the host.type field (wizard and forms). */
export const HOST_TYPE_UI = {
	fieldLabel: "Host Type",
	attributeName: "host.type",
	sectionTitle: "Resource Configuration",
	sectionDescription:
		"Host type determines which protocols apply. Each selected protocol adds a configuration step next.",
	fieldHelper: "Required to match connectors and protocols.",
};

/** Protocol ids offered only when host.type is windows. */
export const WINDOWS_ONLY_PROTOCOL_IDS = ["wmi", "winrm"];

const PROTOCOL_OPTIONS_SOURCE = [
	{ id: "ssh", label: "SSH" },
	{ id: "wmi", label: "WMI" },
	{ id: "winrm", label: "WinRM" },
	{ id: "wbem", label: "WBEM" },
	{ id: "http", label: "HTTP / HTTPS" },
	{ id: "snmp", label: "SNMP v1 / v2" },
	{ id: "snmpv3", label: "SNMP v3" },
	{ id: "ipmi", label: "IPMI" },
	{ id: "jdbc", label: "JDBC" },
	{ id: "jmx", label: "JMX" },
	{ id: "oscommand", label: "OS Command" },
	{ id: "ping", label: "Ping (ICMP)" },
];

/** Protocol catalog entries sorted A–Z by label. */
export const PROTOCOL_OPTIONS = [...PROTOCOL_OPTIONS_SOURCE].sort((a, b) =>
	compareLocale(a.label, b.label),
);

/** Default ports per transport for protocols where port depends on HTTP vs HTTPS. */
export const TRANSPORT_LINKED_PORT_DEFAULTS = {
	http: { HTTP: 80, HTTPS: 443 },
	wbem: { HTTP: 5988, HTTPS: 5989 },
	winrm: { HTTP: 5985, HTTPS: 5986 },
};

/**
 * @param {string} protocol
 * @returns {boolean}
 */
export const hasTransportLinkedPort = (protocol) =>
	Object.prototype.hasOwnProperty.call(TRANSPORT_LINKED_PORT_DEFAULTS, protocol);

/**
 * @param {string} protocol
 * @param {unknown} transport
 * @returns {number | null}
 */
export const getDefaultPortForTransport = (protocol, transport) => {
	const map = TRANSPORT_LINKED_PORT_DEFAULTS[protocol];
	if (!map) {
		return null;
	}
	const normalized = String(transport || "")
		.trim()
		.toUpperCase();
	if (normalized === "HTTP") {
		return map.HTTP;
	}
	if (normalized === "HTTPS") {
		return map.HTTPS;
	}
	return map.HTTPS ?? map.HTTP ?? null;
};

/**
 * @param {string} protocol
 * @param {unknown} transport
 * @returns {string | undefined}
 */
export const portDescriptionForTransport = (protocol, transport) => {
	const defaultPort = getDefaultPortForTransport(protocol, transport);
	if (defaultPort == null) {
		return undefined;
	}
	const normalized = String(transport || "")
		.trim()
		.toUpperCase();
	const transportLabel = normalized === "HTTP" ? "HTTP" : "HTTPS";
	return `Default for ${transportLabel}: ${defaultPort}. Change only for a custom port.`;
};

/**
 * Inline hint for protocol ports with a fixed engine default (JMX, SNMP, …).
 *
 * @param {string} protocol
 * @returns {string | undefined}
 */
export const portDescriptionForProtocol = (protocol) => {
	const defaultPort = PROTOCOL_DEFAULTS[protocol]?.port;
	if (defaultPort === undefined || defaultPort === null || defaultPort === "") {
		return undefined;
	}
	return `Default: ${defaultPort}.`;
};

/**
 * Whether switching transport should replace the port with the new transport default.
 * Updates when the port is blank or still matches the previous transport default.
 * Custom ports are left unchanged.
 *
 * @param {string} protocol
 * @param {unknown} previousTransport
 * @param {unknown} port
 * @returns {boolean}
 */
export const shouldAutoUpdatePortForTransport = (protocol, previousTransport, port) => {
	const portStr = String(port ?? "").trim();
	if (portStr === "") {
		return true;
	}
	const previousDefault = getDefaultPortForTransport(protocol, previousTransport);
	return previousDefault != null && portStr === String(previousDefault);
};

/**
 * @param {string} protocolId
 * @param {string} [hostType]
 * @returns {boolean}
 */
export const isProtocolAvailableForHostType = (protocolId, hostType = "") => {
	const type = String(hostType ?? "").trim();
	if (!type || type === HOST_TYPE_UNSELECTED) {
		return true;
	}
	if (WINDOWS_ONLY_PROTOCOL_IDS.includes(protocolId) && type !== "windows") {
		return false;
	}
	return true;
};

/**
 * @param {string} [hostType]
 * @returns {typeof PROTOCOL_OPTIONS}
 */
export const getProtocolOptionsForHostType = (hostType = "") =>
	PROTOCOL_OPTIONS.filter((p) => isProtocolAvailableForHostType(p.id, hostType));

/**
 * @param {string[]} [protocolIds]
 * @param {string} [hostType]
 * @returns {string[]}
 */
export const filterSelectedProtocolsForHostType = (protocolIds = [], hostType = "") => {
	const allowed = new Set(protocolIds.filter((id) => isProtocolAvailableForHostType(id, hostType)));
	return PROTOCOL_OPTIONS.filter((p) => allowed.has(p.id)).map((p) => p.id);
};

/** @type {Record<string, Record<string, unknown>>} */
export const PROTOCOL_DEFAULTS = {
	ssh: {
		port: 22,
		username: "",
		password: "",
		privateKey: "",
		_authMethod: "password",
		timeout: "2m",
		useSudo: false,
		sudoCommand: "sudo",
		useSudoCommands: "",
	},
	wmi: { username: "", password: "", timeout: 30, namespace: "" },
	winrm: {
		username: "",
		password: "",
		protocol: "HTTP",
		port: "",
		timeout: 30,
		namespace: "",
		authentications: "NTLM",
	},
	wbem: {
		username: "",
		password: "",
		protocol: "HTTPS",
		port: "",
		timeout: 30,
		namespace: "",
		vcenter: "",
	},
	http: { username: "", password: "", protocol: "HTTPS", port: "", timeout: 120 },
	snmp: { version: "2", community: "public", port: 161, timeout: 30, retryIntervals: "" },
	snmpv3: {
		username: "",
		password: "",
		privacy: "",
		privacyPassword: "",
		authType: "SHA",
		contextName: "",
		port: 161,
		timeout: 30,
		retryIntervals: "",
	},
	ipmi: {
		username: "",
		password: "",
		bmcKey: "",
		skipAuth: false,
		_authMethod: "credentials",
		timeout: 120,
	},
	jdbc: {
		_jdbcMode: "url",
		url: "",
		type: "",
		database: "",
		port: "",
		username: "",
		password: "",
		timeout: 30,
	},
	jmx: { username: "", password: "", port: 1099, timeout: 30 },
	oscommand: { useSudo: false, sudoCommand: "sudo", useSudoCommands: "", timeout: 30 },
	ping: { timeout: 5 },
};

/**
 * Engine default transport (WBEM: HTTPS, WinRM: HTTP).
 *
 * @param {string} protocol
 * @returns {"HTTP" | "HTTPS" | null}
 */
export const getDefaultTransportForProtocol = (protocol) => {
	const transport = PROTOCOL_DEFAULTS[protocol]?.protocol;
	if (!transport) {
		return null;
	}
	const normalized = String(transport).trim().toUpperCase();
	if (normalized === "HTTP" || normalized === "HTTPS") {
		return normalized;
	}
	return null;
};

/**
 * @param {string} protocol
 * @returns {string | undefined}
 */
export const transportDescriptionForProtocol = (protocol) => {
	const defaultTransport = getDefaultTransportForProtocol(protocol);
	if (!defaultTransport) {
		return undefined;
	}
	return `Default is ${defaultTransport}.`;
};

/** @type {ProtocolField} */
export const TIMEOUT_FIELD = {
	name: "timeout",
	label: "Timeout",
	type: "text",
	required: true,
};

/** Inline hint shown next to the Timeout label. */
export const TIMEOUT_FIELD_DESCRIPTION = "Seconds when no unit is given.";

/** Validation error when the value is empty, zero, or not a supported duration. */
export const TIMEOUT_FORMAT_HINT = "Enter a duration greater than 0.";

/**
 * Help tooltip for timeout fields (aligned with engine {@code TimeDeserializer}).
 * Plain numbers are seconds; units are combined largest to smallest: h, m, s, ms.
 */
export const TIMEOUT_FORMAT_TOOLTIP = `Plain numbers are treated as seconds.

Combine units from largest to smallest:
h (hour) · m (minute) · s (second) · ms (millisecond)

Examples: 30, 30s, 1m, 1m20s, 2h, 8000ms`;

/** Same pattern as {@code TimeDeserializer} in the engine (case-insensitive). */
const TIME_DURATION_PATTERN =
	/^\s*(?:(\d+)\s*(?:years?|yrs?|y))?\s*(?:(\d+)\s*(?:weeks?|wks?|w))?\s*(?:(\d+)\s*(?:days?|d))?\s*(?:(\d+)\s*(?:hours?|hrs?|h))?\s*(?:(\d+)\s*(?:minutes?|mins?|m))?\s*(?:(\d+)\s*(?:seconds?|secs?|s))?\s*(?:(\d+)\s*(?:milliseconds?|millisecs?|ms))?\s*$/i;

/** WinRM authentication select values (form); maps to YAML array via {@link winRmAuthenticationsFromForm}. */
export const WINRM_AUTH_NTLM = "NTLM";
export const WINRM_AUTH_KERBEROS = "KERBEROS";
export const WINRM_AUTH_BOTH = "NTLM,KERBEROS";

/**
 * @param {unknown} value YAML array or comma-separated string
 * @returns {string}
 */
export const winRmAuthenticationsToForm = (value) => {
	const parts = (Array.isArray(value) ? value : parseStringList(value) || [])
		.map((entry) => String(entry).trim().toUpperCase())
		.filter(Boolean);
	if (parts.length === 0) {
		return WINRM_AUTH_NTLM;
	}
	const hasNtlm = parts.includes("NTLM");
	const hasKerberos = parts.includes("KERBEROS");
	if (hasNtlm && hasKerberos) {
		return WINRM_AUTH_BOTH;
	}
	if (hasKerberos) {
		return WINRM_AUTH_KERBEROS;
	}
	return WINRM_AUTH_NTLM;
};

/**
 * @param {unknown} value form select value
 * @returns {string[]}
 */
export const winRmAuthenticationsFromForm = (value) => {
	const normalized = String(value ?? "")
		.trim()
		.toUpperCase()
		.replace(/\s+/g, "");
	if (normalized === WINRM_AUTH_BOTH || normalized === "NTLM,KERBEROS") {
		return ["NTLM", "KERBEROS"];
	}
	if (normalized === WINRM_AUTH_KERBEROS) {
		return ["KERBEROS"];
	}
	return ["NTLM"];
};

/** @type {Record<string, ProtocolField[]>} */
export const PROTOCOL_FIELDS = {
	ssh: [
		{
			name: "username",
			label: "Username",
			type: "text",
			required: true,
		},
		{
			name: "authentication",
			label: "Authentication",
			type: "authChoice",
			required: true,
			helperText: "Choose how to authenticate to the SSH server",
			authOptions: [
				{
					value: "password",
					label: "Password",
					fieldName: "password",
					fieldLabel: "Password",
					fieldType: "password",
				},
				{
					value: "privateKey",
					label: "Private Key",
					fieldName: "privateKey",
					fieldLabel: "Private Key",
					fieldType: "textarea",
					placeholder: "Paste PEM private key content",
				},
			],
		},
		{ name: "port", label: "Port", type: "text", required: true, helperText: "SSH port" },
		TIMEOUT_FIELD,
		{
			name: "useSudo",
			label: "Use sudo",
			type: "boolean",
			helperText: "Run commands with sudo privileges",
		},
		{
			name: "sudoCommand",
			label: "Sudo command",
			type: "text",
			helperText: "Sudo command (default: sudo)",
		},
		{
			name: "useSudoCommands",
			label: "Sudo commands (comma-separated)",
			type: "text",
			helperText: "Commands that require sudo",
		},
	],
	wmi: [
		{ name: "username", label: "Username", type: "text", required: true },
		{ name: "password", label: "Password", type: "password", required: true },
		TIMEOUT_FIELD,
		{ name: "namespace", label: "Force namespace", type: "text", advanced: true },
	],
	winrm: [
		{ name: "username", label: "Username", type: "text", required: true },
		{ name: "password", label: "Password", type: "password", required: true },
		{
			name: "protocol",
			label: "Transport",
			type: "select",
			required: true,
			options: [
				{ value: "HTTP", label: "HTTP" },
				{ value: "HTTPS", label: "HTTPS" },
			],
		},
		{
			name: "port",
			label: "Port",
			type: "text",
		},
		TIMEOUT_FIELD,
		{
			name: "authentications",
			label: "Authentication schemes",
			type: "select",
			advanced: true,
			helperText: "Default: NTLM.",
			options: [
				{ value: WINRM_AUTH_NTLM, label: "NTLM" },
				{ value: WINRM_AUTH_KERBEROS, label: "Kerberos" },
				{ value: WINRM_AUTH_BOTH, label: "NTLM and Kerberos" },
			],
		},
		{ name: "namespace", label: "Force namespace", type: "text", advanced: true },
	],
	wbem: [
		{ name: "username", label: "Username", type: "text", required: true },
		{ name: "password", label: "Password", type: "password", required: true },
		{
			name: "protocol",
			label: "Transport",
			type: "select",
			required: true,
			options: [
				{ value: "HTTP", label: "HTTP" },
				{ value: "HTTPS", label: "HTTPS" },
			],
		},
		{
			name: "port",
			label: "Port",
			type: "text",
		},
		TIMEOUT_FIELD,
		{ name: "namespace", label: "Force namespace", type: "text", advanced: true },
		{ name: "vcenter", label: "vCenter hostname", type: "text", advanced: true },
	],
	http: [
		{ name: "username", label: "Username", type: "text" },
		{ name: "password", label: "Password", type: "password" },
		{
			name: "protocol",
			label: "Transport",
			type: "select",
			required: true,
			options: [
				{ value: "HTTP", label: "HTTP" },
				{ value: "HTTPS", label: "HTTPS" },
			],
		},
		{
			name: "port",
			label: "Port",
			type: "text",
		},
		TIMEOUT_FIELD,
	],
	snmp: [
		{
			name: "version",
			label: "SNMP version",
			type: "select",
			required: true,
			options: [
				{ value: "1", label: "v1" },
				{ value: "2", label: "v2c" },
			],
		},
		{ name: "community", label: "Community", type: "password", required: true },
		{ name: "port", label: "Port", type: "text", required: true },
		TIMEOUT_FIELD,
		{
			name: "retryIntervals",
			label: "Retry intervals (ms, comma-separated)",
			advanced: true,
			type: "text",
		},
	],
	snmpv3: [
		{ name: "username", label: "Username", type: "text", required: true },
		{ name: "password", label: "Password", type: "password", required: true },
		{
			name: "authType",
			label: "Authentication",
			type: "select",
			options: [
				{ value: "SHA", label: "SHA" },
				{ value: "SHA256", label: "SHA256" },
				{ value: "SHA512", label: "SHA512" },
				{ value: "SHA384", label: "SHA384" },
				{ value: "SHA224", label: "SHA224" },
				{ value: "MD5", label: "MD5" },
				{ value: "NO_AUTH", label: "No auth" },
			],
		},
		{
			name: "privacy",
			label: "Privacy",
			type: "radio",
			options: [
				{ value: "", label: "None", clears: ["privacyPassword"] },
				{ value: "DES", label: "DES" },
				{ value: "AES", label: "AES" },
				{ value: "AES192", label: "AES192" },
				{ value: "AES256", label: "AES256" },
			],
		},
		{
			name: "privacyPassword",
			label: "Privacy password",
			type: "password",
			required: true,
			showIf: (values) => Boolean(String(values.privacy ?? "").trim()),
		},
		{
			name: "contextName",
			label: "Context name",
			type: "text",
			helperText: "Optional. Leave blank for the default SNMP context.",
			helpTooltip: SNMPV3_CONTEXT_NAME_TOOLTIP,
			advanced: true,
		},
		{ name: "port", label: "Port", type: "text", required: true },
		TIMEOUT_FIELD,
		{
			name: "retryIntervals",
			label: "Retry intervals (ms, comma-separated)",
			advanced: true,
			type: "text",
		},
	],
	ipmi: [
		{
			name: "authentication",
			label: "Access",
			type: "authChoice",
			helpTooltip: IPMI_AUTH_TOOLTIP,
			authOptions: [
				{
					value: "credentials",
					label: "BMC credentials",
					fields: [
						{
							fieldName: "username",
							fieldLabel: "Username",
							fieldType: "text",
							required: true,
						},
						{
							fieldName: "password",
							fieldLabel: "Password",
							fieldType: "password",
						},
					],
				},
				{
					value: "skipAuth",
					label: "Open BMC",
					skipAuth: true,
				},
			],
		},
		TIMEOUT_FIELD,
		{
			name: "bmcKey",
			label: "BMC key (hex)",
			type: "text",
			advanced: true,
			helperText: "Two-key authentication (hexadecimal)",
			showIf: (values) => !values.skipAuth,
		},
	],
	jdbc: [
		{
			name: "_jdbcMode",
			label: "Connection",
			type: "modeChoice",
			helpTooltip: JDBC_CONNECTION_MODE_TOOLTIP,
			options: [
				{ value: "url", label: "Connection URL", clears: ["database", "port"] },
				{ value: "manual", label: "Database and port", clears: ["url", "type"] },
			],
		},
		// URL mode fields
		{
			name: "url",
			label: "JDBC URL",
			type: "text",
			helperText: "Full JDBC connection URL",
			showIf: (v) => (v._jdbcMode || "url") !== "manual",
		},
		{
			name: "type",
			label: "Database type",
			type: "select",
			helperText: "Optional",
			showIf: (v) => (v._jdbcMode || "url") !== "manual",
			options: [
				{ value: "", label: "— None —" },
				{ value: "MySQL", label: "MySQL" },
				{ value: "PostgreSQL", label: "PostgreSQL" },
				{ value: "SQLServer", label: "SQL Server" },
				{ value: "Oracle", label: "Oracle" },
				{ value: "DB2", label: "DB2" },
				{ value: "H2", label: "H2" },
				{ value: "SQLite", label: "SQLite" },
			],
		},
		// Manual mode fields
		{
			name: "database",
			label: "Database name",
			type: "text",
			showIf: (v) => v._jdbcMode === "manual",
		},
		{ name: "port", label: "Port", type: "text", showIf: (v) => v._jdbcMode === "manual" },
		// Common fields
		{ name: "username", label: "Username", type: "text", required: true },
		{ name: "password", label: "Password", type: "password", required: true },
		TIMEOUT_FIELD,
	],
	jmx: [
		{ name: "username", label: "Username", type: "text" },
		{ name: "password", label: "Password", type: "password" },
		{ name: "port", label: "Port", type: "text", required: true },
		TIMEOUT_FIELD,
	],
	oscommand: [
		{ name: "useSudo", label: "Use sudo", type: "boolean" },
		{ name: "sudoCommand", label: "Sudo command", type: "text" },
		{
			name: "useSudoCommands",
			label: "Sudo commands (comma-separated)",
			type: "text",
		},
		TIMEOUT_FIELD,
	],
	ping: [TIMEOUT_FIELD],
};

const parseTimeoutToSeconds = (value) => {
	if (value === "" || value === null || value === undefined) {
		return undefined;
	}
	const str = String(value).trim();
	if (str === "") {
		return undefined;
	}
	if (/^\d+$/.test(str)) {
		const n = parseInt(str, 10);
		return Number.isFinite(n) && n > 0 ? n : undefined;
	}
	const match = str.match(TIME_DURATION_PATTERN);
	if (!match) {
		return undefined;
	}
	const years = match[1] ? parseInt(match[1], 10) : 0;
	const weeks = match[2] ? parseInt(match[2], 10) : 0;
	const days = match[3] ? parseInt(match[3], 10) : 0;
	const hours = match[4] ? parseInt(match[4], 10) : 0;
	const minutes = match[5] ? parseInt(match[5], 10) : 0;
	const seconds = match[6] ? parseInt(match[6], 10) : 0;
	const milliseconds = match[7] ? parseInt(match[7], 10) : 0;
	const total =
		years * 60 * 60 * 24 * 365 +
		weeks * 60 * 60 * 24 * 7 +
		days * 60 * 60 * 24 +
		hours * 60 * 60 +
		minutes * 60 +
		seconds +
		Math.floor(milliseconds / 1000);
	return total > 0 ? total : undefined;
};

export { parseTimeoutToSeconds };

const parseNumber = (value) => {
	if (value === "" || value === null || value === undefined) {
		return undefined;
	}
	const n = Number(value);
	return Number.isFinite(n) ? n : undefined;
};

const parseRetryIntervals = (value) => {
	if (!value || !String(value).trim()) {
		return undefined;
	}
	const parts = String(value)
		.split(",")
		.map((s) => parseInt(s.trim(), 10))
		.filter((n) => Number.isFinite(n));
	return parts.length ? parts : undefined;
};

const parseStringList = (value) => {
	if (!value || !String(value).trim()) {
		return undefined;
	}
	const parts = String(value)
		.split(",")
		.map((s) => s.trim())
		.filter(Boolean);
	return parts.length ? parts : undefined;
};

/**
 * @param {string} protocol
 * @param {Record<string, unknown>} values
 * @returns {Record<string, unknown>}
 */
export const buildProtocolConfigFromForm = (protocol, values) => {
	const raw = { ...values };
	/** @type {Record<string, unknown>} */
	const config = {};

	const setIfPresent = (key, val) => {
		if (val !== undefined && val !== null && val !== "") {
			config[key] = val;
		}
	};

	switch (protocol) {
		case "ssh": {
			setIfPresent("username", raw.username);
			const authMethod = resolveProtocolAuthMethod("ssh", raw);
			if (authMethod === "privateKey") {
				setIfPresent("privateKey", raw.privateKey);
			} else {
				setIfPresent("password", raw.password);
			}
			setIfPresent("port", parseNumber(raw.port));
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			if (raw.useSudo) {
				config.useSudo = true;
				setIfPresent("sudoCommand", raw.sudoCommand);
				{
					const cmds = parseStringList(raw.useSudoCommands);
					if (cmds) {
						config.useSudoCommands = cmds;
					}
				}
			}
			break;
		}
		case "wmi":
			setIfPresent("username", raw.username);
			setIfPresent("password", raw.password);
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			setIfPresent("namespace", raw.namespace);
			break;
		case "winrm":
			setIfPresent("username", raw.username);
			setIfPresent("password", raw.password);
			setIfPresent("protocol", raw.protocol);
			setIfPresent("port", parseNumber(raw.port));
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			setIfPresent("namespace", raw.namespace);
			{
				const auths = winRmAuthenticationsFromForm(raw.authentications);
				const isDefaultNtlm = auths.length === 1 && auths[0] === "NTLM";
				if (!isDefaultNtlm) {
					config.authentications = auths;
				}
			}
			break;
		case "wbem":
			setIfPresent("username", raw.username);
			setIfPresent("password", raw.password);
			setIfPresent("protocol", raw.protocol);
			setIfPresent("port", parseNumber(raw.port));
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			setIfPresent("namespace", raw.namespace);
			setIfPresent("vcenter", raw.vcenter);
			break;
		case "http": {
			const transport = String(raw.protocol ?? "HTTPS")
				.trim()
				.toUpperCase();
			config.https = transport !== "HTTP";
			setIfPresent("port", parseNumber(raw.port));
			setIfPresent("username", raw.username);
			setIfPresent("password", raw.password);
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			break;
		}
		case "snmp":
			setIfPresent("version", raw.version);
			setIfPresent("community", raw.community);
			setIfPresent("port", parseNumber(raw.port));
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			{
				const retries = parseRetryIntervals(raw.retryIntervals);
				if (retries) {
					config.retryIntervals = retries;
				}
			}
			break;
		case "snmpv3":
			setIfPresent("username", raw.username);
			setIfPresent("password", raw.password);
			setIfPresent("privacy", raw.privacy);
			if (String(raw.privacy ?? "").trim()) {
				setIfPresent("privacyPassword", raw.privacyPassword);
			}
			setIfPresent("authType", raw.authType);
			setIfPresent("contextName", raw.contextName);
			setIfPresent("port", parseNumber(raw.port));
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			{
				const retries = parseRetryIntervals(raw.retryIntervals);
				if (retries) {
					config.retryIntervals = retries;
				}
			}
			break;
		case "ipmi":
			if (raw.skipAuth) {
				config.skipAuth = true;
			} else {
				setIfPresent("username", raw.username);
				setIfPresent("password", raw.password);
				setIfPresent("bmcKey", raw.bmcKey);
			}
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			break;
		case "jdbc":
			setIfPresent("url", raw.url);
			setIfPresent("username", raw.username);
			setIfPresent("password", raw.password);
			setIfPresent("type", raw.type);
			setIfPresent("database", raw.database);
			setIfPresent("port", parseNumber(raw.port));
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			break;
		case "jmx":
			setIfPresent("port", parseNumber(raw.port));
			setIfPresent("username", raw.username);
			setIfPresent("password", raw.password);
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			break;
		case "oscommand":
			if (raw.useSudo) {
				config.useSudo = true;
				setIfPresent("sudoCommand", raw.sudoCommand);
				{
					const cmds = parseStringList(raw.useSudoCommands);
					if (cmds) {
						config.useSudoCommands = cmds;
					}
				}
			}
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			break;
		case "ping":
			setIfPresent("timeout", parseTimeoutToSeconds(raw.timeout));
			break;
		default:
			break;
	}

	return config;
};

/**
 * @param {string} protocol
 * @param {Record<string, unknown>} config
 * @returns {Record<string, unknown>}
 */
export const protocolConfigToForm = (protocol, config = {}) => {
	const defaults = PROTOCOL_DEFAULTS[protocol] || {};
	/** @type {Record<string, unknown>} */
	const form = { ...defaults };

	if (protocol === "jdbc") {
		// Infer UI mode from which fields are configured in YAML.
		const cfg = config || {};
		form._jdbcMode = cfg.database || cfg.port ? "manual" : "url";
	}

	if (protocol === "ssh") {
		const cfg = config || {};
		form._authMethod = String(cfg.privateKey ?? "").trim() ? "privateKey" : "password";
	}

	if (protocol === "ipmi") {
		form._authMethod = config?.skipAuth ? "skipAuth" : "credentials";
	}

	Object.entries(config || {}).forEach(([key, value]) => {
		if (value === undefined || value === null) {
			return;
		}
		if (key === "useSudoCommands" || key === "retryIntervals") {
			form[key] = Array.isArray(value) ? value.join(", ") : String(value);
		} else if (key === "authentications" && protocol === "winrm") {
			form[key] = winRmAuthenticationsToForm(value);
		} else if (key === "authentications") {
			form[key] = Array.isArray(value) ? value.join(", ") : String(value);
		} else if (typeof value === "boolean") {
			form[key] = value;
		} else {
			form[key] = value;
		}
	});

	if (protocol === "http") {
		if (config.https !== undefined) {
			form.protocol = config.https === false ? "HTTP" : "HTTPS";
		}
	}

	return form;
};

/** Same set as {@code NetworkHelper.TYPICAL_LOCALHOST_HOSTNAMES} in the engine. */
const TYPICAL_LOCALHOST_HOSTNAMES = new Set([
	"localhost",
	"127.0.0.1",
	"::1",
	"0:0:0:0:0:0:0:1",
	"0000:0000:0000:0000:0000:0000:0000:0001",
]);

/** Credential fields not required when the target host is localhost. */
const LOCALHOST_OPTIONAL_AUTH_FIELDS = new Set(["username", "password"]);

/**
 * Whether the host identifier or display name refers to the local machine.
 *
 * @param {string} [hostId]
 * @param {string} [hostName]
 * @returns {boolean}
 */
export const isLocalhostHost = (hostId, hostName) => {
	const candidates = [hostId, hostName];
	return candidates.some((candidate) => {
		if (candidate == null || String(candidate).trim() === "") {
			return true;
		}
		return TYPICAL_LOCALHOST_HOSTNAMES.has(String(candidate).trim().toLowerCase());
	});
};

/**
 * @param {string} fieldName
 * @returns {boolean}
 */
export const isAuthFieldOptionalOnLocalhost = (fieldName) =>
	LOCALHOST_OPTIONAL_AUTH_FIELDS.has(fieldName);

/**
 * Active authentication mode for protocols with an auth-choice UI.
 *
 * @param {string} protocol
 * @param {Record<string, unknown>} protocolConfig
 * @returns {string | null}
 */
export const resolveProtocolAuthMethod = (protocol, protocolConfig) => {
	if (protocol === "ssh") {
		const explicit = String(protocolConfig._authMethod ?? "").trim();
		if (explicit === "password" || explicit === "privateKey") {
			return explicit;
		}
		return String(protocolConfig.privateKey ?? "").trim() ? "privateKey" : "password";
	}
	if (protocol === "ipmi") {
		if (protocolConfig.skipAuth) {
			return "skipAuth";
		}
		const explicit = String(protocolConfig._authMethod ?? "").trim();
		return explicit || "credentials";
	}
	return null;
};

/**
 * @param {unknown} value
 * @param {{ required?: boolean; label?: string }} [options]
 * @returns {string | null}
 */
export const validateTimeoutValue = (value, { required = false, label = "Timeout" } = {}) => {
	const timeoutStr = String(value ?? "").trim();
	if (timeoutStr === "") {
		return required ? `${label} is required` : null;
	}
	const seconds = parseTimeoutToSeconds(timeoutStr);
	if (seconds === undefined || seconds <= 0) {
		return TIMEOUT_FORMAT_HINT;
	}
	return null;
};

export const MIN_PORT = 1;
export const MAX_PORT = 65535;
export const PORT_RANGE_HINT = `Must be between ${MIN_PORT} and ${MAX_PORT}.`;

/**
 * @param {unknown} value
 * @returns {string}
 */
export const sanitizePortInput = (value) => String(value ?? "").replace(/\D/g, "");

/**
 * @param {unknown} value
 * @param {{ required?: boolean; label?: string }} [options]
 * @returns {string | null}
 */
export const validatePortValue = (value, { required = false, label = "Port" } = {}) => {
	const portStr = String(value ?? "").trim();
	if (portStr === "") {
		return required ? `${label} is required` : null;
	}
	if (!/^\d+$/.test(portStr)) {
		return PORT_RANGE_HINT;
	}
	const portNum = Number(portStr);
	if (!Number.isInteger(portNum) || portNum < MIN_PORT || portNum > MAX_PORT) {
		return PORT_RANGE_HINT;
	}
	return null;
};

/**
 * Collects all validation errors for a protocol configuration form.
 *
 * @param {string} protocol
 * @param {Record<string, unknown>} protocolConfig
 * @param {{ hostId?: string; hostName?: string }} [options]
 * @returns {Record<string, string>}
 */
export const collectProtocolConfigErrors = (protocol, protocolConfig, options = {}) => {
	const isLocal = isLocalhostHost(options.hostId, options.hostName);
	const fields = PROTOCOL_FIELDS[protocol] || [];
	/** @type {Record<string, string>} */
	const errors = {};

	for (const field of fields) {
		if (field.showIf && !field.showIf(protocolConfig)) {
			continue;
		}
		if (field.type === "authChoice" || field.type === "modeChoice") {
			continue;
		}

		const val = protocolConfig[field.name];
		const isEmpty = val === undefined || val === null || String(val).trim() === "";

		if (field.name === "port") {
			const portError = validatePortValue(val, {
				required: Boolean(field.required),
				label: field.label,
			});
			if (portError) {
				errors.port = portError;
			}
			continue;
		}

		if (field.name === "timeout") {
			const timeoutError = validateTimeoutValue(val, {
				required: Boolean(field.required),
				label: field.label,
			});
			if (timeoutError) {
				errors.timeout = timeoutError;
			}
			continue;
		}

		if (!field.required) {
			continue;
		}
		if (isLocal && isAuthFieldOptionalOnLocalhost(field.name)) {
			continue;
		}
		if (isEmpty) {
			errors[field.name] = `${field.label} is required`;
		}
	}

	if (protocol === "ssh" && !isLocal) {
		const authMethod = resolveProtocolAuthMethod("ssh", protocolConfig);
		if (authMethod === "privateKey") {
			if (!String(protocolConfig.privateKey || "").trim()) {
				errors.privateKey = "Private key is required";
			}
		} else if (!String(protocolConfig.password || "").trim()) {
			errors.password = "Password is required";
		}
	}

	if (protocol === "ipmi" && !protocolConfig.skipAuth) {
		if (!String(protocolConfig.username || "").trim()) {
			errors.username = "Username is required";
		}
	}

	if (protocol === "jdbc") {
		const hasUrl = String(protocolConfig.url || "").trim();
		const hasType = String(protocolConfig.type || "").trim();
		if (!hasUrl && !hasType) {
			const message = "Provide a JDBC URL or a database type";
			errors.url = message;
			errors.type = message;
		}
	}

	return errors;
};

/**
 * @param {string} protocol
 * @param {Record<string, unknown>} protocolConfig
 * @param {{ hostId?: string; hostName?: string }} [options]
 * @returns {string | null}
 */
export const validateProtocolConfig = (protocol, protocolConfig, options = {}) => {
	const errors = collectProtocolConfigErrors(protocol, protocolConfig, options);
	const firstKey = Object.keys(errors)[0];
	return firstKey ? errors[firstKey] : null;
};

/**
 * Validates every protocol configured on a host.
 *
 * @param {Record<string, Record<string, unknown>>} protocolsForm
 * @param {{ hostId?: string; hostName?: string }} [options]
 * @returns {string | null}
 */
export const validateAllProtocols = (protocolsForm, options = {}) => {
	const ids = Object.keys(protocolsForm || {});
	if (ids.length === 0) {
		return "Add at least one monitoring protocol.";
	}
	for (const protocolId of ids) {
		const label = PROTOCOL_OPTIONS.find((p) => p.id === protocolId)?.label || protocolId;
		const message = validateProtocolConfig(protocolId, protocolsForm[protocolId], options);
		if (message) {
			return `${label}: ${message}`;
		}
	}
	return null;
};
