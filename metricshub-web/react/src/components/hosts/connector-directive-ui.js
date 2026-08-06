import { guidedConfigRowHoverBg } from "./guided-config-ui-tokens";
import { parseConnectorDirective } from "./connector-utils";

/**
 * @param {import("./connector-utils").ConnectorDirectiveKind} kind
 * @returns {string}
 */
export const getDirectiveKindLabel = (kind) => {
	switch (kind) {
		case "select":
			return "Selected connector";
		case "force":
			return "Forced connector";
		case "exclude":
			return "Excluded connector";
		case "include-tag":
			return "Selected tag";
		case "exclude-tag":
			return "Excluded tag";
	}
	return "Connector directive";
};

/**
 * Chip label — MetricsHub YAML directive syntax.
 *
 * @param {string} directive
 * @returns {string}
 */
export const getDirectiveChipLabel = (directive) => {
	const parsed = parseConnectorDirective(directive);
	return parsed.raw || directive;
};

/**
 * @param {string} directive
 * @returns {string}
 */
export const getDirectiveChipTooltip = (directive) => {
	const parsed = parseConnectorDirective(directive);
	if (!parsed.value) {
		return directive;
	}
	return `${getDirectiveKindLabel(parsed.kind)}: ${parsed.value}`;
};

/**
 * Chip label for a configurable connector instance (additionalConnectors entry).
 *
 * @param {string} instanceId
 * @param {boolean} [forced]
 * @returns {string}
 */
export const getAdditionalConnectorChipLabel = (instanceId, forced = true) => {
	const id = String(instanceId ?? "").trim();
	if (!id) {
		return "";
	}
	return forced ? `+${id}` : id;
};

/**
 * @param {string} instanceId
 * @param {string} [usesConnector]
 * @param {boolean} [forced]
 * @returns {string}
 */
export const getAdditionalConnectorChipTooltip = (instanceId, usesConnector, forced = true) => {
	const id = String(instanceId ?? "").trim();
	const uses = String(usesConnector ?? "").trim();
	const kind = forced ? "Forced connector" : "Selected connector";
	if (uses && uses !== id) {
		return `${kind} instance: ${id}\nUses connector: ${uses}`;
	}
	return `${kind} instance: ${id}`;
};

/**
 * @param {import("./connector-utils").ConnectorDirectiveKind} kind
 * @returns {object}
 */
export const getDirectiveChipSx = (kind) => {
	switch (kind) {
		case "force":
			return {
				bgcolor: "text.primary",
				color: "background.paper",
				borderColor: "text.primary",
				"&:hover": { bgcolor: "text.secondary" },
			};
		case "exclude":
		case "exclude-tag":
			return {
				bgcolor: "warning.main",
				color: "warning.contrastText",
				borderColor: "warning.main",
			};
		case "select":
		case "include-tag":
			return {
				bgcolor: "primary.main",
				color: "primary.contrastText",
				borderColor: "primary.main",
				"&:hover": { bgcolor: "primary.dark", borderColor: "primary.dark" },
			};
	}
	return {
		color: "primary.main",
		borderColor: "primary.main",
	};
};

/** Light gray delete icon on filled directive chips. */
export const DIRECTIVE_CHIP_DELETE_ICON_SX = {
	"& .MuiChip-deleteIcon": {
		color: "grey.400",
		opacity: 0.95,
		"&:hover": {
			color: "grey.200",
		},
	},
};

/**
 * Delete icon styling tuned for chip background (exclude chips use a lighter fill).
 *
 * @param {import("./connector-utils").ConnectorDirectiveKind} kind
 * @returns {object}
 */
export const getDirectiveChipDeleteIconSx = (kind) => {
	if (kind === "exclude" || kind === "exclude-tag") {
		return {
			"& .MuiChip-deleteIcon": {
				color: "grey.700",
				opacity: 0.75,
				"&:hover": {
					color: "grey.900",
				},
			},
		};
	}
	return DIRECTIVE_CHIP_DELETE_ICON_SX;
};

/**
 * @param {"force" | "exclude" | "select" | "include-tag" | "exclude-tag" | "none"} selection
 * @param {import("@mui/material/styles"). Theme} theme
 * @returns {object | undefined}
 */
export const directiveRowHighlightSx = (selection, theme) => {
	if (selection === "none") {
		return undefined;
	}
	const emphasis = selection === "force" || selection === "select" || selection === "include-tag";
	return {
		bgcolor: guidedConfigRowHoverBg(theme, emphasis, false),
		"&:hover": {
			bgcolor: guidedConfigRowHoverBg(theme, emphasis, true),
		},
	};
};
