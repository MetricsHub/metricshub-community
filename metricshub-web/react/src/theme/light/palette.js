import { common } from "@mui/material/colors";
import { alpha } from "@mui/material/styles";
import { divider, error, info, neutral, success, warning, blue } from "../colors";

/**
 * Build the light theme palette object.
 *
 * @returns The light theme palette object.
 */
export const buildPalette = () => {
	return {
		action: {
			active: neutral[800],
			disabled: alpha(neutral[900], 0.38),
			disabledBackground: alpha(neutral[900], 0.18),
			focus: blue.main,
			hover: alpha(neutral[900], 0.12),
			selected: alpha(neutral[900], 0.22),
		},
		background: {
			default: common.white,
			paper: neutral[200],
		},
		divider: divider.light,
		error,
		info,
		mode: "light",
		neutral,
		primary: blue,
		success,
		text: {
			primary: neutral[900],
			secondary: neutral[700],
			disabled: alpha(neutral[900], 0.48),
		},
		warning,
	};
};
