import { inputLabelClasses } from "@mui/material";

/**
 * Build component style overrides and default props.
 *
 * @returns component style overrides and default props.
 */
export const buildComponents = () => {
	return {
		MuiTextField: {
			defaultProps: {
				variant: "filled",
			},
		},
		MuiButton: {
			styleOverrides: {
				root: {
					borderRadius: "26px",
					textTransform: "none",
					fontWeight: 500,
					variants: [
						{
							props: { variant: "text", size: "small" },
							style: {
								fontSize: "14px",
								padding: "7px 12px",
							},
						},
						{
							props: { variant: "text", size: "medium" },
							style: {
								fontSize: "15px",
								padding: "9px 16px",
							},
						},
						{
							props: { variant: "text", size: "large" },
							style: {
								fontSize: "16px",
								padding: "12px 16px",
							},
						},
					],
				},
				sizeSmall: {
					fontSize: "14px",
					padding: "4px 12px",
				},
				sizeMedium: {
					fontSize: "15px",
					padding: "6px 16px",
				},
				sizeLarge: {
					fontSize: "16px",
					padding: "8px 22px",
				},
			},
		},
		MuiIconButton: {
			defaultProps: {
				size: "medium",
			},
			styleOverrides: {
				root: {
					borderRadius: 8,
					padding: 4,
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
					fontSize: 14,
					fontWeight: 500,
					lineHeight: "24px",
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
					fontSize: 14,
					fontWeight: 500,
					lineHeight: "24px",
				},
			},
		},
		MuiFormLabel: {
			styleOverrides: {
				root: {
					fontSize: "14px",
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
