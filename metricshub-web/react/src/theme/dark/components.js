import { filledInputClasses } from "@mui/material";

/**
 * Build component style overrides and default props.
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
					[`&.${filledInputClasses.disabled}`]: {
						backgroundColor: "transparent",
					},
				},
			},
		},
		MuiFilledInput: {
			styleOverrides: {
				root: {
					borderColor: palette.divider,
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
						backgroundColor: palette.primary.lightest,
						color: palette.primary.darkest,
					},
				},
			},
		},
	};
};
