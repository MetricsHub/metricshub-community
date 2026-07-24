import { compareLocale } from "../../utils/alphabetic-sort";
import { PROTOCOL_OPTIONS } from "./protocol-definitions";

/** @typedef {'basics' | 'protocol' | 'connectors'} FormSectionType */

/**
 * @typedef {object} FormSectionDescriptor
 * @property {string} id
 * @property {string} label
 * @property {FormSectionType} type
 * @property {string} [protocolId]
 */

const protocolLabel = (protocolId) =>
	PROTOCOL_OPTIONS.find((p) => p.id === protocolId)?.label || protocolId;

/**
 * Selected protocol ids sorted A–Z by display label (form section order).
 *
 * @param {string[]} [selectedProtocols]
 * @returns {string[]}
 */
export const getOrderedSelectedProtocols = (selectedProtocols = []) => {
	const set = new Set((selectedProtocols || []).map(String).filter(Boolean));
	return PROTOCOL_OPTIONS.filter((p) => set.has(p.id))
		.sort((a, b) => compareLocale(a.label, b.label))
		.map((p) => p.id);
};

/**
 * Builds the dynamic form section list from current state.
 *
 * @param {object} state
 * @returns {FormSectionDescriptor[]}
 */
export const buildFormSections = (state) => {
	/** @type {FormSectionDescriptor[]} */
	const steps = [{ id: "basics", label: "Resource Details", type: "basics" }];

	const orderedProtocols = getOrderedSelectedProtocols(state.selectedProtocols);
	for (const protocolId of orderedProtocols) {
		steps.push({
			id: `protocol-${protocolId}`,
			label: `${protocolLabel(protocolId)} Configuration`,
			type: "protocol",
			protocolId,
		});
	}

	const hasConnectorRelevantProtocol = orderedProtocols.some((protocolId) => protocolId !== "ping");
	if (hasConnectorRelevantProtocol) {
		steps.push({ id: "connectors", label: "Connectors", type: "connectors" });
	}

	return steps;
};

/**
 * @param {FormSectionDescriptor[]} steps
 * @param {number} index
 * @returns {FormSectionDescriptor | undefined}
 */
export const getStepAt = (steps, index) => steps[index];

/**
 * @param {FormSectionDescriptor[]} steps
 * @param {FormSectionType} type
 * @param {string} [protocolId]
 * @returns {number}
 */
export const findStepIndex = (steps, type, protocolId) =>
	steps.findIndex((s) => s.type === type && (protocolId == null || s.protocolId === protocolId));

/**
 * Visible step indices for the stepper UI.
 *
 * @param {FormSectionDescriptor[]} steps
 * @param {number} activeStep
 * @param {number} furthestStep
 * @returns {number[]}
 */
/**
 * Short description shown under the active step in the side stepper.
 *
 * @param {FormSectionDescriptor | undefined} step
 * @returns {string | null}
 */
export const getFormSectionDescription = (step) => {
	if (!step) {
		return null;
	}
	switch (step.type) {
		case "basics":
			return "Enter resource name and target";
		case "protocol":
			return `Configure ${protocolLabel(step.protocolId)} settings`;
		case "connectors":
			return "Select one or more connectors";
		default:
			return null;
	}
};

/**
 * Subtitle for the create page header (Step X of Y — …).
 *
 * @param {FormSectionDescriptor | undefined} step
 * @returns {string}
 */
export const getFormSectionPageSubtitle = (step) => {
	if (!step) {
		return "";
	}
	switch (step.type) {
		case "basics":
			return "Define the resource identity and placement";
		case "protocol":
			return `Configure how MetricsHub connects via ${protocolLabel(step.protocolId)}`;
		case "connectors":
			return "Choose the connectors that match this resource";
		default:
			return "";
	}
};
