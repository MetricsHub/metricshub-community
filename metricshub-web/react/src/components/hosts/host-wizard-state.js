import { compareLocale } from "../../utils/alphabetic-sort";
import { hasManualConnectorConfiguration } from "./connector-utils";
import { PROTOCOL_DEFAULTS, PROTOCOL_OPTIONS, protocolConfigToForm } from "./protocol-definitions";
import { getOrderedSelectedProtocols } from "./host-wizard-steps";

export const createEmptyHostWizardState = (defaultResourceGroup = null) => ({
	hostId: "",
	targetType: defaultResourceGroup ? "group" : "standalone",
	resourceGroup: defaultResourceGroup || "",
	hostName: "",
	hostType: "",
	selectedProtocols: [],
	protocols: {},
	connectors: [],
	additionalConnectors: {},
	compatibleConnectorIds: [],
	compatibleVariableConnectorIds: [],
	compatibleVariableConnectorIdsStale: false,
	selectedVariableConnectorTemplates: [],
	configureVariableConnectors: false,
	connectorDetectionMode: "automatic",
	originalHostId: "",
	furthestStep: 0,
	_editMode: false,
});

/**
 * @param {object} state
 * @returns {object}
 */
export const normalizeHostWizardState = (state) => {
	const base = {
		...createEmptyHostWizardState(),
		...state,
		connectors: Array.isArray(state.connectors) ? state.connectors : [],
		additionalConnectors:
			state.additionalConnectors && typeof state.additionalConnectors === "object"
				? state.additionalConnectors
				: {},
		connectorDetectionMode:
			state.connectorDetectionMode === "raw"
				? "manual"
				: state.connectorDetectionMode === "manual" || state.connectorDetectionMode === "automatic"
					? state.connectorDetectionMode
					: "automatic",
		configureVariableConnectors: Boolean(state.configureVariableConnectors),
		selectedVariableConnectorTemplates: Array.isArray(state.selectedVariableConnectorTemplates)
			? state.selectedVariableConnectorTemplates.map(String).filter(Boolean)
			: [],
		furthestStep: typeof state.furthestStep === "number" ? state.furthestStep : 0,
	};

	if (Array.isArray(state.selectedProtocols) && state.selectedProtocols.length > 0) {
		base.selectedProtocols = getOrderedSelectedProtocols(state.selectedProtocols);
	} else if (state.protocols && typeof state.protocols === "object") {
		base.selectedProtocols = getOrderedSelectedProtocols(Object.keys(state.protocols));
	} else if (state.protocol) {
		base.selectedProtocols = [state.protocol];
		base.protocols = {
			[state.protocol]:
				state.protocolConfig ||
				protocolConfigToForm(state.protocol, PROTOCOL_DEFAULTS[state.protocol] || {}),
		};
	} else {
		base.selectedProtocols = [];
	}

	if (hasManualConnectorConfiguration(base) && base.connectorDetectionMode === "automatic") {
		base.connectorDetectionMode = "manual";
	}

	return base;
};

/**
 * @param {Record<string, unknown>} obj
 * @returns {Record<string, unknown>}
 */
const sortObjectKeys = (obj) => {
	if (!obj || typeof obj !== "object") {
		return {};
	}
	return Object.keys(obj)
		.sort(compareLocale)
		.reduce((acc, key) => {
			acc[key] = obj[key];
			return acc;
		}, {});
};

/**
 * Updates selected protocols for the stepper. Deselected protocol drafts stay in {@code protocols}
 * so they can be restored when the user selects the protocol again.
 *
 * @param {object} state
 * @param {string[]} selectedProtocols
 * @returns {object}
 */
export const applySelectedProtocols = (state, selectedProtocols) => ({
	...state,
	selectedProtocols: getOrderedSelectedProtocols(selectedProtocols),
});

/**
 * @param {object} state
 * @returns {string}
 */
const protocolsFingerprint = (state) => {
	const selected = new Set(getOrderedSelectedProtocols(state.selectedProtocols));
	const entries = Object.entries(state.protocols ?? {}).filter(([id]) => selected.has(id));
	return sortObjectKeys(Object.fromEntries(entries));
};

const hostNameFingerprint = (hostName) => {
	const names = Array.isArray(hostName)
		? hostName
		: String(hostName ?? "")
				.split(/[;,]/)
				.map((name) => name.trim());
	return names.map(String).filter(Boolean).sort(compareLocale).join(",");
};

export const getHostWizardCommittedFingerprint = (state) =>
	JSON.stringify({
		hostId: String(state.hostId ?? "").trim(),
		hostName: hostNameFingerprint(state.hostName),
		hostType: state.hostType ?? "",
		targetType: state.targetType ?? "standalone",
		resourceGroup: state.resourceGroup ?? "",
		selectedProtocols: [...(state.selectedProtocols ?? [])],
		protocols: protocolsFingerprint(state),
		connectors: [...(state.connectors ?? [])].map(String).sort(compareLocale),
		additionalConnectors: state.additionalConnectors ?? {},
		selectedVariableConnectorTemplates: [...(state.selectedVariableConnectorTemplates ?? [])]
			.map(String)
			.filter(Boolean)
			.sort(compareLocale),
		configureVariableConnectors: Boolean(state.configureVariableConnectors),
		connectorDetectionMode: state.connectorDetectionMode ?? "automatic",
	});

/** @deprecated alias */
export const getHostWizardFingerprint = getHostWizardCommittedFingerprint;

/**
 * @param {object} state
 * @param {string} baselineFingerprint
 * @returns {boolean}
 */
export const isHostWizardDirty = (state, baselineFingerprint) =>
	getHostWizardCommittedFingerprint(state) !== baselineFingerprint;

/**
 * Fingerprint for a single wizard step (edit-mode per-step dirty detection).
 *
 * @param {object} state
 * @param {import("./host-wizard-steps").WizardStepDescriptor} step
 * @returns {string}
 */
export const getWizardStepFingerprint = (state, step) => {
	switch (step.type) {
		case "basics":
			return JSON.stringify({
				hostId: String(state.hostId ?? "").trim(),
				hostName: hostNameFingerprint(state.hostName),
				hostType: state.hostType ?? "",
				targetType: state.targetType ?? "standalone",
				resourceGroup: state.resourceGroup ?? "",
				selectedProtocols: [...getOrderedSelectedProtocols(state.selectedProtocols)],
			});
		case "protocol":
			return JSON.stringify(sortObjectKeys(state.protocols?.[step.protocolId] ?? {}));
		case "connectors":
			return JSON.stringify({
				connectors: [...(state.connectors ?? [])].map(String).sort(compareLocale),
				connectorDetectionMode: state.connectorDetectionMode ?? "automatic",
				additionalConnectors: sortObjectKeys(state.additionalConnectors || {}),
			});
		default:
			return "";
	}
};
