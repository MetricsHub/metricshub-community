import { compareLocale } from "../../utils/alphabetic-sort";
import { encryptWizardProtocolPasswords } from "../../utils/password-encrypt";

/** Common MetricsHub protocol keys used in host filters (A–Z). */
export const KNOWN_PROTOCOLS = [
	"http",
	"ipmi",
	"jdbc",
	"jmx",
	"oscommand",
	"ping",
	"snmp",
	"snmpv3",
	"ssh",
	"wbem",
	"winrm",
	"wmi",
];

/**
 * Builds resource summary statistics from a UI configuration snapshot.
 *
 * @param {{ resources?: Record<string, unknown>; resourceGroups?: Record<string, unknown> }} snapshot
 * @returns {object}
 */
export const summarizeHostsSnapshot = (snapshot) => {
	const resourceGroups = snapshot?.resourceGroups || {};
	const standaloneResources = snapshot?.resources || {};

	const groups = Object.entries(resourceGroups)
		.map(([name, node]) => {
			const resources = getGroupResources(node);
			const hosts = Object.entries(resources);
			const protocols = collectProtocolsInGroup(resources);
			/** @type {Record<string, number>} */
			const hostTypes = {};
			hosts.forEach(([, hostConfig]) => {
				const t = String(hostConfig?.attributes?.["host.type"] || "other");
				hostTypes[t] = (hostTypes[t] || 0) + 1;
			});
			return {
				name,
				node,
				hostCount: hosts.length,
				protocols,
				hostTypes,
				attributeCount: Object.keys(node?.attributes || {}).length,
			};
		})
		.sort((a, b) => compareLocale(a.name, b.name));

	const standaloneHosts = Object.entries(standaloneResources)
		.map(([hostId, hostConfig]) => ({
			hostId,
			hostConfig,
			displayName: getHostDisplayName(hostId, hostConfig),
			protocols: getHostProtocolNames(hostConfig),
			hostType: hostConfig?.attributes?.["host.type"],
		}))
		.sort((a, b) => compareLocale(a.displayName, b.displayName));

	const groupedHostCount = groups.reduce((sum, g) => sum + g.hostCount, 0);

	return {
		groups,
		standaloneHosts,
		groupCount: groups.length,
		groupedHostCount,
		standaloneCount: standaloneHosts.length,
		totalHosts: groupedHostCount + standaloneHosts.length,
		allProtocols: collectProtocolsInGroup({
			...standaloneResources,
			...Object.fromEntries(groups.flatMap((g) => Object.entries(getGroupResources(g.node)))),
		}),
	};
};

/**
 * @param {Record<string, unknown> | undefined} groupNode
 * @returns {Record<string, { attributes?: Record<string, unknown>; protocols?: Record<string, unknown> }>}
 */
export const getGroupResources = (groupNode) => {
	const resources = groupNode?.resources;
	if (!resources || typeof resources !== "object") {
		return {};
	}
	return resources;
};

/**
 * @param {Record<string, unknown>} hostConfig
 * @returns {string[]}
 */
export const getHostProtocolNames = (hostConfig) => {
	const protocols = hostConfig?.protocols;
	if (!protocols || typeof protocols !== "object") {
		return [];
	}
	return Object.keys(protocols).sort(compareLocale);
};

/**
 * @param {unknown} hostName
 * @returns {string[]}
 */
export const getHostNames = (hostName) => {
	const rawNames = Array.isArray(hostName)
		? hostName
		: String(hostName ?? "")
				.split(/[;,]/)
				.map((name) => name.trim());
	const seen = new Set();
	return rawNames
		.map((name) => String(name ?? "").trim())
		.filter(Boolean)
		.filter((name) => {
			const key = name.toLowerCase();
			if (seen.has(key)) {
				return false;
			}
			seen.add(key);
			return true;
		})
		.sort(compareLocale);
};

/**
 * @param {unknown} hostName
 * @returns {string | string[]}
 */
export const normalizeHostNameValue = (hostName) => {
	const names = getHostNames(hostName);
	if (names.length > 1) {
		return names;
	}
	return names[0] || "";
};

/**
 * @param {Record<string, unknown>} hostConfig
 * @returns {boolean}
 */
export const isMultiHostConfig = (hostConfig) =>
	getHostNames(hostConfig?.attributes?.["host.name"]).length > 1;

/**
 * Per-(resource × hostname) derived IDs used by Explorer and protocol health:
 * `${hostId}-${index+1}-${hostname}`.
 *
 * @param {string} hostId
 * @param {string[]} hostNames
 * @returns {string[]}
 */
export const buildMultiHostDerivedIds = (hostId, hostNames) =>
	hostNames.map((name, index) => `${hostId}-${index + 1}-${name}`);

/**
 * Sorts [hostId, hostConfig] tuples by display name (host.name, else host id).
 *
 * @param {[string, Record<string, unknown>][]} entries
 * @returns {[string, Record<string, unknown>][]}
 */
export const sortHostEntryTuples = (entries) =>
	[...entries].sort((a, b) =>
		compareLocale(getHostDisplayName(a[0], a[1]), getHostDisplayName(b[0], b[1])),
	);

/**
 * Collects all protocol names present in a group's hosts.
 *
 * @param {Record<string, unknown>} resources
 * @returns {string[]}
 */
export const collectProtocolsInGroup = (resources) => {
	const names = new Set();
	Object.values(resources || {}).forEach((host) => {
		getHostProtocolNames(host).forEach((p) => names.add(p));
	});
	return [
		...KNOWN_PROTOCOLS.filter((p) => names.has(p)),
		...[...names].filter((p) => !KNOWN_PROTOCOLS.includes(p)),
	].sort(compareLocale);
};

/**
 * @param {string} hostId
 * @param {Record<string, unknown>} hostConfig
 * @returns {string}
 */
export const getHostDisplayName = (hostId, hostConfig) => {
	const attrs = hostConfig?.attributes;
	if (attrs && typeof attrs === "object" && attrs["host.name"]) {
		const hostNames = getHostNames(attrs["host.name"]);
		if (hostNames.length > 0) {
			return hostNames.join(", ");
		}
	}
	return hostId;
};

/**
 * @param {string} hostId
 * @param {Record<string, unknown>} hostConfig
 * @param {string} searchQuery
 * @param {string} protocolFilter
 * @returns {boolean}
 */
export const hostMatchesFilters = (hostId, hostConfig, searchQuery, protocolFilter) => {
	if (protocolFilter && protocolFilter !== "all") {
		const protocols = getHostProtocolNames(hostConfig);
		if (!protocols.includes(protocolFilter)) {
			return false;
		}
	}

	const q = searchQuery.trim().toLowerCase();
	if (!q) {
		return true;
	}

	const displayName = getHostDisplayName(hostId, hostConfig).toLowerCase();
	const hostType = String(hostConfig?.attributes?.["host.type"] || "").toLowerCase();
	const protocolStr = getHostProtocolNames(hostConfig).join(" ").toLowerCase();

	return (
		hostId.toLowerCase().includes(q) ||
		displayName.includes(q) ||
		hostType.includes(q) ||
		protocolStr.includes(q)
	);
};

/**
 * Flattens a nested object into key-value rows for display tables.
 *
 * @param {Record<string, unknown> | undefined} obj
 * @param {string} [prefix]
 * @returns {{ id: string; property: string; value: string }[]}
 */
export const flattenToRows = (obj, prefix = "") => {
	if (!obj || typeof obj !== "object") {
		return [];
	}
	const rows = [];
	Object.entries(obj).forEach(([key, value]) => {
		const path = prefix ? `${prefix}.${key}` : key;
		if (value !== null && typeof value === "object" && !Array.isArray(value)) {
			rows.push(...flattenToRows(value, path));
		} else {
			const displayValue = Array.isArray(value)
				? [...value].map(String).sort(compareLocale).join(", ")
				: value === undefined || value === null
					? ""
					: String(value);
			rows.push({
				id: path,
				property: path,
				value: displayValue,
			});
		}
	});
	return rows;
};

import {
	PROTOCOL_DEFAULTS,
	buildProtocolConfigFromForm,
	protocolConfigToForm,
} from "./protocol-definitions";
import {
	buildAdditionalConnectorsPayload,
	buildConnectorsPayload,
	parseAdditionalConnectorsFromConfig,
	parseConnectorDirectivesFromConfig,
	parseForcedConnectorIdsFromConfig,
} from "./connector-utils";

/**
 * Picks the primary protocol to edit when a host has several configured.
 *
 * @param {Record<string, unknown>} protocols
 * @returns {string}
 */
export const pickPrimaryProtocol = (protocols) => {
	const keys = Object.keys(protocols || {});
	if (keys.length === 0) {
		return "ssh";
	}
	const preferred = ["ssh", "wmi", "snmp", "http", "winrm", "wbem"];
	for (const p of preferred) {
		if (keys.includes(p)) {
			return p;
		}
	}
	return keys[0];
};

/**
 * Maps a host entry from snapshot data into wizard state.
 *
 * @param {string} hostId
 * @param {Record<string, unknown>} hostConfig
 * @param {string | null | undefined} resourceGroup
 * @returns {object}
 */
export const hostConfigToWizardState = (hostId, hostConfig, resourceGroup) => {
	const attrs = hostConfig?.attributes || {};
	const yamlProtocols = hostConfig?.protocols || {};
	const inGroup = Boolean(resourceGroup);

	/** @type {Record<string, Record<string, unknown>>} */
	const protocols = {};
	for (const [protocolId, config] of Object.entries(yamlProtocols)) {
		protocols[protocolId] = protocolConfigToForm(
			protocolId,
			config && typeof config === "object" ? config : PROTOCOL_DEFAULTS[protocolId] || {},
		);
	}
	const selectedProtocols = Object.keys(protocols);
	const additionalConnectors = parseAdditionalConnectorsFromConfig(
		hostConfig?.additionalConnectors,
	);
	const selectedVariableConnectorTemplates = [
		...new Set(
			Object.values(additionalConnectors)
				.map((entry) => String(entry?.uses ?? "").trim())
				.filter(Boolean),
		),
	].sort(compareLocale);

	return {
		hostId,
		originalHostId: hostId,
		targetType: inGroup ? "group" : "standalone",
		resourceGroup: inGroup ? resourceGroup : "",
		originalTargetType: inGroup ? "group" : "standalone",
		originalResourceGroup: inGroup ? resourceGroup : "",
		hostName: normalizeHostNameValue(attrs["host.name"] || ""),
		hostType: attrs["host.type"] ? String(attrs["host.type"]) : "",
		selectedProtocols,
		protocols,
		connectors: parseConnectorDirectivesFromConfig(hostConfig?.connectors),
		additionalConnectors,
		selectedVariableConnectorTemplates,
		configureVariableConnectors: selectedVariableConnectorTemplates.length > 0,
		compatibleConnectorIds: parseForcedConnectorIdsFromConfig(hostConfig?.connectors),
		connectorDetectionMode:
			parseConnectorDirectivesFromConfig(hostConfig?.connectors).length > 0 ||
			Object.keys(additionalConnectors).length > 0
				? "manual"
				: "automatic",
		furthestStep: 0,
	};
};

/**
 * Builds the API payload from wizard state (only currently selected protocols).
 *
 * @param {object} wizardState
 * @returns {object}
 */
export const buildHostPayloadFromWizard = (wizardState) => {
	const {
		hostId,
		targetType,
		resourceGroup,
		hostName,
		hostType,
		protocols: protocolsForm,
		selectedProtocols,
		connectors: connectorIds,
		additionalConnectors,
		connectorDetectionMode,
	} = wizardState;

	const selectedSet = new Set(
		Array.isArray(selectedProtocols) && selectedProtocols.length > 0
			? selectedProtocols.map(String)
			: Object.keys(protocolsForm || {}),
	);

	/** @type {Record<string, unknown>} */
	const protocols = {};
	for (const [protocolId, formValues] of Object.entries(protocolsForm || {})) {
		if (!selectedSet.has(protocolId)) {
			continue;
		}
		protocols[protocolId] = buildProtocolConfigFromForm(protocolId, formValues);
	}

	const payload = {
		hostId,
		resourceGroup: targetType === "group" ? resourceGroup : null,
		attributes: {
			"host.name": normalizeHostNameValue(hostName),
			"host.type": hostType,
		},
		protocols,
	};
	if (connectorDetectionMode === "manual" || connectorDetectionMode === "raw") {
		const connectors = buildConnectorsPayload(connectorIds);
		if (connectors.length > 0) {
			payload.connectors = connectors;
		}
	}
	if (connectorDetectionMode === "manual" || connectorDetectionMode === "raw") {
		const additional = buildAdditionalConnectorsPayload(additionalConnectors);
		if (Object.keys(additional).length > 0) {
			payload.additionalConnectors = additional;
		}
	}
	return payload;
};

/**
 * Builds the API payload, optionally encrypting plain-text protocol passwords first (on save).
 *
 * @param {object} wizardState
 * @param {{ encryptPasswords?: boolean }} [options]
 * @returns {Promise<object>}
 */
export const buildHostPayloadFromWizardAsync = async (wizardState, options = {}) => {
	const { encryptPasswords = false } = options;
	const state = encryptPasswords ? await encryptWizardProtocolPasswords(wizardState) : wizardState;
	return buildHostPayloadFromWizard(state);
};

/** @deprecated use hostConfigToWizardState */
export const hostConfigToFormValues = hostConfigToWizardState;
