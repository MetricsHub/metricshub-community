import { inputLabelClasses } from "@mui/material";

/**
 * Build component style overrides and default props.
 *
 * @returns component style overrides and default props.
 */
export const buildComponents = () => {
	return {
		MuiLink: {
			styleOverrides: {
				root: {
					transition: "color 0.4s ease, background-color 0.4s ease",
				},
			},
		},
		MuiBreadcrumbs: {
			styleOverrides: {
				root: {
					transition: "color 0.4s ease",
				},
				separator: {
					transition: "color 0.4s ease",
				},
			},
		},
		MuiDivider: {
			styleOverrides: {
				root: {
					transition: "border-color 0.4s ease, background-color 0.4s ease",
				},
			},
		},
		MuiAccordion: {
			styleOverrides: {
				root: {
					transition: "background-color 0.4s ease, border-color 0.4s ease",
					"&:before": {
						transition: "background-color 0.4s ease",
					},
				},
			},
		},
		MuiAccordionSummary: {
			styleOverrides: {
				root: {
					transition: "background-color 0.4s ease, color 0.4s ease",
				},
				expandIconWrapper: {
					transition: "color 0.4s ease",
				},
			},
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
						"background-color 0.4s ease, box-shadow 0.4s ease, border-color 0.4s ease, color 0.4s ease",
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
					transition: "background-color 0.4s ease, color 0.4s ease, transform 0.2s ease-in-out",
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
				root: {
					transition: "background-color 0.4s ease, color 0.4s ease, border-color 0.4s ease",
				},
				input: {
					transition: "color 0.4s ease",
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
					transition: "color 0.4s ease",
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
					transition: "background-color 0.4s ease, color 0.4s ease, border-color 0.4s ease",
					"&:before": {
						display: "none",
					},
					"&:after": {
						transition: "border-bottom-color 0.4s ease",
					},
				},
				input: {
					fontSize: "0.875rem",
					fontWeight: 500,
					lineHeight: "1.7em",
				},
			},
		},
		MuiOutlinedInput: {
			styleOverrides: {
				root: {
					transition: "background-color 0.4s ease, color 0.4s ease",
				},
				notchedOutline: {
					transition: "border-color 0.4s ease",
				},
			},
		},
		MuiInputLabel: {
			styleOverrides: {
				root: {
					transition: "color 0.4s ease",
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
		MuiListItem: {
			styleOverrides: {
				root: {
					transition: "background-color 0.4s ease, color 0.4s ease",
				},
			},
		},
		MuiListItemText: {
			styleOverrides: {
				root: {
					transition: "color 0.4s ease",
				},
			},
		},
		MuiListItemIcon: {
			styleOverrides: {
				root: {
					transition: "color 0.4s ease",
				},
			},
		},
		MuiPaper: {
			styleOverrides: {
				root: {
					backgroundImage: "none",
					transition:
						"background-color 0.4s ease, color 0.4s ease, box-shadow 0.4s ease, border-color 0.4s ease",
				},
			},
		},
		MuiTableCell: {
			styleOverrides: {
				root: {
					transition: "background-color 0.4s ease, color 0.4s ease, border-bottom-color 0.4s ease",
				},
			},
		},
		MuiTableHead: {
			styleOverrides: {
				root: {
					transition: "background-color 0.4s ease",
				},
			},
		},
		MuiTableRow: {
			styleOverrides: {
				root: {
					transition: "background-color 0.4s ease",
				},
			},
		},
		MuiSvgIcon: {
			styleOverrides: {
				root: {
					transition: "color 0.4s ease, fill 0.4s ease",
				},
			},
		},
		MuiTreeItem: {
			styleOverrides: {
				content: {
					transition: "background-color 0.4s ease, color 0.4s ease",
				},
				iconContainer: {
					transition: "color 0.4s ease",
				},
			},
		},
		MuiDataGrid: {
			styleOverrides: {
				columnHeaders: ({ theme }) => ({
					backgroundColor:
						theme.palette.mode === "dark" ? theme.palette.neutral[800] : theme.palette.neutral[100],
				}),
			},
		},
	};
};
