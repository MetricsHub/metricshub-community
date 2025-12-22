import React from "react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vscDarkPlus, vs } from "react-syntax-highlighter/dist/esm/styles/prism";
import { Box, useTheme } from "@mui/material";

const MetricNameHighlighter = ({ name }) => {
	const theme = useTheme();
	const isDark = theme.palette.mode === "dark";

	// We can try to detect if it's a path-like metric (dots) and treat it as a specific language
	// or just generic. 'properties' or 'yaml' might work well for dot notation.
	// Let's try 'javascript' as it handles dots well.

	return (
		<Box
			sx={{
				"& pre": {
					margin: "0 !important",
					padding: "4px 8px !important",
					borderRadius: "4px",
					backgroundColor: "transparent !important", // Let the parent control bg or use the style's bg
					// If we want the style's bg, remove this line.
					// But usually in a table cell we might want it transparent or specific.
					// Let's keep the style's bg but make it fit.
				},
				"& code": {
					fontFamily: "Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace !important",
					fontSize: "0.85rem",
				},
				maxWidth: "100%",
				overflow: "hidden",
			}}
		>
			<SyntaxHighlighter
				language="javascript"
				style={isDark ? vscDarkPlus : vs}
				customStyle={{
					margin: 0,
					padding: "2px 6px",
					borderRadius: "4px",
					fontSize: "0.85em",
					backgroundColor: isDark ? "#1e1e1e" : "#f5f5f5", // Adaptive bg
				}}
				wrapLongLines={false}
			>
				{name}
			</SyntaxHighlighter>
		</Box>
	);
};

export default MetricNameHighlighter;
