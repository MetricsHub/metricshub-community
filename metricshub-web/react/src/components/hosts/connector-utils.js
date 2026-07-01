import { uiConfigApi } from "../../api/ui-config";
import { compareLocale, sortConnectorSummaries } from "../../utils/alphabetic-sort";
import { HOST_TYPE_LABELS, formatHostTypeLabel } from "./protocol-definitions";
import { getOrderedSelectedProtocols } from "./host-wizard-steps";

/** @type {Promise<object[]> | null} */
let connectorCatalogPromise = null;

/** @typedef {'force' | 'exclude' | 'select' | 'include-tag' | 'exclude-tag'} ConnectorDirectiveKind */

/**
 * @param {string} directive
 * @returns {{ kind: ConnectorDirectiveKind; value: string; raw: string }}
 */
export const parseConnectorDirective = (directive) => {
	const raw = String(directive ?? "").trim();
	if (!raw) {
		return { kind: "select", value: "", raw: "" };
	}
	if (raw.startsWith("!#")) {
		return { kind: "exclude-tag", value: raw.slice(2).trim(), raw };
	}
	if (raw.startsWith("#")) {
		return { kind: "include-tag", value: raw.slice(1).trim(), raw };
	}
	if (raw.startsWith("+")) {
		return { kind: "force", value: raw.slice(1).trim(), raw };
	}
	if (raw.startsWith("!")) {
		return { kind: "exclude", value: raw.slice(1).trim(), raw };
	}
	return { kind: "select", value: raw, raw };
};

/**
 * @param {ConnectorDirectiveKind} kind
 * @param {string} value
 * @returns {string}
 */
export const formatConnectorDirective = (kind, value) => {
	const normalized = String(value ?? "").trim();
	if (!normalized) {
		return "";
	}
	switch (kind) {
		case "force":
			return `+${normalized}`;
		case "exclude":
			return `!${normalized}`;
		case "include-tag":
			return `#${normalized}`;
		case "exclude-tag":
			return `!#${normalized}`;
		case "select":
		default:
			return normalized;
	}
};

/**
 * Parses connector directives from YAML/config preserving prefixes.
 *
 * @param {unknown} connectors
 * @returns {string[]}
 */
export const parseConnectorDirectivesFromConfig = (connectors) => {
	if (!connectors) {
		return [];
	}
	const list = Array.isArray(connectors) ? connectors : [connectors];
	return list
		.map((entry) => String(entry ?? "").trim())
		.filter(Boolean)
		.sort(compareLocale);
};

/**
 * Parses forced connector IDs ({@code +id}) from YAML/config.
 *
 * @param {unknown} connectors
 * @returns {string[]}
 */
export const parseForcedConnectorIdsFromConfig = (connectors) =>
	parseConnectorDirectivesFromConfig(connectors)
		.map((entry) => parseConnectorDirective(entry))
		.filter((parsed) => (parsed.kind === "force" || parsed.kind === "select") && parsed.value)
		.map((parsed) => parsed.value)
		.filter(Boolean)
		.sort(compareLocale);

/**
 * @deprecated use {@link parseForcedConnectorIdsFromConfig} or {@link parseConnectorDirectivesFromConfig}
 * @param {unknown} connectors
 * @returns {string[]}
 */
export const parseConnectorIdsFromConfig = parseForcedConnectorIdsFromConfig;

/**
 * @param {string[]} directives
 * @returns {string[]}
 */
export const buildConnectorsPayload = (directives) => {
	const unique = [
		...new Set((directives || []).map((entry) => String(entry).trim()).filter(Boolean)),
	].sort(compareLocale);
	return unique;
};

/**
 * @param {string[]} directives
 * @param {string} connectorId
 * @returns {ConnectorDirectiveKind | 'none'}
 */
export const getConnectorSelectionKind = (directives, connectorId) => {
	const target = String(connectorId ?? "")
		.trim()
		.toLowerCase();
	if (!target) {
		return "none";
	}
	for (const directive of directives || []) {
		const parsed = parseConnectorDirective(directive);
		if (!["force", "exclude", "select"].includes(parsed.kind)) {
			continue;
		}
		if (parsed.value.toLowerCase() === target) {
			return parsed.kind;
		}
	}
	return "none";
};

/**
 * True when at least one connector is selected/forced or a tag is selected.
 * In that mode exclusions are redundant — unlisted connectors are already skipped.
 *
 * @param {string[]} directives
 * @returns {boolean}
 */
export const hasSelectionOrForceDirectives = (directives) =>
	(directives || []).some((directive) => {
		const parsed = parseConnectorDirective(directive);
		return parsed.kind === "select" || parsed.kind === "force" || parsed.kind === "include-tag";
	});

/**
 * Whether the wizard has any manual connector configuration (directives, instances, or templates).
 *
 * @param {object} state
 * @returns {boolean}
 */
export const hasManualConnectorConfiguration = (state) => {
	const directives = state?.connectors ?? [];
	const additional = state?.additionalConnectors ?? {};
	const templates = state?.selectedVariableConnectorTemplates ?? [];
	return directives.length > 0 || Object.keys(additional).length > 0 || templates.length > 0;
};

/**
 * Exclude directives are redundant once a connector/tag is selected, forced, or configured.
 *
 * @param {object} options
 * @param {string[]} [options.directives]
 * @param {Record<string, unknown>} [options.additionalConnectors]
 * @param {string[]} [options.selectedVariableConnectorTemplates]
 * @returns {boolean}
 */
export const shouldDisableConnectorExcludes = ({
	directives = [],
	additionalConnectors = {},
	selectedVariableConnectorTemplates = [],
} = {}) => {
	if (hasSelectionOrForceDirectives(directives)) {
		return true;
	}
	if (Object.keys(additionalConnectors || {}).length > 0) {
		return true;
	}
	return (selectedVariableConnectorTemplates || []).length > 0;
};

/**
 * Splits connector directives into exclude entries and everything else.
 *
 * @param {string[]} directives
 * @returns {{ excludes: string[]; rest: string[] }}
 */
export const partitionExcludeDirectives = (directives) => {
	/** @type {string[]} */
	const excludes = [];
	/** @type {string[]} */
	const rest = [];
	for (const directive of directives || []) {
		const parsed = parseConnectorDirective(directive);
		if (parsed.kind === "exclude" || parsed.kind === "exclude-tag") {
			excludes.push(directive);
		} else {
			rest.push(directive);
		}
	}
	return {
		excludes: buildConnectorsPayload(excludes),
		rest: buildConnectorsPayload(rest),
	};
};

/**
 * When a connector/tag is selected or forced, exclude directives are stashed and removed
 * from the active list. When nothing is selected or forced, stashed excludes are restored.
 *
 * @param {string[]} directives
 * @param {string[]} [stashedExcludes]
 * @returns {{ active: string[]; stashedExcludes: string[] }}
 */
export const reconcileExcludeDirectives = (directives, stashedExcludes = []) => {
	const working = [...(directives || [])];
	if (hasSelectionOrForceDirectives(working)) {
		const { excludes, rest } = partitionExcludeDirectives(working);
		return {
			active: rest,
			stashedExcludes: buildConnectorsPayload([...(stashedExcludes || []), ...excludes]),
		};
	}
	return {
		active: buildConnectorsPayload([...working, ...(stashedExcludes || [])]),
		stashedExcludes: [],
	};
};

/**
 * @param {string[]} directives
 * @param {string} tag
 * @returns {'none' | 'include-tag' | 'exclude-tag'}
 */
export const getTagSelectionKind = (directives, tag) => {
	const target = String(tag ?? "")
		.trim()
		.toLowerCase();
	if (!target) {
		return "none";
	}
	for (const directive of directives || []) {
		const parsed = parseConnectorDirective(directive);
		if (parsed.kind === "include-tag" && parsed.value.toLowerCase() === target) {
			return "include-tag";
		}
		if (parsed.kind === "exclude-tag" && parsed.value.toLowerCase() === target) {
			return "exclude-tag";
		}
	}
	return "none";
};

/**
 * Removes directives that match a value and one of the given kinds.
 *
 * @param {string[]} directives
 * @param {string} value connector id or tag name
 * @param {Array<'force' | 'exclude' | 'select' | 'include-tag' | 'exclude-tag'>} kindsToRemove
 * @returns {string[]}
 */
export const removeDirectivesForValue = (directives, value, kindsToRemove) => {
	const normalized = String(value ?? "")
		.trim()
		.toLowerCase();
	if (!normalized) {
		return [...(directives || [])];
	}
	const kinds = new Set(kindsToRemove || []);
	return (directives || [])
		.filter((directive) => {
			const parsed = parseConnectorDirective(directive);
			if (parsed.value.toLowerCase() !== normalized) {
				return true;
			}
			return !kinds.has(parsed.kind);
		})
		.sort(compareLocale);
};

/**
 * Removes a single directive entry by its raw YAML value (e.g. {@code +Linux}).
 *
 * @param {string[]} directives
 * @param {string} raw
 * @returns {string[]}
 */
export const removeConnectorDirectiveByRaw = (directives, raw) =>
	(directives || []).filter((directive) => String(directive) !== String(raw)).sort(compareLocale);

/**
 * Hides force/select directives already represented as additionalConnectors instance chips.
 *
 * @param {string[]} directives
 * @param {Record<string, { uses?: string }>} [additionalConnectors]
 * @returns {string[]}
 */
export const filterDirectivesForAdditionalConnectorChips = (
	directives,
	additionalConnectors = {},
) => {
	const instanceIds = new Set(Object.keys(additionalConnectors || {}));
	if (instanceIds.size === 0) {
		return [...(directives || [])];
	}
	return (directives || []).filter((directive) => {
		const parsed = parseConnectorDirective(directive);
		if ((parsed.kind === "force" || parsed.kind === "select") && instanceIds.has(parsed.value)) {
			return false;
		}
		return true;
	});
};

/**
 * @param {string[]} directives
 * @param {'force' | 'exclude' | 'select' | 'include-tag' | 'exclude-tag'} kind
 * @param {string} value connector id or tag name
 * @returns {string[]}
 */
export const upsertConnectorDirective = (directives, kind, value) => {
	const normalized = String(value ?? "").trim();
	if (!normalized) {
		return [...(directives || [])];
	}
	const isConnectorKind = kind === "force" || kind === "exclude" || kind === "select";
	const isTagKind = kind === "include-tag" || kind === "exclude-tag";
	const next = (directives || []).filter((directive) => {
		const parsed = parseConnectorDirective(directive);
		if (parsed.value.toLowerCase() !== normalized.toLowerCase()) {
			return true;
		}
		if (isConnectorKind) {
			return !["force", "exclude", "select"].includes(parsed.kind);
		}
		if (isTagKind) {
			return !["include-tag", "exclude-tag"].includes(parsed.kind);
		}
		return true;
	});
	const formatted = formatConnectorDirective(kind, normalized);
	return formatted ? [...next, formatted].sort(compareLocale) : next.sort(compareLocale);
};

/**
 * @param {object[]} catalog
 * @returns {string[]}
 */
export const collectCatalogTags = (catalog = []) => {
	const set = new Set();
	for (const item of catalog) {
		for (const tag of item.tags || []) {
			if (tag) {
				set.add(String(tag));
			}
		}
	}
	return [...set].sort(compareLocale);
};

/**
 * @param {object | null | undefined} item connector catalog entry
 * @returns {string}
 */
export const getConnectorListDescription = (item) => String(item?.information ?? "").trim();

/** Category tabs for the compatible connectors list (filters by detection tag). */
export const CONNECTOR_CATEGORY_TABS = [
	{ id: "all", label: "All" },
	{ id: "hardware", label: "Hardware", tag: "Hardware" },
	{ id: "system", label: "System", tag: "System" },
	{ id: "storage", label: "Storage", tag: "Storage" },
	{ id: "database", label: "Database", tag: "Database" },
	{ id: "network", label: "Network", tag: "Network" },
];

/**
 * @param {object[]} catalog
 * @returns {object[]}
 */
export const dedupeConnectorCatalogById = (catalog = []) => {
	/** @type {Map<string, object>} */
	const byId = new Map();
	for (const item of catalog || []) {
		const id = String(item?.id || "").trim();
		if (!id || byId.has(id)) {
			continue;
		}
		byId.set(id, item);
	}
	return [...byId.values()].sort((a, b) =>
		compareLocale(String(a?.displayName || a?.id || ""), String(b?.displayName || b?.id || "")),
	);
};

/**
 * @param {object} item connector catalog entry
 * @param {string} categoryTabId CONNECTOR_CATEGORY_TABS id
 * @returns {boolean}
 */
export const connectorMatchesCategoryTab = (item, categoryTabId = "all") => {
	if (!categoryTabId || categoryTabId === "all") {
		return true;
	}
	const tab = CONNECTOR_CATEGORY_TABS.find((entry) => entry.id === categoryTabId);
	if (!tab?.tag) {
		return true;
	}
	const wanted = tab.tag.toLowerCase();
	return (item?.tags || []).some((tag) => String(tag).toLowerCase() === wanted);
};

/**
 * @param {object} item
 * @param {string} nameIdQuery
 * @param {Set<string>} selectedPlatforms
 * @param {string} [categoryTabId]
 * @returns {boolean}
 */
export const connectorMatchesListFilters = (
	item,
	nameIdQuery,
	selectedPlatforms,
	categoryTabId = "all",
) => {
	const q = nameIdQuery.trim().toLowerCase();
	if (q) {
		const id = String(item.id || "").toLowerCase();
		const name = String(item.displayName || "").toLowerCase();
		if (!id.includes(q) && !name.includes(q)) {
			return false;
		}
	}
	if (!connectorMatchesCategoryTab(item, categoryTabId)) {
		return false;
	}
	if (selectedPlatforms.size > 0) {
		const platforms = (item.platforms || []).map((p) => String(p));
		if (!platforms.some((p) => selectedPlatforms.has(p))) {
			return false;
		}
	}
	return true;
};

/**
 * @param {object} item
 * @param {string} query
 * @param {{ tags?: Set<string>; platforms?: Set<string>; protocols?: Set<string>; hostTypes?: Set<string> }} filters
 * @returns {boolean}
 */
export const connectorMatchesCatalogFilters = (item, query, filters = {}) => {
	const q = query.trim().toLowerCase();
	if (q) {
		const id = String(item.id || "").toLowerCase();
		const name = String(item.displayName || "").toLowerCase();
		const tags = (item.tags || []).map((t) => String(t).toLowerCase());
		if (!id.includes(q) && !name.includes(q) && !tags.some((t) => t.includes(q))) {
			return false;
		}
	}
	if (filters.tags?.size) {
		const itemTags = (item.tags || []).map((t) => String(t));
		if (!itemTags.some((t) => filters.tags.has(t))) {
			return false;
		}
	}
	if (filters.platforms?.size) {
		const platforms = (item.platforms || []).map((p) => String(p));
		if (!platforms.some((p) => filters.platforms.has(p))) {
			return false;
		}
	}
	if (filters.protocols?.size) {
		const protocols = (item.requiredProtocols || []).map((p) => String(p));
		if (!protocols.some((p) => filters.protocols.has(p))) {
			return false;
		}
	}
	if (filters.hostTypes?.size) {
		const hostTypes = (item.appliesToHostTypes || []).map((t) => String(t));
		if (!hostTypes.some((t) => filters.hostTypes.has(t))) {
			return false;
		}
	}
	return true;
};

/**
 * @param {object[]} catalog
 * @returns {{ tags: string[]; platforms: string[]; protocols: string[]; hostTypes: string[] }}
 */
export const collectCatalogFilterOptions = (catalog = []) => {
	const tags = new Set();
	const platforms = new Set();
	const protocols = new Set();
	const hostTypes = new Set();
	for (const item of catalog) {
		for (const tag of item.tags || []) {
			if (tag) {
				tags.add(String(tag));
			}
		}
		for (const platform of item.platforms || []) {
			if (platform) {
				platforms.add(String(platform));
			}
		}
		for (const protocol of item.requiredProtocols || []) {
			if (protocol) {
				protocols.add(String(protocol));
			}
		}
		for (const hostType of item.appliesToHostTypes || []) {
			if (hostType) {
				hostTypes.add(String(hostType));
			}
		}
	}
	return {
		tags: [...tags].sort(compareLocale),
		platforms: [...platforms].sort(compareLocale),
		protocols: [...protocols].sort(compareLocale),
		hostTypes: [...hostTypes].sort(compareLocale),
	};
};

/**
 * @param {ConnectorDirectiveKind} kind
 * @returns {string}
 */
export const connectorDirectiveLabel = (kind) => {
	switch (kind) {
		case "select":
			return "selection";
		case "force":
			return "force";
		case "exclude":
			return "exclude";
		case "include-tag":
			return "tag selection";
		case "exclude-tag":
			return "tag exclusion";
		default:
			return "auto-detect";
	}
};

/**
 * Builds default variable values from connector catalog definitions.
 *
 * @param {Array<{ name?: string; defaultValue?: string }>} variableDefinitions
 * @returns {Record<string, string>}
 */
export const buildDefaultVariableValues = (variableDefinitions = []) => {
	/** @type {Record<string, string>} */
	const values = {};
	for (const def of variableDefinitions) {
		const name = String(def?.name ?? "").trim();
		if (!name) {
			continue;
		}
		values[name] = def?.defaultValue != null ? String(def.defaultValue) : "";
	}
	return values;
};

/**
 * Reads instance variable values for the form, preserving explicit empty strings.
 * Keys missing from stored values fall back to connector template defaults.
 *
 * @param {Record<string, string>} [storedVariables]
 * @param {Array<{ name?: string; defaultValue?: string }>} [variableDefinitions]
 * @returns {Record<string, string>}
 */
export const readInstanceVariableValues = (storedVariables = {}, variableDefinitions = []) => {
	const defaults = buildDefaultVariableValues(variableDefinitions);
	const stored = storedVariables && typeof storedVariables === "object" ? storedVariables : {};
	/** @type {Record<string, string>} */
	const values = {};
	for (const def of variableDefinitions || []) {
		const name = String(def?.name ?? "").trim();
		if (!name) {
			continue;
		}
		if (Object.prototype.hasOwnProperty.call(stored, name)) {
			values[name] = stored[name] == null ? "" : String(stored[name]);
		} else {
			values[name] = defaults[name] ?? "";
		}
	}
	for (const [name, value] of Object.entries(stored)) {
		if (!(name in values)) {
			values[name] = value == null ? "" : String(value);
		}
	}
	return values;
};

/**
 * Normalizes variable values before persisting an additionalConnectors entry.
 * Keeps explicit empty strings so cleared fields are not restored from defaults later.
 *
 * @param {Record<string, string>} [values]
 * @param {Array<{ name?: string; defaultValue?: string }>} [variableDefinitions]
 * @returns {Record<string, string>}
 */
export const normalizeInstanceVariableValues = (values = {}, variableDefinitions = []) => {
	const defaults = buildDefaultVariableValues(variableDefinitions);
	const source = values && typeof values === "object" ? values : {};
	/** @type {Record<string, string>} */
	const normalized = {};
	for (const def of variableDefinitions || []) {
		const name = String(def?.name ?? "").trim();
		if (!name) {
			continue;
		}
		if (Object.prototype.hasOwnProperty.call(source, name)) {
			normalized[name] = source[name] == null ? "" : String(source[name]);
		} else {
			normalized[name] = defaults[name] ?? "";
		}
	}
	for (const [name, value] of Object.entries(source)) {
		if (!(name in normalized)) {
			normalized[name] = value == null ? "" : String(value);
		}
	}
	return normalized;
};

/**
 *
 * @param {string} templateId
 * @param {Array<{ name?: string; defaultValue?: string }>} [variableDefinitions]
 * @returns {{ uses: string; force: boolean; variables: Record<string, string> }}
 */
export const createDefaultAdditionalConnectorEntry = (templateId, variableDefinitions = []) => ({
	uses: templateId,
	force: true,
	variables: buildDefaultVariableValues(variableDefinitions),
});

/**
 * Parses additionalConnectors from host config into wizard state shape.
 *
 * @param {unknown} additionalConnectors
 * @returns {Record<string, { uses: string; force: boolean; variables: Record<string, string> }>}
 */
export const parseAdditionalConnectorsFromConfig = (additionalConnectors) => {
	if (!additionalConnectors || typeof additionalConnectors !== "object") {
		return {};
	}
	/** @type {Record<string, { uses: string; force: boolean; variables: Record<string, string> }>} */
	const parsed = {};
	for (const [connectorId, raw] of Object.entries(additionalConnectors)) {
		if (!connectorId || !raw || typeof raw !== "object") {
			continue;
		}
		const entry = /** @type {Record<string, unknown>} */ (raw);
		const uses = String(entry.uses ?? connectorId).trim() || connectorId;
		const force = entry.force !== false;
		/** @type {Record<string, string>} */
		const variables = {};
		if (entry.variables && typeof entry.variables === "object") {
			for (const [name, value] of Object.entries(entry.variables)) {
				if (name) {
					variables[name] = value == null ? "" : String(value);
				}
			}
		}
		parsed[connectorId] = { uses, force, variables };
	}
	return parsed;
};

/**
 * Formats applies-to host types for incompatibility messages.
 *
 * @param {object} connector
 * @returns {string}
 */
const formatConnectorAppliesTo = (connector) => {
	const appliesToHostTypes = (connector.appliesToHostTypes || []).map((t) => String(t));
	const appliesToDisplayNames = connector.appliesToDisplayNames || [];
	if (appliesToDisplayNames.length > 0) {
		return appliesToDisplayNames
			.map((displayName, index) => {
				const key = appliesToHostTypes[index] || displayName;
				return `${displayName} (${key})`;
			})
			.join(", ");
	}
	return appliesToHostTypes.join(", ");
};

/**
 * Evaluates connector compatibility for a resource context (client-side).
 *
 * @param {object} connector
 * @param {{ hostType?: string; protocols?: string[]; isLocalhost?: boolean }} context
 * @returns {{ compatible: boolean; incompatibilityReasons: string[] }}
 */
export const evaluateConnectorCompatibility = (
	connector,
	{ hostType = "", protocols = [], isLocalhost = false } = {},
) => {
	/** @type {string[]} */
	const reasons = [];
	const hostTypeInput = String(hostType ?? "").trim();
	const normalizedHostType = hostTypeInput.toLowerCase();
	const configuredProtocols = getOrderedSelectedProtocols(protocols);
	const configuredSet = new Set(configuredProtocols);

	if (!hostTypeInput) {
		reasons.push(`Invalid or missing host.type: ${hostTypeInput}`);
		return { compatible: false, incompatibilityReasons: reasons };
	}
	if (!Object.prototype.hasOwnProperty.call(HOST_TYPE_LABELS, normalizedHostType)) {
		reasons.push(`Invalid or missing host.type: ${hostTypeInput}`);
		return { compatible: false, incompatibilityReasons: reasons };
	}
	if (configuredProtocols.length === 0) {
		reasons.push("Configure at least one protocol on the resource before selecting connectors.");
		return { compatible: false, incompatibilityReasons: reasons };
	}

	const appliesToHostTypes = (connector?.appliesToHostTypes || []).map((t) =>
		String(t).toLowerCase(),
	);
	if (appliesToHostTypes.length === 0) {
		reasons.push("Connector has no detection definition.");
		return { compatible: false, incompatibilityReasons: reasons };
	}
	if (!appliesToHostTypes.includes(normalizedHostType)) {
		reasons.push(
			`Requires host.type ${formatConnectorAppliesTo(connector)} (current: ${formatHostTypeLabel(normalizedHostType)} / ${normalizedHostType}).`,
		);
		return { compatible: false, incompatibilityReasons: reasons };
	}

	const expectedConnection = isLocalhost ? "LOCAL" : "REMOTE";
	const connectionTypes = (connector?.connectionTypes || []).map((c) => String(c).toUpperCase());
	if (!connectionTypes.includes(expectedConnection)) {
		reasons.push(
			`Requires ${expectedConnection.toLowerCase()} connection (connector allows: ${connectionTypes
				.map((c) => c.toLowerCase())
				.join(", ")}).`,
		);
		return { compatible: false, incompatibilityReasons: reasons };
	}

	const requiredProtocols = (connector?.requiredProtocols || []).map(String);
	if (!requiredProtocols.some((protocol) => configuredSet.has(protocol))) {
		reasons.push(
			`Requires at least one of these protocols: ${requiredProtocols.join(", ")} (configured: ${configuredProtocols.join(", ")}).`,
		);
		return { compatible: false, incompatibilityReasons: reasons };
	}

	return { compatible: true, incompatibilityReasons: reasons };
};

/**
 * Annotates each catalog entry with compatibility for the given resource context.
 *
 * @param {object[]} catalog
 * @param {{ hostType?: string; protocols?: string[]; isLocalhost?: boolean }} context
 * @returns {object[]}
 */
export const annotateConnectorCatalog = (catalog = [], context = {}) =>
	(catalog || []).map((item) => {
		const { compatible, incompatibilityReasons } = evaluateConnectorCompatibility(item, context);
		return { ...item, compatible, incompatibilityReasons };
	});

/**
 * Returns compatible connectors for the given resource context.
 *
 * @param {object[]} catalog
 * @param {{ hostType?: string; protocols?: string[]; isLocalhost?: boolean }} context
 * @returns {object[]}
 */
export const filterCompatibleConnectorCatalog = (catalog = [], context = {}) =>
	annotateConnectorCatalog(catalog, context).filter((item) => item.compatible);

/**
 * Fetches the static connector catalog once per page session.
 *
 * @returns {Promise<object[]>}
 */
export const fetchConnectorCatalog = async () => {
	if (!connectorCatalogPromise) {
		connectorCatalogPromise = uiConfigApi
			.getConnectorCatalog()
			.then((raw) => sortConnectorSummaries(Array.isArray(raw) ? raw : []))
			.catch((error) => {
				connectorCatalogPromise = null;
				throw error;
			});
	}
	return connectorCatalogPromise;
};

/** Clears the in-memory connector catalog cache (tests). */
export const resetConnectorCatalogCache = () => {
	connectorCatalogPromise = null;
};

/**
 * Fetches the compatible connector catalog for host.type + protocols.
 *
 * @param {string} hostType
 * @param {string[]} selectedProtocols
 * @returns {Promise<{ catalog: object[]; catalogById: Map<string, object> | null; skipped: boolean; failed: boolean }>}
 */
export const fetchCompatibleConnectorCatalog = async (hostType, selectedProtocols) => {
	const normalizedHostType = String(hostType ?? "").trim();
	const protocols = getOrderedSelectedProtocols(selectedProtocols);

	if (!normalizedHostType || protocols.length === 0) {
		return { catalog: [], catalogById: null, skipped: true, failed: false };
	}

	try {
		const fullCatalog = await fetchConnectorCatalog();
		const catalog = filterCompatibleConnectorCatalog(fullCatalog, {
			hostType: normalizedHostType,
			protocols,
		});
		const catalogById = new Map(catalog.map((item) => [String(item.id || ""), item]));
		return { catalog, catalogById, skipped: false, failed: false };
	} catch {
		return { catalog: [], catalogById: null, skipped: false, failed: true };
	}
};

/**
 * Derives compatible connector IDs from the latest wizard state and a catalog map.
 *
 * @param {object} state wizard state slice
 * @param {Map<string, object> | null} catalogById
 * @param {{ failed?: boolean }} [options]
 * @returns {{ compatibleConnectorIds: string[]; compatibleVariableConnectorIds: string[] }}
 */
export const computeCompatibleConnectorIdsFromCatalog = (
	state,
	catalogById,
	{ failed = false } = {},
) => {
	const prevConnectors = Array.isArray(state.connectors) ? state.connectors.map(String) : [];
	const prevAdditional = state.additionalConnectors || {};

	if (failed || !catalogById) {
		return {
			compatibleConnectorIds: prevConnectors,
			compatibleVariableConnectorIds: Object.keys(prevAdditional).sort(compareLocale),
		};
	}

	const compatibleConnectorIds = prevConnectors
		.map((directive) => parseConnectorDirective(directive))
		.filter((parsed) => (parsed.kind === "force" || parsed.kind === "select") && parsed.value)
		.filter((parsed) => {
			const item = catalogById.get(parsed.value);
			return item && !item.hasVariables;
		})
		.map((parsed) => parsed.value)
		.sort(compareLocale);
	const compatibleVariableConnectorIds = Object.keys(prevAdditional)
		.filter((connectorId) => {
			const entry = prevAdditional[connectorId];
			const uses = String(entry?.uses ?? connectorId).trim() || connectorId;
			const item = catalogById.get(uses);
			return item?.hasVariables;
		})
		.sort(compareLocale);

	return { compatibleConnectorIds, compatibleVariableConnectorIds };
};

/**
 * Fetches the connector catalog for the given host.type + protocols so the
 * caller can recompute compatibility against the *current* wizard state.
 *
 * @deprecated Prefer {@link fetchCompatibleConnectorCatalog} + {@link computeCompatibleConnectorIdsFromCatalog}.
 * @param {object} state wizard state slice
 * @returns {Promise<{ catalogById: Map<string, object> | null; skipped: boolean; failed: boolean }>}
 */
export const pruneWizardConnectorsForCompatibility = async (state) => {
	const { catalogById, skipped, failed } = await fetchCompatibleConnectorCatalog(
		state.hostType,
		state.selectedProtocols,
	);
	return { catalogById, skipped, failed };
};

/**
 * @param {Record<string, { uses?: string; force?: boolean; variables?: Record<string, string> }>} [additionalConnectors]
 * @returns {Record<string, { uses?: string; force?: boolean; variables?: Record<string, string> }>}
 */
export const visibleAdditionalConnectors = (additionalConnectors = {}) => ({
	...(additionalConnectors || {}),
});

/**
 * Merges edits from the variables step into stored additionalConnectors, preserving hidden drafts.
 *
 * @param {Record<string, { uses?: string; force?: boolean; variables?: Record<string, string> }>} stored
 * @param {Record<string, { uses?: string; force?: boolean; variables?: Record<string, string> }>} visiblePatch
 * @param {string[]} compatibleVariableConnectorIds
 * @returns {Record<string, { uses?: string; force?: boolean; variables?: Record<string, string> }>}
 */
/**
 * Returns whether another additionalConnectors entry already uses the candidate ID.
 *
 * @param {Record<string, unknown>} [additionalConnectors]
 * @param {string} candidateId
 * @param {string} [excludeInstanceId] instance being renamed (ignored when matching)
 * @returns {boolean}
 */
export const isAdditionalConnectorIdTaken = (
	additionalConnectors = {},
	candidateId,
	excludeInstanceId = "",
) => {
	const candidate = String(candidateId ?? "")
		.trim()
		.toLowerCase();
	if (!candidate) {
		return false;
	}
	const exclude = String(excludeInstanceId ?? "")
		.trim()
		.toLowerCase();
	for (const key of Object.keys(additionalConnectors)) {
		const normalized = String(key).trim().toLowerCase();
		if (normalized === candidate && normalized !== exclude) {
			return true;
		}
	}
	return false;
};

/**
 * Validates additionalConnectors entries and any in-progress instance ID edit.
 *
 * @param {object} options
 * @param {Record<string, { uses?: string; force?: boolean; variables?: Record<string, string> }>} [options.additionalConnectors]
 * @param {string[]} [options.selectedVariableConnectorTemplates]
 * @param {{ instanceKey?: string | null; instanceId?: string }} [options.editDraft]
 * @returns {{ errors: Record<string, string>; highlightInstanceId: string | null; valid: boolean }}
 */
export const collectConnectorVariablesErrors = ({
	additionalConnectors = {},
	selectedVariableConnectorTemplates = [],
	editDraft = null,
} = {}) => {
	/** @type {Record<string, string>} */
	const errors = {};
	let highlightInstanceId = null;

	const entries = Object.entries(additionalConnectors || {});
	const seenIds = new Set();

	for (const [instanceId, entry] of entries) {
		const id = String(instanceId ?? "").trim();
		if (!id) {
			errors._connectorVariables = "Each variable connector must have a connector ID.";
			highlightInstanceId = highlightInstanceId || instanceId;
			continue;
		}
		if (seenIds.has(id.toLowerCase())) {
			errors._connectorVariables = "Connector IDs must be unique.";
			highlightInstanceId = highlightInstanceId || instanceId;
		}
		seenIds.add(id.toLowerCase());
		const uses = String(entry?.uses ?? "").trim();
		if (!uses) {
			errors[`additionalConnectors.${instanceId}.uses`] = "uses is required";
			highlightInstanceId = highlightInstanceId || instanceId;
		}
	}

	const templates = (selectedVariableConnectorTemplates || [])
		.map((templateId) => String(templateId ?? "").trim())
		.filter(Boolean);
	for (const templateId of templates) {
		const hasInstance = entries.some(
			([, entry]) => String(entry?.uses ?? "").trim() === templateId,
		);
		if (!hasInstance) {
			errors._connectorVariables = "Each configured connector needs at least one instance.";
			break;
		}
	}

	const editKey = String(editDraft?.instanceKey ?? "").trim();
	if (editKey) {
		const trimmed = String(editDraft?.instanceId ?? "").trim();
		if (!trimmed) {
			errors[`additionalConnectors.${editKey}.id`] = "Connector ID is required.";
			errors._connectorVariables = errors._connectorVariables || "Connector ID is required.";
			highlightInstanceId = highlightInstanceId || editKey;
		} else if (isAdditionalConnectorIdTaken(additionalConnectors, trimmed, editKey)) {
			errors[`additionalConnectors.${editKey}.id`] = "This connector ID is already in use.";
			errors._connectorVariables = errors._connectorVariables || "Connector IDs must be unique.";
			highlightInstanceId = highlightInstanceId || editKey;
		}
	}

	return {
		errors,
		highlightInstanceId,
		valid: Object.keys(errors).length === 0,
	};
};

/**
 * Suggests a unique additionalConnectors instance key for a template.
 *
 * @param {string} templateId
 * @param {Record<string, unknown>} [additionalConnectors]
 * @returns {string}
 */
export const suggestAdditionalConnectorInstanceId = (templateId, additionalConnectors = {}) => {
	const base = String(templateId ?? "").trim();
	if (!base) {
		return "";
	}
	if (!isAdditionalConnectorIdTaken(additionalConnectors, base)) {
		return base;
	}
	let suffix = 2;
	while (isAdditionalConnectorIdTaken(additionalConnectors, `${base}-${suffix}`)) {
		suffix += 1;
	}
	return `${base}-${suffix}`;
};

export const mergeAdditionalConnectorsPatch = (stored = {}, visiblePatch = {}) => ({
	...stored,
	...visiblePatch,
});

/**
 * Applies additionalConnectors edits and keeps force directives in sync for forced instances.
 *
 * @param {object} state wizard state slice
 * @param {Record<string, { uses?: string; force?: boolean; variables?: Record<string, string> }>} nextAdditionalConnectors
 * @returns {object} wizard state patch
 */
export const applyAdditionalConnectorsChange = (state, nextAdditionalConnectors) => {
	const prevAdditional = state.additionalConnectors || {};
	const prevConnectors = Array.isArray(state.connectors) ? state.connectors : [];
	let connectors = [...prevConnectors];

	for (const id of Object.keys(prevAdditional)) {
		if (!(id in (nextAdditionalConnectors || {}))) {
			connectors = removeDirectivesForValue(connectors, id, ["force", "exclude", "select"]);
		}
	}

	for (const [id, entry] of Object.entries(nextAdditionalConnectors || {})) {
		if (entry?.force !== false) {
			connectors = upsertConnectorDirective(connectors, "force", id);
		} else {
			connectors = removeDirectivesForValue(connectors, id, ["force"]);
		}
	}

	/** @type {object} */
	const patch = { additionalConnectors: nextAdditionalConnectors || {} };
	const connectorsChanged =
		connectors.length !== prevConnectors.length ||
		connectors.some((directive, index) => directive !== prevConnectors[index]);
	if (connectorsChanged) {
		patch.connectors = connectors;
	}
	if (
		Object.keys(nextAdditionalConnectors || {}).length > 0 &&
		state.connectorDetectionMode === "automatic"
	) {
		patch.connectorDetectionMode = "manual";
	}
	const templateIds = [
		...new Set(
			Object.values(nextAdditionalConnectors || {})
				.map((entry) => String(entry?.uses ?? "").trim())
				.filter(Boolean),
		),
	].sort(compareLocale);
	patch.selectedVariableConnectorTemplates = templateIds;
	patch.configureVariableConnectors = templateIds.length > 0;
	return patch;
};

export const buildAdditionalConnectorsPayload = (additionalConnectors) => {
	if (!additionalConnectors || typeof additionalConnectors !== "object") {
		return {};
	}
	/** @type {Record<string, { uses: string; force: boolean; variables: Record<string, string> }>} */
	const payload = {};
	for (const [connectorId, entry] of Object.entries(additionalConnectors)) {
		const id = String(connectorId ?? "").trim();
		if (!id || !entry) {
			continue;
		}
		const uses = String(entry.uses ?? id).trim() || id;
		payload[id] = {
			uses,
			force: entry.force !== false,
			variables: entry.variables || {},
		};
	}
	return payload;
};

/**
 * @param {string} connectorId
 * @returns {string}
 */
export const connectorDocumentationUrl = (connectorId) => {
	const id = String(connectorId ?? "")
		.trim()
		.toLowerCase();
	return `https://metricshub.com/docs/latest/connectors/${encodeURIComponent(id)}`;
};

/**
 * @param {string} text
 * @returns {string[]}
 */
export const parseRawConnectorsText = (text) => {
	const lines = String(text ?? "")
		.split(/\r?\n/)
		.map((line) => line.trim())
		.filter(Boolean);
	return [...new Set(lines)].sort(compareLocale);
};

/**
 * Parses comma-separated connector directives (single-line code editor).
 *
 * @param {string} text
 * @returns {string[]}
 */
export const parseInlineConnectorsText = (text) => {
	const parts = String(text ?? "")
		.split(",")
		.map((part) => part.trim())
		.filter(Boolean);
	return [...new Set(parts)].sort(compareLocale);
};

/**
 * @param {string[]} directives
 * @returns {string}
 */
export const formatRawConnectorsText = (directives) =>
	(directives || [])
		.map((entry) => String(entry ?? "").trim())
		.filter(Boolean)
		.sort(compareLocale)
		.join("\n");

/**
 * Formats connector directives as a single comma-separated line for the code tab.
 *
 * @param {string[]} directives
 * @returns {string}
 */
export const formatInlineConnectorsText = (directives) =>
	(directives || [])
		.map((entry) => String(entry ?? "").trim())
		.filter(Boolean)
		.sort(compareLocale)
		.join(" , ");

/**
 * Removes connector directives and variable instances that are incompatible with the
 * current host context after host.type or protocol changes.
 *
 * @param {object} state wizard state slice
 * @param {object[]} [annotatedCatalog]
 * @returns {{ connectors: string[]; additionalConnectors: Record<string, unknown>; selectedVariableConnectorTemplates: string[]; configureVariableConnectors: boolean }}
 */
export const pruneWizardConnectorsForHostContext = (state, annotatedCatalog = []) => {
	const compatiblePlain = (annotatedCatalog || []).filter(
		(item) => item?.compatible && !item?.hasVariables,
	);
	const compatibleVariable = (annotatedCatalog || []).filter(
		(item) => item?.compatible && item?.hasVariables,
	);
	const compatibleIds = new Set(compatiblePlain.map((item) => String(item.id || "")));
	const variableTemplateIds = new Set(compatibleVariable.map((item) => String(item.id || "")));
	const tagOptions = new Set(
		collectCatalogTags(compatiblePlain).map((tag) => String(tag).toLowerCase()),
	);

	const connectors = (state.connectors || []).filter((directive) => {
		const parsed = parseConnectorDirective(directive);
		if (!parsed.value) {
			return false;
		}
		if (parsed.kind === "include-tag" || parsed.kind === "exclude-tag") {
			return tagOptions.has(parsed.value.toLowerCase());
		}
		return compatibleIds.has(parsed.value);
	});

	const additionalConnectors = Object.fromEntries(
		Object.entries(state.additionalConnectors || {}).filter(([, entry]) => {
			const uses = String(entry?.uses ?? "").trim();
			return uses && variableTemplateIds.has(uses);
		}),
	);

	const selectedVariableConnectorTemplates = (state.selectedVariableConnectorTemplates || [])
		.map((id) => String(id || "").trim())
		.filter((id) => id && variableTemplateIds.has(id))
		.sort(compareLocale);

	const configureVariableConnectors = selectedVariableConnectorTemplates.length > 0;

	return {
		connectors,
		additionalConnectors,
		selectedVariableConnectorTemplates,
		configureVariableConnectors,
	};
};
