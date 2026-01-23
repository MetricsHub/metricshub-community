import React from "react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vscDarkPlus, vs } from "react-syntax-highlighter/dist/esm/styles/prism";
import { useTheme } from "@mui/material";
import TruncatedText from "./TruncatedText";

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
		<TruncatedText
			text={name}
			sx={{
				// Target the inner container of SyntaxHighlighter
				"& > span": {
					margin: "0 !important",
					padding: "4px 8px !important",
					borderRadius: "4px",
					backgroundColor: "transparent !important",
					border: "none !important",
					boxShadow: "none !important",
					display: "inline !important",
				},
				// Target the inner code/span elements
				"& code, & span span": {
					fontFamily: isDark
						? "inherit"
						: "Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace !important",
					fontSize: "0.85rem",
					border: "none !important",
					boxShadow: "none !important",
				},
				width: "100%",
			}}
		>
			<SyntaxHighlighter
				language="javascript"
				style={isDark ? vscDarkPlus : vs}
				PreTag="span"
				CodeTag="span"
				customStyle={{
					margin: 0,
					padding: "0",
					borderRadius: "4px",
					fontSize: "0.85em",
					backgroundColor: isDark ? "#1e1e1e" : "#f5f5f5",
					border: "none",
					boxShadow: "none",
					display: "inline",
					...(isDark
						? {}
						: {
								fontFamily: "Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace",
								fontWeight: 400,
							}),
				}}
				codeTagProps={{
					style: {
						backgroundColor: "transparent",
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
		</TruncatedText>
	);
};

export default React.memo(MetricNameHighlighter);
