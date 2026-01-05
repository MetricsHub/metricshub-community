import * as React from "react";
import HoverInfo from "../monitors/components/HoverInfo";
import MetricNameHighlighter from "./MetricNameHighlighter";

/**
 * Shared renderer for metric column headers.
 * Ensures consistent styling, tooltips, and syntax highlighting across different views.
 *
 * @param {string} key - Raw metric key.
 * @param {object} meta - Metric metadata (description, unit, etc.).
 * @param {string} displayUnit - The unit to display in the tooltip.
 * @returns {JSX.Element} The rendered header component.
 */
export const renderMetricHeader = (key, meta, displayUnit) => {
	const lowercaseName = key.toLowerCase();
	return (
		<HoverInfo
			title={lowercaseName}
			description={meta?.description}
			unit={displayUnit || meta?.unit}
			sx={{ display: "inline-block" }}
		>
			<MetricNameHighlighter name={lowercaseName} />
		</HoverInfo>
	);
};
