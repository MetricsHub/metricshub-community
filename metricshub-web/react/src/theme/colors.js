import { alpha } from "@mui/material/styles";

// Helper function to add alpha variants to a color
const withAlphas = (color) => ({
	...color,
	alpha4: alpha(color.main, 0.04),
	alpha8: alpha(color.main, 0.08),
	alpha12: alpha(color.main, 0.12),
	alpha30: alpha(color.main, 0.3),
	alpha50: alpha(color.main, 0.5),
});

// MetricsHub Blue color palette
export const blue = withAlphas({
	lightest: "#F8F9FA",
	light: "#9EC5FE",
	main: "#267DF4",
	dark: "#0A58CA",
	darkest: "#212529",
	contrastText: "#FFFFFF",
});

// Neutral gray color palette
export const neutral = {
	50: "#FBFBFB",
	100: "#F8F9FA",
	200: "#E9ECEF",
	300: "#DEE2E6",
	400: "#CED4DA",
	500: "#ADB5BD",
	600: "#6C757D",
	700: "#495057",
	800: "#343A40",
	900: "#212529",
};

// Success green color palette
export const success = withAlphas({
	lightest: "#D1E7DD",
	light: "#A3CFBB",
	main: "#45ce52",
	dark: "#167c4cff",
	darkest: "#0A3622",
	contrastText: "#FFFFFF",
});

// Info cyan color palette
export const info = withAlphas({
	lightest: "#CFF4FC",
	light: "#9EEAF9",
	main: "#0DCAF0",
	dark: "#087990",
	darkest: "#055160",
	contrastText: "#000000",
});

// Warning yellow color palette
export const warning = withAlphas({
	lightest: "#FFF3CD",
	light: "#FFE69C",
	main: "#FFC107",
	dark: "#997404",
	darkest: "#664D03",
	contrastText: "#000000",
});

// Error red color palette
export const error = withAlphas({
	lightest: "#F8D7DA",
	light: "#d87680",
	main: "#E50031",
	dark: "#b30012",
	darkest: "#5a0009",
	contrastText: "#FFFFFF",
});

// Divider colors for light and dark themes
export const divider = {
	light: "#DEE2E6",
	dark: "#2D3748",
};
