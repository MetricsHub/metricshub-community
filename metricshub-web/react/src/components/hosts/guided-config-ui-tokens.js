import { alpha } from "@mui/material/styles";

/**
 * Table row hover intensity for guided-config pickers (connectors, instances, etc.).
 * Adjust these values globally to tune hover contrast.
 */
export const GUIDED_CONFIG_ROW_HOVER_ALPHA = {
	emphasis: { light: 0.06, dark: 0.14 },
	emphasisHover: { light: 0.09, dark: 0.2 },
	subtle: { light: 0.04, dark: 0.1 },
	subtleHover: { light: 0.06, dark: 0.14 },
};

/**
 * @param {import("@mui/material/styles").Theme} theme
 * @param {boolean} [emphasis]
 * @param {boolean} [hovered]
 * @returns {string}
 */
export const guidedConfigRowHoverBg = (theme, emphasis = true, hovered = false) => {
	const mode = theme.palette.mode === "dark" ? "dark" : "light";
	const palette = GUIDED_CONFIG_ROW_HOVER_ALPHA;
	const level = emphasis
		? hovered
			? palette.emphasisHover
			: palette.emphasis
		: hovered
			? palette.subtleHover
			: palette.subtle;
	return alpha(theme.palette.text.primary, level[mode]);
};

/** Basic alert style inside guided-config panels. */
export const guidedConfigPanelAlertSx = {
	width: "100%",
};

/** Outlined controls with primary text — transparent fill, blue on hover. */
export const guidedConfigOutlinedPrimaryHoverSx = {
	color: "primary.main",
	borderColor: "primary.main",
	bgcolor: "transparent",
	"&:hover": {
		bgcolor: "primary.main",
		backgroundColor: "primary.main",
		borderColor: "primary.main",
		color: "primary.contrastText",
	},
};

/**
 * @param {string} text
 * @returns {string}
 */
export const toTitleCase = (text) =>
	String(text ?? "")
		.split(/\s+/)
		.filter(Boolean)
		.map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
		.join(" ");
