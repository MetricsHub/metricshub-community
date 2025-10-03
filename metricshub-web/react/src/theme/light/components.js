import { filledInputClasses } from "@mui/material";

/**
 * Create component style overrides and default props.
 *
 * @param {*} param0 - Object containing the palette property.
 * @returns {object} - The component style overrides and default props.
 */
export const buildComponents = ({ palette }) => {
	return {
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
