import * as React from "react";
import { Box } from "@mui/material";
import HoverInfo from "./HoverInfo";

/**
 * Converts raw metric entries into utilization parts with percentages.
 * @param {Array<{key: string, value: number}>} entries
 * @returns {Array<{key: string, value: number, pct: number, rawPct: number}>}
 */
export const buildUtilizationParts = (entries) => {
	const parts = entries
		.map(({ key, value: raw }) => {
			// Don't clamp to 1, as some metrics might exceed 100% (e.g. multi-core aggregation issues)
			// or be non-normalized. We normalize them below relative to the total sum.
			const value = typeof raw === "number" ? Math.max(0, raw) : 0;
			return { key, value };
		})
		.filter((p) => p.value >= 0); // Keep 0 values so we know they exist

	if (parts.length === 0) return [];

	const sum = parts.reduce((acc, p) => acc + p.value, 0);
	// Metrics can be in 0-1 range or 0-100 range.
	// We use a small epsilon to handle floating point issues.
	const is0to100 = sum > 1.05;
	const total = is0to100 ? Math.max(sum, 100) : Math.max(sum, 1);

	const result = parts.map((p) => {
		const rawPct = (p.value / total) * 100;
		let pct = Math.round(rawPct);
		// If value is > 0 but rounds to 0, clamp to 1 for visual readability
		if (p.value > 0 && pct === 0) pct = 1;
		return {
			key: p.key,
			value: p.value,
			pct,
			rawPct,
		};
	});

	const currentPctSum = result.reduce((acc, p) => acc + p.pct, 0);
	if (currentPctSum < 100) {
		const noneValue = total - sum;
		const rawPct = (noneValue / total) * 100;
		result.push({
			key: "none",
			value: noneValue,
			pct: 100 - currentPctSum,
			rawPct,
		});
	}

	return result;
};

/**
 * Extracts a display label from a metric key.
 * Handles keys with braces like `system.cpu.utilization{state="idle"}`.
 * @param {string} key
 * @returns {string}
 */
export const colorLabelFromKey = (key) => {
	const braceStart = key.indexOf("{");
	const braceEnd = key.lastIndexOf("}");
	if (braceStart !== -1 && braceEnd > braceStart) {
		const insideBraces = key.slice(braceStart + 1, braceEnd);
		const quoteStart = insideBraces.indexOf('"');
		const quoteEnd = insideBraces.lastIndexOf('"');
		if (quoteStart !== -1 && quoteEnd > quoteStart) {
			return insideBraces.slice(quoteStart + 1, quoteEnd).toLowerCase();
		}
		return insideBraces.toLowerCase();
	}
	return key.substring(key.lastIndexOf(".") + 1).toLowerCase();
};

/**
 * Returns a color function for a given metric label.
 * @param {string} name
 * @returns {(theme: any) => string}
 */
export const colorFor = (name) => {
	const n = name.toLowerCase();
	if (n.includes("used")) return (theme) => theme.palette.primary.main;
	if (n.includes("free")) return (theme) => theme.palette.action.disabled;
	if (n.includes("cache")) return (theme) => theme.palette.warning.main;
	if (n.includes("buffer")) return (theme) => theme.palette.warning.light;
	if (n.includes("idle")) return (theme) => theme.palette.action.disabled;
	if (n.includes("system")) return (theme) => theme.palette.error.main;
	if (n.includes("user")) return (theme) => theme.palette.info.main;
	if (n.includes("nice")) return (theme) => theme.palette.success.main;
	if (n.includes("wait")) return (theme) => theme.palette.warning.dark; // io_wait, iowait
	if (n.includes("steal")) return (theme) => theme.palette.text.primary;
	if (n.includes("irq")) return (theme) => theme.palette.secondary.main; // irq, softirq
	if (n.includes("receive")) return (theme) => theme.palette.success.main;
	if (n.includes("transmit")) return (theme) => theme.palette.secondary.main;
	if (n === "none") return (theme) => theme.palette.action.hover;

	// Fallback: generate a consistent color based on the string hash
	let hash = 0;
	for (let i = 0; i < n.length; i++) {
		hash = n.charCodeAt(i) + ((hash << 5) - hash);
	}
	const hue = Math.abs(hash) % 360;
	return () => `hsl(${hue}, 70%, 50%)`;
};

/**
 * Returns a priority value for sorting metric labels.
 * @param {string} label
 * @returns {number}
 */
export const getPriority = (label) => {
	if (label.includes("user")) return 10;
	if (label.includes("nice")) return 15;
	if (label.includes("system")) return 20;
	if (label.includes("wait")) return 25; // io_wait
	if (label.includes("irq")) return 28;
	if (label.includes("steal")) return 29;
	if (label.includes("used")) return 30;
	if (label.includes("cache")) return 40;
	if (label.includes("buffer")) return 45;
	if (label.includes("free")) return 90;
	if (label.includes("idle")) return 100;
	if (label === "none") return 1000;
	return 50;
};

/**
 * Comparator for utilization parts based on priority and percentage.
 * @param {{key: string, pct: number}} a
 * @param {{key: string, pct: number}} b
 * @returns {number}
 */
export const compareUtilizationParts = (a, b) => {
	const labelA = colorLabelFromKey(a.key);
	const labelB = colorLabelFromKey(b.key);

	const pA = getPriority(labelA);
	const pB = getPriority(labelB);

	if (pA !== pB) return pA - pB;

	return a.pct - b.pct;
};

// Shared empty bar styles - extracted to avoid repetition
const emptyBarSx = {
	position: "relative",
	height: 16,
	borderRadius: 1,
	overflow: "hidden",
	bgcolor: "action.hover",
	display: "flex",
	width: "100%",
};

/**
 * Renders a stacked progress bar for utilization metrics.
 *
 * @param {object} props - Component props
 * @param {Array<{key: string, value: number, pct: number}>} props.parts - Utilization parts to render
 */
const UtilizationStackComponent = ({ parts }) => {
	const sortedParts = React.useMemo(() => {
		if (!Array.isArray(parts) || parts.length === 0) return [];
		return [...parts].sort(compareUtilizationParts);
	}, [parts]);

	const hasNonZero = React.useMemo(() => sortedParts.some((p) => p.value > 0), [sortedParts]);

	const filteredParts = React.useMemo(() => sortedParts.filter((p) => p.value > 0), [sortedParts]);

	if (!Array.isArray(parts) || parts.length === 0 || !hasNonZero) {
		// Render empty bar if no parts or all values are zero
		return <Box sx={emptyBarSx} />;
	}

	return (
		<Box
			sx={{
				position: "relative",
				height: 16,
				borderRadius: 1,
				overflow: "hidden",
				bgcolor: "action.hover",
				display: "flex",
				width: "100%",
				flexGrow: 1,
				alignSelf: "center",
			}}
		>
			{filteredParts.map((p) => {
				const label = colorLabelFromKey(p.key);
				const isNone = p.key === "none";
				const threshold = 12;
				const showText = p.pct > threshold;

				const bar = (
					<Box
						sx={{
							width: "100%",
							height: "100%",
							bgcolor: colorFor(label),
							display: "flex",
							alignItems: "center",
							justifyContent: "center",
						}}
					>
						{showText && (
							<Box
								component="span"
								sx={{
									color: !isNone ? "common.white" : "text.secondary",
									fontSize: "0.7rem",
									fontWeight: 600,
									lineHeight: 1,
									textShadow: !isNone ? "0px 0px 2px rgba(0,0,0,0.5)" : "none",
									userSelect: "none",
								}}
							>
								{p.pct}%
							</Box>
						)}
					</Box>
				);

				return (
					<HoverInfo
						key={p.key}
						label={isNone ? "None" : label}
						value={p.rawPct / 100}
						sx={{
							width: `${p.pct}%`,
						}}
					>
						{bar}
					</HoverInfo>
				);
			})}
		</Box>
	);
};

export const UtilizationStack = React.memo(UtilizationStackComponent);
