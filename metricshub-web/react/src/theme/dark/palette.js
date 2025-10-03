import { alpha } from "@mui/material/styles";
import { divider, error, info, neutral, success, warning, blue } from "../colors";

/**
 * Build the dark theme palette object.
 *
 * @returns The dark theme palette object.
 */
export const buildPalette = () => {
	return {
		action: {
			active: neutral[500],
			disabled: alpha(neutral[100], 0.38),
			disabledBackground: alpha(neutral[100], 0.12),
			focus: alpha(neutral[100], 0.16),
			hover: alpha(neutral[100], 0.12),
			selected: alpha(neutral[100], 0.22),
		},
		background: {
			default: neutral[900],
			paper: neutral[700],
		},
		divider: divider.dark,
		error,
		info,
		mode: "dark",
		neutral,
		primary: blue,
		success,
		text: {
			primary: neutral[100],
			secondary: neutral[300],
			disabled: alpha(neutral[100], 0.48),
		},
		warning,
	};
};
