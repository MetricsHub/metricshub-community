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
	Button,
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
	appendReasoningToMessage,
} from "../store/slices/chat-slice";
import ChatMessage from "../components/chat/ChatMessage";
import CloseIcon from "@mui/icons-material/Close";
import ArrowForwardIosIcon from "@mui/icons-material/ArrowForwardIos";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

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
	const hasMessages = React.useMemo(() => messages.length > 0, [messages]);
	const [isReasoningPanelOpen, setIsReasoningPanelOpen] = React.useState(false);
	const [highlightedReasoningId, setHighlightedReasoningId] = React.useState(null);
	const reasoningPanelWidth = React.useMemo(() => ({ xs: 260, sm: 300, md: 360 }), []);
	const reasoningPanelPadding = React.useMemo(
		() =>
			Object.fromEntries(
				Object.entries(reasoningPanelWidth).map(([key, value]) => [key, `${value}px`]),
			),
		[reasoningPanelWidth],
	);
	const streamingPreviewRef = React.useRef(null);
	const STREAMING_PREVIEW_MAX_HEIGHT = 120;
	const assistantMessages = React.useMemo(
		() => messages.filter((m) => m.role === "assistant"),
		[messages],
	);
	const assistantMessagesWithKeys = React.useMemo(
		() =>
			assistantMessages.map((msg, index) => ({
				...msg,
				__key: msg.id ?? `assistant-${index}`,
			})),
		[assistantMessages],
	);
	const activeAssistantMessage = React.useMemo(() => {
		if (messages.length === 0) return null;
		const last = messages[messages.length - 1];
		return last.role === "assistant" ? last : null;
	}, [messages]);
	const selectedReasoningMessage = React.useMemo(() => {
		if (!highlightedReasoningId) return null;
		return assistantMessagesWithKeys.find((m) => m.__key === highlightedReasoningId) || null;
	}, [assistantMessagesWithKeys, highlightedReasoningId]);
	const isSelectedStreaming =
		isLoading &&
		activeAssistantMessage &&
		selectedReasoningMessage &&
		activeAssistantMessage.id === selectedReasoningMessage.id;
	const streamingReasoning = React.useMemo(() => {
		const last = messages[messages.length - 1];
		if (!isLoading || !last || last.role !== "assistant") return "";
		return last.reasoning || "";
	}, [isLoading, messages]);

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

	// Keep the streaming preview pinned to the newest tokens
	React.useEffect(() => {
		if (streamingPreviewRef.current && streamingReasoning) {
			streamingPreviewRef.current.scrollTop = streamingPreviewRef.current.scrollHeight;
		}
	}, [streamingReasoning]);

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
		dispatch(
			addMessage({
				message: { role: "assistant", content: "", reasoning: "", id: assistantMessageId },
			}),
		);
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
				onReasoning: (chunk) => {
					dispatch(
						appendReasoningToMessage({
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
							updates: { content: "", reasoning: "" },
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

	const openReasoningPanel = React.useCallback(
		(targetId = null) => {
			setHighlightedReasoningId((current) => {
				const shouldClose = isReasoningPanelOpen && current === targetId;
				if (shouldClose) {
					setIsReasoningPanelOpen(false);
					return null;
				}
				setIsReasoningPanelOpen(true);
				return targetId;
			});
		},
		[isReasoningPanelOpen],
	);

	// Render the TextField for the input area
	const renderInputArea = () => (
		<Box sx={{ maxWidth: "900px", mx: "auto" }}>
			<Paper
				elevation={0}
				sx={{
					p: 1.5,
					borderRadius: 2,
					border: `1px solid ${theme.palette.mode === "dark" ? theme.palette.neutral[700] : theme.palette.neutral[300]}`,
					bgcolor:
						theme.palette.mode === "dark" ? theme.palette.neutral[900] : theme.palette.neutral[50],
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
								padding: "8px 0",
								lineHeight: 1.5,
								color: theme.palette.text.primary,
								"&::placeholder": {
									color: theme.palette.text.secondary,
									opacity: 0.6,
								},
							},
							"& .MuiInputBase-inputMultiline": {
								padding: "8px 0",
								lineHeight: 1.5,
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
					<Typography
						variant="caption"
						sx={{ color: theme.palette.text.secondary, fontSize: "0.75rem" }}
					>
						Generating response...
					</Typography>
				</Box>
			)}
		</Box>
	);

	/**
	 * Render the reasoning panel
	 */
	const renderReasoningPanel = () => {
		const reasoningContent = selectedReasoningMessage?.reasoning?.trim() || "";

		return (
			<Box
				sx={{
					width: "100%",
					borderLeft: `1px solid ${theme.palette.mode === "dark" ? theme.palette.neutral[800] : theme.palette.neutral[200]}`,
					bgcolor:
						theme.palette.mode === "dark" ? theme.palette.neutral[900] : theme.palette.neutral[50],
					display: "flex",
					flexDirection: "column",
					height: "100%",
				}}
			>
				<Box
					sx={{
						display: "flex",
						alignItems: "center",
						justifyContent: "space-between",
						px: 2,
						py: 1.5,
						borderBottom: `1px solid ${
							theme.palette.mode === "dark"
								? theme.palette.neutral[800]
								: theme.palette.neutral[200]
						}`,
					}}
				>
					<Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
						Thoughts
					</Typography>
					<IconButton size="small" onClick={() => setIsReasoningPanelOpen(false)}>
						<CloseIcon fontSize="small" />
					</IconButton>
				</Box>
				<Box
					sx={{
						flex: 1,
						overflowY: "auto",
						p: 2,
						"&::-webkit-scrollbar": {
							width: "8px",
						},
						"&::-webkit-scrollbar-thumb": {
							backgroundColor:
								theme.palette.mode === "dark"
									? theme.palette.neutral[800]
									: theme.palette.neutral[300],
							borderRadius: "4px",
						},
					}}
				>
					{!selectedReasoningMessage ? (
						<Typography variant="body2" sx={{ color: theme.palette.text.secondary }}>
							Select a message to view its thoughts.
						</Typography>
					) : (
						<Paper
							variant="outlined"
							sx={{
								p: 1.5,
								borderColor: theme.palette.primary.main,
								bgcolor:
									theme.palette.mode === "dark"
										? theme.palette.primary.alpha8
										: theme.palette.primary.alpha8,
							}}
						>
							{reasoningContent ? (
								<Box
									sx={{
										color: theme.palette.text.primary,
										lineHeight: 1.5,
										"& p": { m: 0, mb: 1 },
										"& p:last-of-type": { mb: 0 },
										"& ul, & ol": { pl: 3, my: 0 },
										"& code": {
											fontFamily:
												"ui-monospace, SFMono-Regular, SFMono, Menlo, Consolas, monospace",
											fontSize: "0.9em",
										},
									}}
								>
									<ReactMarkdown remarkPlugins={[remarkGfm]}>{reasoningContent}</ReactMarkdown>
								</Box>
							) : (
								<Typography variant="body2" sx={{ color: theme.palette.text.secondary }}>
									No reasoning received for this response yet.
								</Typography>
							)}
							{isSelectedStreaming && (
								<Box sx={{ display: "flex", alignItems: "center", gap: 1, mt: 1 }}>
									<CircularProgress size={12} />
									<Typography variant="caption" sx={{ color: theme.palette.text.secondary }}>
										Streaming reasoning...
									</Typography>
								</Box>
							)}
						</Paper>
					)}
				</Box>
			</Box>
		);
	};

	return (
		<Box
			sx={{
				height: "calc(100vh - 76px)",
				display: "flex",
				flexDirection: "column",
				bgcolor:
					theme.palette.mode === "dark" ? theme.palette.neutral[900] : theme.palette.neutral[50],
			}}
		>
			<Box
				sx={{
					flex: 1,
					display: "flex",
					minHeight: 0,
					pr: isReasoningPanelOpen ? reasoningPanelPadding : 0,
					position: "relative",
				}}
			>
				<Box sx={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
					{/* Messages Container */}
					<Box
						sx={{
							flex: 1,
							overflowY: "auto",
							px: { xs: 2, sm: 3, md: 4 },
							py: 3,
							pr: { xs: 2, sm: 3, md: 4 },
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
						{hasMessages === false ? (
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
									Ask questions about your infrastructure, metrics, monitoring setup, or get help
									with MetricsHub configuration.
								</Typography>
								<Box sx={{ width: "100%", mt: 4 }}>{renderInputArea()}</Box>
							</Box>
						) : (
							<Box sx={{ maxWidth: "900px", mx: "auto" }}>
								{messages.map((msg, index) => {
									const isAssistant = msg.role === "assistant";
									const isStreamingAssistant =
										isLoading && isAssistant && index === messages.length - 1;
									const messageReasoning = msg.reasoning || "";
									const reasoningKey = msg.id ?? `assistant-${index}`;

									return (
										<React.Fragment key={msg.id || index}>
											{isAssistant && (
												<Box
													sx={{
														display: "flex",
														justifyContent: "flex-start",
														ml: { xs: 0, sm: 5 },
														mb: 1,
														gap: 1,
														alignItems: "flex-start",
														flexDirection: "column",
													}}
												>
													<Box sx={{ display: "flex", alignItems: "center", gap: 0.75 }}>
														{isStreamingAssistant && (
															<>
																<CircularProgress size={14} />
															</>
														)}
														<Button
															size="small"
															variant="text"
															endIcon={<ArrowForwardIosIcon fontSize="inherit" />}
															onClick={() => openReasoningPanel(reasoningKey)}
															sx={{
																textTransform: "none",
																minWidth: 0,
																px: 1,
																py: 0.5,
																pl: 1.5,
																gap: 0.5,
															}}
														>
															Thinking
														</Button>
													</Box>
													{isStreamingAssistant && messageReasoning && (
														<Box
															ref={isStreamingAssistant ? streamingPreviewRef : null}
															sx={{
																maxHeight: STREAMING_PREVIEW_MAX_HEIGHT,
																overflow: "hidden",
																overflowY: "auto",
																width: "100%",
																color: theme.palette.text.disabled,
																bgcolor:
																	theme.palette.mode === "dark"
																		? theme.palette.neutral[900]
																		: theme.palette.neutral[50],
																border: "none",
																borderRadius: 1,
																px: 1,
																py: 0.75,
																fontSize: "0.9rem",
																lineHeight: 1.5,
																position: "relative",
																"&:before": {
																	content: "none",
																},
																"&::-webkit-scrollbar": { display: "none" },
																msOverflowStyle: "none",
																scrollbarWidth: "none",
															}}
														>
															<Typography
																variant="body2"
																sx={{
																	whiteSpace: "pre-wrap",
																	lineHeight: 1.5,
																	color: "inherit",
																	m: 0,
																}}
															>
																{messageReasoning}
															</Typography>
														</Box>
													)}
												</Box>
											)}

											<ChatMessage
												role={msg.role}
												content={msg.content}
												isStreaming={
													msg.role === "assistant" && isLoading && index === messages.length - 1
												}
											/>
										</React.Fragment>
									);
								})}
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
					{hasMessages && (
						<Box
							sx={{
								px: { xs: 2, sm: 3, md: 4 },
								py: 2.5,
								pr: { xs: 2, sm: 3, md: 4 },
								borderTop: `1px solid ${
									theme.palette.mode === "dark"
										? theme.palette.neutral[700]
										: theme.palette.neutral[200]
								}`,
							}}
						>
							{renderInputArea()}
						</Box>
					)}
				</Box>
				<Box
					sx={{
						position: "absolute",
						top: 0,
						right: 0,
						height: "100%",
						width: reasoningPanelWidth,
						overflowX: "hidden",
						overflowY: "auto",
						pointerEvents: "none",
					}}
				>
					<Box
						sx={{
							position: "absolute",
							inset: 0,
							transform: isReasoningPanelOpen ? "translateX(0)" : "translateX(100%)",
							opacity: isReasoningPanelOpen ? 1 : 0,
							transition: "transform 0.35s cubic-bezier(0.33, 1, 0.68, 1), opacity 0.25s ease",
							pointerEvents: isReasoningPanelOpen ? "auto" : "none",
						}}
					>
						{renderReasoningPanel()}
					</Box>
				</Box>
			</Box>
		</Box>
	);
}

export default ChatPage;
