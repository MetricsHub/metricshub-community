import * as React from "react";
import {
	Box,
	TextField,
	IconButton,
	Stack,
	Typography,
	Paper,
	CircularProgress,
	Alert,
	useTheme,
} from "@mui/material";
import SendIcon from "@mui/icons-material/Send";
import StopIcon from "@mui/icons-material/Stop";
import SmartToyIcon from "@mui/icons-material/SmartToy";
import { chatApi } from "../api/chat";
import { useAppDispatch, useAppSelector } from "../hooks/store";
import {
	addMessage,
	updateMessage,
	appendToMessage,
	setLoading,
	setError,
	selectCurrentMessages,
	selectChatLoading,
	selectChatError,
	selectCurrentConversationId,
	createConversation,
} from "../store/slices/chat-slice";
import ChatMessage from "../components/chat/ChatMessage";

/**
 * Chat page component with professional system design
 */
function ChatPage() {
	const theme = useTheme();
	const dispatch = useAppDispatch();
	const messages = useAppSelector(selectCurrentMessages);
	const isLoading = useAppSelector(selectChatLoading);
	const error = useAppSelector(selectChatError);
	const currentConversationId = useAppSelector(selectCurrentConversationId);

	const [input, setInput] = React.useState("");
	const [abortController, setAbortController] = React.useState(null);
	const messagesEndRef = React.useRef(null);
	const inputRef = React.useRef(null);

	// Initialize conversation on mount if none exists
	React.useEffect(() => {
		if (!currentConversationId) {
			const newId = `conv_${Date.now()}`;
			dispatch(createConversation({ id: newId }));
		}
	}, [currentConversationId, dispatch]);

	// Auto-scroll to bottom when new messages arrive (throttled to avoid performance issues)
	React.useEffect(() => {
		const timer = setTimeout(() => {
			messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
		}, 100);
		return () => clearTimeout(timer);
	}, [messages]);

	// Focus input on mount
	React.useEffect(() => {
		inputRef.current?.focus();
	}, []);

	/**
	 * Handle sending a message
	 */
	const handleSend = React.useCallback(() => {
		if (!input.trim() || isLoading) return;

		const userMessage = input.trim();
		setInput("");
		dispatch(setError({ error: null }));

		// Ensure we have a conversation
		if (!currentConversationId) {
			const newId = `conv_${Date.now()}`;
			dispatch(createConversation({ id: newId }));
		}

		// Build history from current messages (before adding new ones)
		const history = messages
			.filter((m) => m.role !== "assistant" || m.content)
			.map(({ role, content }) => ({ role, content }));

		// Add user message to Redux store
		dispatch(addMessage({ message: { role: "user", content: userMessage } }));

		// Add placeholder for assistant response
		const assistantMessageId = Date.now();
		dispatch(addMessage({ message: { role: "assistant", content: "", id: assistantMessageId } }));
		dispatch(setLoading({ isLoading: true }));

		// Stream chat response
		const controller = chatApi.streamChat(
			{ message: userMessage, history },
			{
				onChunk: (chunk) => {
					// Append chunk to message in Redux store
					dispatch(
						appendToMessage({
							messageId: assistantMessageId,
							chunk,
						}),
					);
				},
				onDone: () => {
					dispatch(setLoading({ isLoading: false }));
					setAbortController(null);
				},
				onError: (err) => {
					dispatch(setError({ error: err.message || "Failed to get response" }));
					dispatch(setLoading({ isLoading: false }));
					setAbortController(null);
					// Remove the placeholder message on error by clearing its content
					dispatch(
						updateMessage({
							messageId: assistantMessageId,
							updates: { content: "" },
						}),
					);
				},
			},
		);

		setAbortController(controller);
	}, [input, messages, isLoading, currentConversationId, dispatch]);

	/**
	 * Handle Enter key press
	 */
	const handleKeyDown = React.useCallback(
		(e) => {
			if (e.key === "Enter" && !e.shiftKey) {
				e.preventDefault();
				handleSend();
			}
		},
		[handleSend],
	);

	/**
	 * Handle cancel/stop streaming
	 */
	const handleStop = React.useCallback(() => {
		if (abortController) {
			abortController.abort();
			setAbortController(null);
			dispatch(setLoading({ isLoading: false }));
		}
	}, [abortController, dispatch]);

	const renderInputArea = () => (
		<Box sx={{ maxWidth: "900px", mx: "auto" }}>
			<Paper
				elevation={0}
				sx={{
					p: 1.5,
					borderRadius: 2,
					border: `1px solid ${theme.palette.mode === "dark" ? theme.palette.neutral[700] : theme.palette.neutral[300]}`,
					bgcolor: theme.palette.mode === "dark" ? theme.palette.neutral[800] : theme.palette.background.paper,
					transition: "all 0.2s ease",
					"&:focus-within": {
						borderColor: theme.palette.primary.main,
						boxShadow: `0 0 0 2px ${theme.palette.mode === "dark" ? "rgba(38, 125, 244, 0.15)" : "rgba(38, 125, 244, 0.1)"}`,
					},
				}}
			>
				<Stack direction="row" spacing={1.5} alignItems="flex-end">
					<TextField
						inputRef={inputRef}
						fullWidth
						multiline
						maxRows={4}
						placeholder="Type your message..."
						value={input}
						onChange={(e) => setInput(e.target.value)}
						onKeyDown={handleKeyDown}
						disabled={isLoading}
						variant="standard"
						sx={{
							"& .MuiInputBase-root": {
								padding: 0,
								fontSize: "0.9375rem",
								"&::before": {
									display: "none",
								},
								"&::after": {
									display: "none",
								},
							},
							"& .MuiInputBase-input": {
								padding: 0,
								color: theme.palette.text.primary,
								"&::placeholder": {
									color: theme.palette.text.secondary,
									opacity: 0.6,
								},
							},
						}}
					/>
					{isLoading ? (
						<IconButton
							onClick={handleStop}
							sx={{
								mb: 0.5,
								bgcolor: theme.palette.error.main,
								color: theme.palette.error.contrastText,
								"&:hover": {
									bgcolor: theme.palette.error.dark,
								},
								width: 36,
								height: 36,
							}}
						>
							<StopIcon fontSize="small" />
						</IconButton>
					) : (
						<IconButton
							onClick={handleSend}
							disabled={!input.trim()}
							sx={{
								mb: 0.5,
								bgcolor: input.trim() ? theme.palette.primary.main : "transparent",
								color: input.trim() ? theme.palette.primary.contrastText : theme.palette.text.disabled,
								"&:hover": {
									bgcolor: input.trim() ? theme.palette.primary.dark : "transparent",
								},
								"&:disabled": {
									bgcolor: "transparent",
								},
								width: 36,
								height: 36,
								transition: "all 0.2s ease",
							}}
						>
							<SendIcon fontSize="small" />
						</IconButton>
					)}
				</Stack>
			</Paper>
			{isLoading && (
				<Box sx={{ display: "flex", alignItems: "center", gap: 1, mt: 1.5, px: 1 }}>
					<CircularProgress size={14} />
					<Typography variant="caption" sx={{ color: theme.palette.text.secondary, fontSize: "0.75rem" }}>
						Generating response...
					</Typography>
				</Box>
			)}
		</Box>
	);

	return (
		<Box
			sx={{
				height: "calc(100vh - 64px)",
				display: "flex",
				flexDirection: "column",
				bgcolor:
					theme.palette.mode === "dark" ? theme.palette.neutral[900] : theme.palette.neutral[50],
			}}
		>

			{/* Messages Container */}
			<Box
				sx={{
					flex: 1,
					overflowY: "auto",
					px: { xs: 2, sm: 3, md: 4 },
					py: 3,
					"&::-webkit-scrollbar": {
						width: "8px",
					},
					"&::-webkit-scrollbar-track": {
						backgroundColor: "transparent",
					},
					"&::-webkit-scrollbar-thumb": {
						backgroundColor:
							theme.palette.mode === "dark"
								? theme.palette.neutral[700]
								: theme.palette.neutral[400],
						borderRadius: "4px",
						"&:hover": {
							backgroundColor:
								theme.palette.mode === "dark"
									? theme.palette.neutral[600]
									: theme.palette.neutral[500],
						},
					},
				}}
			>
				{messages.length === 0 ? (
					<Box
						sx={{
							display: "flex",
							flexDirection: "column",
							alignItems: "center",
							justifyContent: "center",
							height: "100%",
							textAlign: "center",
							px: 2,
						}}
					>
						<Box
							sx={{
								width: 64,
								height: 64,
								borderRadius: "50%",
								display: "flex",
								alignItems: "center",
								justifyContent: "center",
								bgcolor:
									theme.palette.mode === "dark"
										? theme.palette.neutral[800]
										: theme.palette.neutral[200],
								mb: 3,
							}}
						>
							<SmartToyIcon sx={{ fontSize: 32, color: theme.palette.text.secondary }} />
						</Box>
						<Typography
							variant="h6"
							sx={{
								fontWeight: 600,
								mb: 1,
								color: theme.palette.text.primary,
							}}
						>
							Start a conversation with M8B
						</Typography>
						<Typography
							variant="body2"
							sx={{
								color: theme.palette.text.secondary,
								maxWidth: "400px",
							}}
						>
							Ask questions about your infrastructure, metrics, monitoring setup, or get help with
							MetricsHub configuration.
						</Typography>
						<Box sx={{ width: "100%", mt: 4 }}>{renderInputArea()}</Box>
					</Box>
				) : (
					<Box sx={{ maxWidth: "900px", mx: "auto" }}>
						{messages.map((msg, index) => (
							<ChatMessage
								key={msg.id || index}
								role={msg.role}
								content={msg.content}
								isStreaming={msg.role === "assistant" && isLoading && index === messages.length - 1}
							/>
						))}
					</Box>
				)}
				{error && (
					<Box sx={{ maxWidth: "900px", mx: "auto", mb: 2 }}>
						<Alert
							severity="error"
							sx={{ borderRadius: 2 }}
							onClose={() => dispatch(setError({ error: null }))}
						>
							{error}
						</Alert>
					</Box>
				)}
				<div ref={messagesEndRef} />
			</Box>

			{/* Input Section */}
			{messages.length > 0 && (
				<Box
					sx={{
						px: { xs: 2, sm: 3, md: 4 },
						py: 2.5,
						borderTop: `1px solid ${theme.palette.mode === "dark" ? theme.palette.neutral[700] : theme.palette.neutral[200]}`,
						bgcolor:
							theme.palette.mode === "dark"
								? theme.palette.neutral[900]
								: theme.palette.background.paper,
					}}
				>
					{renderInputArea()}
				</Box>
			)}
		</Box>
	);
}

export default ChatPage;
