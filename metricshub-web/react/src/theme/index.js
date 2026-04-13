import { createTheme as createMetricsHubTheme, responsiveFontSizes } from "@mui/material/styles";
import { buildOptions as buildGeneralOptions } from "./general/options";
import { buildOptions as buildDarkOptions } from "./dark/options";
import { buildOptions as buildLightOptions } from "./light/options";

/**
 * Create a MUI theme instance based on configuration.
 *
 * @param {*} config - Configuration object containing direction, paletteMode, and responsiveFontSizes properties.
 * @returns MUI theme instance.
 */
export const createTheme = (config) => {
	let theme = createMetricsHubTheme(
		// Base options available for both dark and light palette modes
		buildGeneralOptions({
			direction: config.direction,
		}),
		// Options based on selected palette mode, color preset and contrast
		config.paletteMode === "dark" ? buildDarkOptions() : buildLightOptions(),
	);

	if (config.responsiveFontSizes) {
		theme = responsiveFontSizes(theme);
	}

	return theme;
};
