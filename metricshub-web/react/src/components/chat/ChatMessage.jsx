import * as React from "react";
import { Box, Typography, Paper, useTheme } from "@mui/material";
import PersonIcon from "@mui/icons-material/Person";
import SmartToyIcon from "@mui/icons-material/SmartToy";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import CopyButton from "../common/CopyButton";
import StreamingTableScroller from "../common/StreamingTableScroller";

/**
 * Build styles for assistant messages in chat.
 * @param {*} theme  current MUI theme
 * @returns styles object
 */
const assistantMessagesSx = (theme) => ({
	"& > *": {
		marginTop: 0,
		marginBottom: "1em",
		"&:last-child": {
			marginBottom: 0,
		},
	},
	// Headings
	"& h1, & h2, & h3, & h4, & h5, & h6": {
		fontWeight: 600,
		marginTop: "1.5em",
		marginBottom: "0.5em",
		lineHeight: 1.2,
		color: theme.palette.text.primary,
	},
	"& h1": { fontSize: "1.5rem" },
	"& h2": { fontSize: "1.25rem" },
	"& h3": { fontSize: "1.125rem" },
	"& h4, & h5, & h6": { fontSize: "1rem" },
	// Paragraphs
	"& p": {
		margin: 0,
		marginBottom: "1em",
		lineHeight: 1.7,
		fontSize: "0.9375rem",
		color: theme.palette.text.primary,
		"&:last-child": {
			marginBottom: 0,
		},
	},
	// Lists
	"& ul, & ol": {
		margin: "0.5em 0",
		paddingLeft: "1.5em",
		"& li": {
			margin: "0.25em 0",
			lineHeight: 1.7,
		},
	},
	// Code blocks
	"& pre": {
		backgroundColor:
			theme.palette.mode === "dark" ? theme.palette.neutral[900] : theme.palette.neutral[100],
		border: `1px solid ${theme.palette.mode === "dark" ? theme.palette.neutral[700] : theme.palette.neutral[300]}`,
		borderRadius: "4px",
		padding: "0.75em",
		overflow: "auto",
		fontSize: "0.875rem",
		lineHeight: 1.5,
		margin: "0.5em 0",
		"& code": {
			backgroundColor: "transparent",
			padding: 0,
			border: "none",
		},
	},
	// Inline code
	"& code": {
		backgroundColor:
			theme.palette.mode === "dark" ? theme.palette.neutral[900] : theme.palette.neutral[100],
		border: `1px solid ${theme.palette.mode === "dark" ? theme.palette.neutral[700] : theme.palette.neutral[300]}`,
		borderRadius: "3px",
		padding: "0.125em 0.375em",
		fontSize: "0.875em",
		fontFamily: "monospace",
		color: theme.palette.text.primary,
	},
	// Blockquotes
	"& blockquote": {
		borderLeft: `3px solid ${theme.palette.primary.main}`,
		paddingLeft: "1em",
		margin: "0.5em 0",
		color: theme.palette.text.secondary,
		fontStyle: "italic",
	},
	// Links
	"& a": {
		color: theme.palette.primary.main,
		textDecoration: "none",
		"&:hover": {
			textDecoration: "underline",
		},
	},
	// Tables
	"& table": {
		borderCollapse: "collapse",
		width: "max-content",
		minWidth: "100%",
		margin: 0, // The wrapper manages the spacing
		fontSize: "0.875rem",
	},
	"& th, & td": {
		border: `1px solid ${theme.palette.mode === "dark" ? theme.palette.neutral[700] : theme.palette.neutral[300]}`,
		padding: "0.5em",
		textAlign: "left",
		whiteSpace: "nowrap",
	},
	"& th": {
		backgroundColor:
			theme.palette.mode === "dark" ? theme.palette.neutral[900] : theme.palette.neutral[100],
		fontWeight: 600,
	},
	// Horizontal rules
	"& hr": {
		border: "none",
		borderTop: `1px solid ${theme.palette.mode === "dark" ? theme.palette.neutral[700] : theme.palette.neutral[300]}`,
		margin: "1em 0",
	},
	// Strong and emphasis
	"& strong": {
		fontWeight: 600,
		color: theme.palette.text.primary,
	},
	"& em": {
		fontStyle: "italic",
	},
});
/**
 * Chat message component with professional system design style
 */
const ChatMessage = React.memo(({ role, content, isStreaming = false }) => {
	const isUser = React.useMemo(() => role === "user", [role]);
	const theme = useTheme();

	return (
		<Box
			sx={{
				display: "flex",
				flexDirection: "row",
				mb: 4,
				gap: 2,
				alignItems: "flex-start",
				justifyContent: isUser ? "flex-end" : "flex-start",
			}}
		>
			{/* Avatar/Icon - Show on left for assistant, right for user */}
			{!isUser && (
				<Box
					sx={{
						width: 40,
						height: 40,
						borderRadius: "50%",
						display: "flex",
						alignItems: "center",
						justifyContent: "center",
						flexShrink: 0,
						bgcolor:
							theme.palette.mode === "dark"
								? theme.palette.neutral[700]
								: theme.palette.neutral[200],
						color: theme.palette.text.secondary,
					}}
				>
					<SmartToyIcon fontSize="small" />
				</Box>
			)}

			{/* Message Content */}
			<Box sx={{ flex: 1, minWidth: 0, maxWidth: "75%" }}>
				{/* Role Label with Copy Button (for assistant messages) */}
				<Box
					sx={{
						display: "flex",
						alignItems: "center",
						justifyContent: isUser ? "flex-end" : "space-between",
						mb: 0.75,
					}}
				>
					{isUser ? (
						<Typography
							variant="caption"
							sx={{
								fontWeight: 600,
								textTransform: "uppercase",
								letterSpacing: "0.5px",
								fontSize: "0.6875rem",
								color: theme.palette.text.secondary,
							}}
						>
							You
						</Typography>
					) : (
						<>
							<Typography
								variant="caption"
								sx={{
									fontWeight: 600,
									textTransform: "uppercase",
									letterSpacing: "0.5px",
									fontSize: "0.6875rem",
									color: theme.palette.text.secondary,
								}}
							>
								Assistant
							</Typography>
							{content && <CopyButton content={content} />}
						</>
					)}
				</Box>

				{/* Message Bubble */}
				<Paper
					elevation={0}
					sx={{
						p: 2.5,
						bgcolor: isUser
							? theme.palette.primary.main
							: theme.palette.mode === "dark"
								? theme.palette.neutral[800]
								: theme.palette.background.paper,
						borderRadius: 2,
						border: `1px solid ${
							isUser
								? theme.palette.primary.main
								: theme.palette.mode === "dark"
									? theme.palette.neutral[700]
									: theme.palette.neutral[300]
						}`,
						boxShadow: theme.palette.mode === "dark" ? "none" : "0 1px 3px rgba(0, 0, 0, 0.05)",
					}}
				>
					{isUser ? (
						// User messages: plain text
						<Typography
							variant="body1"
							sx={{
								whiteSpace: "pre-wrap",
								wordBreak: "break-word",
								lineHeight: 1.7,
								fontSize: "0.9375rem",
								color: theme.palette.primary.contrastText,
							}}
						>
							{content}
						</Typography>
					) : (
						// Assistant messages: render markdown
						<Box sx={assistantMessagesSx(theme)}>
							<ReactMarkdown
								remarkPlugins={[remarkGfm]}
								components={{
									table: (props) => (
										<StreamingTableScroller isStreaming={isStreaming}>
											<table {...props} />
										</StreamingTableScroller>
									),
								}}
							>
								{content || ""}
							</ReactMarkdown>
							{isStreaming && (
								<Box
									component="span"
									sx={{
										display: "inline-block",
										width: 2,
										height: 16,
										ml: 0.5,
										bgcolor: theme.palette.primary.main,
										animation: "blink 1s infinite",
										"@keyframes blink": {
											"0%, 50%": { opacity: 1 },
											"51%, 100%": { opacity: 0.3 },
										},
									}}
								/>
							)}
						</Box>
					)}
				</Paper>
			</Box>

			{/* Avatar/Icon - Show on right for user */}
			{isUser && (
				<Box
					sx={{
						width: 40,
						height: 40,
						borderRadius: "50%",
						display: "flex",
						alignItems: "center",
						justifyContent: "center",
						flexShrink: 0,
						bgcolor: theme.palette.primary.main,
						color: theme.palette.primary.contrastText,
					}}
				>
					<PersonIcon fontSize="small" />
				</Box>
			)}
		</Box>
	);
});

ChatMessage.displayName = "ChatMessage";

export default ChatMessage;
