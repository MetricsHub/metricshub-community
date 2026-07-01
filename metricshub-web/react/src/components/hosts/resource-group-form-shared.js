/**
 * Shared constants for resource group create/edit forms.
 */

/** Help text shown on the (i) icon next to the three well-known site metrics. */
export const METRIC_HELP_BY_KEY = {
	"hw.site.carbon_intensity":
		"Carbon dioxide produced per kilowatt-hour.\n" +
		"\n" +
		"Regional averages:\n" +
		"  • Europe: 230 g/kWh\n" +
		"  • Texas, USA: 309 g/kWh\n" +
		"  • Ontario, Canada: 40 g/kWh\n" +
		"  • Queensland, Australia: 712 g/kWh\n" +
		"\n" +
		"Source: electricitymap.org",
	"hw.site.electricity_cost":
		"Electricity cost per kilowatt-hour.\n" +
		"\n" +
		"Regional averages (non-household):\n" +
		"  • Europe: $0.12/kWh\n" +
		"  • USA: $0.159/kWh\n" +
		"  • Canada: $0.117/kWh\n" +
		"  • Australia: $0.225/kWh\n" +
		"\n" +
		"Source: globalpetrolprices.com",
	"hw.site.pue":
		"Power Usage Effectiveness.\n" +
		"A ratio describing how efficiently a computer data center uses energy.\n" +
		"The ideal ratio is 1.",
};

export const DEFAULT_METRIC_SEEDS = [
	{ key: "hw.site.carbon_intensity", value: "230" },
	{ key: "hw.site.electricity_cost", value: "0.12" },
	{ key: "hw.site.pue", value: "1.8" },
];

export const RESOURCE_GROUP_FORM_STEPS = [
	{
		id: "details",
		label: "Group details",
		description: "Name and shared attributes inherited by every resource in the group.",
	},
	{
		id: "metrics",
		label: "Site metrics",
		description: "Optional environmental cost inputs — defaults provided for common regions.",
	},
];

export const RESOURCE_GROUP_RESOURCES_STEP = {
	id: "resources",
	label: "Resources",
	description: "Monitored resources in this group — open a row to edit or select rows to delete.",
};

/**
 * @param {"create" | "edit"} mode
 * @returns {typeof RESOURCE_GROUP_FORM_STEPS}
 */
export const getResourceGroupFormSteps = (mode) =>
	mode === "edit"
		? [...RESOURCE_GROUP_FORM_STEPS, RESOURCE_GROUP_RESOURCES_STEP]
		: RESOURCE_GROUP_FORM_STEPS;

/**
 * Canonical stringification used for dirty-checking. Sorts keys so reordering
 * doesn't trigger a false-positive "dirty" state.
 *
 * @param {string} name
 * @param {Record<string, string>} attributes
 * @param {Record<string, number>} metrics
 */
export const resourceGroupConfigFingerprint = (name, attributes, metrics) => {
	const sortedAttrs = Object.fromEntries(
		Object.entries(attributes).sort(([a], [b]) => a.localeCompare(b)),
	);
	const sortedMetrics = Object.fromEntries(
		Object.entries(metrics).sort(([a], [b]) => a.localeCompare(b)),
	);
	return JSON.stringify({
		name: String(name).trim(),
		attributes: sortedAttrs,
		metrics: sortedMetrics,
	});
};

/**
 * @param {Record<string, unknown>} attrs
 * @returns {Record<string, string>}
 */
export const normalizeGroupAttributes = (attrs) =>
	Object.fromEntries(Object.entries(attrs || {}).map(([k, v]) => [k, v == null ? "" : String(v)]));

/**
 * @param {Record<string, unknown>} metrics
 * @returns {Record<string, number>}
 */
export const normalizeGroupMetrics = (metrics) => {
	/** @type {Record<string, number>} */
	const out = {};
	for (const [k, v] of Object.entries(metrics || {})) {
		const n = Number(v);
		if (!Number.isNaN(n)) {
			out[k] = n;
		}
	}
	return out;
};

/**
 * @param {{ description?: string } | null | undefined} step
 * @returns {string | null}
 */
export const getResourceGroupStepSubtitle = (step) => step?.description ?? null;

/**
 * Per-step fingerprint for edit-mode dirty detection (resources step is excluded).
 *
 * @param {string} name
 * @param {Record<string, string>} attributes
 * @param {Record<string, number>} metrics
 * @param {string} stepId
 * @returns {string}
 */
export const getResourceGroupStepFingerprint = (name, attributes, metrics, stepId) => {
	const normalizedName = String(name).trim();
	const normalizedAttrs = normalizeGroupAttributes(attributes);
	const normalizedMetrics = normalizeGroupMetrics(metrics);

	if (stepId === "details") {
		const sortedAttrs = Object.fromEntries(
			Object.entries(normalizedAttrs).sort(([a], [b]) => a.localeCompare(b)),
		);
		return JSON.stringify({ name: normalizedName, attributes: sortedAttrs });
	}
	if (stepId === "metrics") {
		const sortedMetrics = Object.fromEntries(
			Object.entries(normalizedMetrics).sort(([a], [b]) => a.localeCompare(b)),
		);
		return JSON.stringify({ metrics: sortedMetrics });
	}
	return "";
};

/**
 * @param {string} name
 * @param {Record<string, string>} attributes
 * @param {Record<string, number>} metrics
 * @param {string} stepId
 * @param {{ mode?: "create" | "edit"; furthestStepIndex?: number }} [options]
 * @returns {boolean}
 */
export const isResourceGroupStepValid = (name, attributes, metrics, stepId, options = {}) => {
	const { mode = "edit", furthestStepIndex = 0 } = options;
	const metricsStepIndex = RESOURCE_GROUP_FORM_STEPS.findIndex((step) => step.id === "metrics");

	if (stepId === "details") {
		return String(name).trim().length > 0;
	}
	if (stepId === "metrics") {
		if (mode === "create") {
			const detailsValid = String(name).trim().length > 0;
			return detailsValid && furthestStepIndex >= metricsStepIndex;
		}
		return true;
	}
	if (stepId === RESOURCE_GROUP_RESOURCES_STEP.id) {
		return mode === "edit";
	}
	return true;
};

/**
 * @param {string} name
 * @param {Record<string, unknown>} attributes
 * @param {Record<string, unknown>} metrics
 * @returns {{ details: string; metrics: string }}
 */
export const buildResourceGroupBaselineSteps = (name, attributes, metrics) => ({
	details: getResourceGroupStepFingerprint(
		name,
		normalizeGroupAttributes(attributes),
		{},
		"details",
	),
	metrics: getResourceGroupStepFingerprint("", {}, normalizeGroupMetrics(metrics), "metrics"),
});
