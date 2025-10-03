import { buildTypography } from "./typography";
import { buildComponents } from "./components";

/**
 * Build theme options such as breakpoints, typography, shape, etc.
 *
 * @param {*} config - Configuration object containing direction property.
 * @returns theme options object.
 */
export const buildOptions = (config) => {
	const direction = config.direction || "ltr";

	return {
		breakpoints: {
			values: {
				xs: 0,
				sm: 600,
				md: 900,
				lg: 1200,
				xl: 1440,
			},
		},
		components: buildComponents(),
		direction,
		shape: {
			borderRadius: 8,
		},
		typography: buildTypography(),
	};
};
