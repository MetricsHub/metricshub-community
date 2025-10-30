import { inputLabelClasses } from "@mui/material";
import PoppinsExtraBoldwoff2 from "../../assets/fonts/Poppins-ExtraBold.woff2";
import PoppinsExtraBoldwoff from "../../assets/fonts/Poppins-ExtraBold.woff";
import PoppinsExtraBoldttf from "../../assets/fonts/Poppins-ExtraBold.ttf";
import PoppinsBoldwoff2 from "../../assets/fonts/Poppins-Bold.woff2";
import PoppinsBoldwoff from "../../assets/fonts/Poppins-Bold.woff";
import PoppinsBoldttf from "../../assets/fonts/Poppins-Bold.ttf";
import PoppinsRegularwoff2 from "../../assets/fonts/Poppins-Regular.woff2";
import PoppinsRegularwoff from "../../assets/fonts/Poppins-Regular.woff";
import PoppinsRegularttf from "../../assets/fonts/Poppins-Regular.ttf";

/**
 * Build component style overrides and default props.
 *
 * @returns component style overrides and default props.
 */
export const buildComponents = () => {
	return {
		MuiCssBaseline: {
			styleOverrides: `
				@font-face {
					font-family: 'Poppins';
					src: url(${PoppinsExtraBoldwoff2}) format('woff2'),
						url(${PoppinsExtraBoldwoff}) format('woff'),
						url(${PoppinsExtraBoldttf}) format('truetype'),
						local('Helvetica');
					font-weight: 800;
					font-style: normal;
					font-display: swap;
				}	
				@font-face {
					font-family: 'Poppins';
					src: url(${PoppinsBoldwoff2}) format('woff2'),
						url(${PoppinsBoldwoff}) format('woff'),
						url(${PoppinsBoldttf}) format('truetype'),
						local('Helvetica');
					font-weight: 700;
					font-style: normal;
					font-display: swap;
				}
				@font-face {
					font-family: 'Poppins';
					src: url(${PoppinsRegularwoff2}) format('woff2'),
						url(${PoppinsRegularwoff}) format('woff'),
						url(${PoppinsRegularttf}) format('truetype'),
						local('Helvetica');
					font-weight: 400;
					font-style: normal;
					font-display: swap;
				}`,
		},
		MuiTextField: {
			defaultProps: {
				variant: "filled",
			},
		},
		MuiButton: {
			styleOverrides: {
				root: {
					borderRadius: "1.625em",
					textTransform: "none",
					fontWeight: 500,
					padding: ".5em .85em",
					transition:
						"background-color 250ms cubic-bezier(0.4, 0, 0.2, 1), " +
						"box-shadow 250ms cubic-bezier(0.4, 0, 0.2, 1), " +
						"border-color 250ms cubic-bezier(0.4, 0, 0.2, 1), " +
						"color 250ms cubic-bezier(0.4, 0, 0.2, 1)",
				},
				contained: ({ theme }) => ({
					"&.Mui-disabled": {
						backgroundColor: theme.palette.action.disabledBackground,
						color: theme.palette.action.disabled,
						boxShadow: "none",
						border: "1px solid",
						borderColor: theme.palette.divider,
					},
				}),
				sizeSmall: {
					fontSize: "0.875rem",
					padding: "0.5em 1.2em",
				},
				sizeMedium: {
					fontSize: "0.9375rem",
					padding: "0.6em 1.35em",
				},
				sizeLarge: {
					fontSize: "1rem",
					padding: "0.75em 1.7em",
				},
			},
		},
		MuiIconButton: {
			defaultProps: {
				size: "medium",
			},
			styleOverrides: {
				root: {
					borderRadius: "0.5em",
					padding: "0.25em",
					transition: "all 0.2s ease-in-out",
					"&:hover": {
						backgroundColor: "rgba(0,0,0,0.05)",
					},
					"&:active": {
						transform: "scale(0.92)",
					},
				},
			},
		},
		MuiInputBase: {
			styleOverrides: {
				input: {
					"&::placeholder": {
						opacity: 1,
					},
				},
			},
		},
		MuiInput: {
			styleOverrides: {
				input: {
					fontSize: "0.875rem",
					fontWeight: 500,
					lineHeight: "1.7em",
				},
			},
		},
		MuiFilledInput: {
			styleOverrides: {
				root: {
					backgroundColor: "transparent",
					borderRadius: 8,
					borderStyle: "solid",
					borderWidth: 1,
					overflow: "hidden",
					"&:before": {
						display: "none",
					},
					"&:after": {
						display: "none",
					},
				},
				input: {
					fontSize: "0.875rem",
					fontWeight: 500,
					lineHeight: "1.7em",
				},
			},
		},
		MuiFormLabel: {
			styleOverrides: {
				root: {
					fontSize: "0.875rem",
					fontWeight: 500,
					[`&.${inputLabelClasses.filled}`]: {
						transform: "translate(12px, 18px) scale(1)",
					},
					[`&.${inputLabelClasses.shrink}`]: {
						[`&.${inputLabelClasses.standard}`]: {
							transform: "translate(0, -1.5px) scale(0.85)",
						},
						[`&.${inputLabelClasses.filled}`]: {
							transform: "translate(12px, 6px) scale(0.85)",
						},
						[`&.${inputLabelClasses.outlined}`]: {
							transform: "translate(14px, -9px) scale(0.85)",
						},
					},
				},
			},
		},
		MuiPaper: {
			styleOverrides: {
				root: {
					backgroundImage: "none",
				},
			},
		},
	};
};
