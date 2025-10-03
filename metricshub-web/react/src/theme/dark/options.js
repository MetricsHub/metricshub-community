import { buildComponents } from "./components";
import { buildPalette } from "./palette";

/**
 * Build theme options with palette and components.
 *
 * @returns theme options object.
 */
export const buildOptions = () => {
	const palette = buildPalette();
	const components = buildComponents({ palette });

	return {
		components,
		palette,
	};
};
