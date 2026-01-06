import React from "react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vscDarkPlus, vs } from "react-syntax-highlighter/dist/esm/styles/prism";
import { Box, useTheme } from "@mui/material";

/**
 * Renders a metric name with syntax highlighting.
 *
 * @param {object} props - Component props
 * @param {string} props.name - The metric name to highlight
 */
const MetricNameHighlighter = ({ name }) => {
	const theme = useTheme();
	const isDark = theme.palette.mode === "dark";

	return (
		<Box
			className="metric-name-highlighter"
			sx={{
				"& pre": {
					margin: "0 !important",
					padding: "4px 8px !important",
					borderRadius: "4px",
					backgroundColor: "transparent !important",
					border: "none !important",
					boxShadow: "none !important",
				},
				"& code": {
					fontFamily: isDark
						? "inherit"
						: "Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace !important",
					fontSize: "0.85rem",
					border: "none !important",
					boxShadow: "none !important",
					//fontWeight: isDark ? "inherit" : "400 !important",
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
					backgroundColor: isDark ? "#1e1e1e" : "#f5f5f5",
					border: "none",
					boxShadow: "none",
					...(isDark
						? {}
						: {
							fontFamily: "Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace",
							fontWeight: 400,
						}),
				}}
				codeTagProps={{
					style: {
						border: "none",
						boxShadow: "none",
						...(isDark
							? {}
							: {
								fontFamily: "Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace",
								fontWeight: 400,
							}),
					},
				}}
				wrapLongLines={false}
			>
				{name}
			</SyntaxHighlighter>
		</Box>
	);
};

export default React.memo(MetricNameHighlighter);
