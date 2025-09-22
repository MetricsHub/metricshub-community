import { filledInputClasses } from "@mui/material";
import { alpha } from "@mui/material/styles";

/**
 * Create component style overrides and default props.
 *
 * @param {*} param0 - Object containing the palette property.
 * @returns {object} - The component style overrides and default props.
 */
export const buildComponents = ({ palette }) => {
	return {
		MuiInputBase: {
			styleOverrides: {
				input: {
					"&::placeholder": {
						color: palette.text.secondary,
					},
				},
			},
		},
		MuiIconButton: {
			styleOverrides: {
				root: {
					"&:hover": {
						backgroundColor: palette.action.hover,
					},
					"&:active": {
						transform: "scale(0.92)",
					},
				},
			},
		},
		MuiFilledInput: {
			styleOverrides: {
				root: {
					borderColor: palette.neutral[400],
					"&:hover": {
						backgroundColor: palette.action.hover,
					},
					[`&.${filledInputClasses.disabled}`]: {
						backgroundColor: "transparent",
					},
					[`&.${filledInputClasses.focused}`]: {
						backgroundColor: "transparent",
						borderColor: palette.primary.main,
						boxShadow: `${palette.primary.main} 0 0 0 2px`,
					},
					[`&.${filledInputClasses.error}`]: {
						borderColor: palette.error.main,
						boxShadow: `${palette.error.main} 0 0 0 2px`,
					},
				},
			},
		},
		MuiTooltip: {
			styleOverrides: {
				tooltip: {
					backdropFilter: "blur(6px)",
					background: alpha(palette.neutral[900], 0.8),
				},
			},
		},
		MuiButton: {
			styleOverrides: {
				root: {
					"&:hover": {
						backgroundColor: palette.primary.darkest,
						color: palette.primary.lightest,
					},
				},
			},
		},
	};
};
