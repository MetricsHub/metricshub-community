import { createSlice, createSelector } from "@reduxjs/toolkit";

/** @type {ChatState} */
const initialState = {
	/** @type {Record<string, ChatConversation>} */
	conversations: {},
	/** @type {string|null} */
	currentConversationId: null,
	isLoading: false,
	/** @type {string|null} */
	error: null,
};

const chatSlice = createSlice({
	name: "chat",
	initialState,
	reducers: {
		/**
		 * Create a new conversation
		 * @param {ChatState} state
		 * @param {Object} action
		 * @param {string} action.payload.id - Conversation ID
		 * @param {string} [action.payload.title] - Optional title for the conversation
		 */
		createConversation(state, action) {
			const { id, title } = action.payload;
			const now = Date.now();
			state.conversations[id] = {
				id,
				title: title || "New Conversation",
				messages: [],
				createdAt: now,
				updatedAt: now,
			};
			state.currentConversationId = id;
		},
		/**
		 * Add a message to the current conversation
		 * @param {ChatState} state
		 * @param {Object} action
		 * @param {ChatMessage} action.payload.message - The message to add
		 */
		addMessage(state, action) {
			const { message } = action.payload;
			const conversationId = state.currentConversationId;
			if (!conversationId || !state.conversations[conversationId]) {
				// Create a new conversation if none exists
				const newId = `conv_${Date.now()}`;
				state.conversations[newId] = {
					id: newId,
					title: message.role === "user" ? message.content.substring(0, 50) : "New Conversation",
					messages: [],
					createdAt: Date.now(),
					updatedAt: Date.now(),
				};
				state.currentConversationId = newId;
				state.conversations[newId].messages.push({
					...message,
					reasoning: message.reasoning || "",
					timestamp: message.timestamp || Date.now(),
				});
			} else {
				state.conversations[conversationId].messages.push({
					...message,
					reasoning: message.reasoning || "",
					timestamp: message.timestamp || Date.now(),
				});
				state.conversations[conversationId].updatedAt = Date.now();
			}
		},
		/**
		 * Update a message in the current conversation
		 * @param {ChatState} state
		 * @param {Object} action
		 * @param {number|string} action.payload.messageId - ID of the message to update
		 * @param {Partial<ChatMessage>} action.payload.updates - Updates to apply to the message
		 */
		updateMessage(state, action) {
			const { messageId, updates } = action.payload;
			const conversationId = state.currentConversationId;
			if (!conversationId || !state.conversations[conversationId]) return;

			const conversation = state.conversations[conversationId];
			// Only match messages that have an id matching messageId
			// This prevents matching user messages (which don't have ids) by timestamp
			const messageIndex = conversation.messages.findIndex((msg) => {
				return msg.id !== undefined && msg.id !== null && msg.id === messageId;
			});
			if (messageIndex !== -1) {
				conversation.messages[messageIndex] = {
					...conversation.messages[messageIndex],
					...updates,
				};
				conversation.updatedAt = Date.now();
			}
		},
		/**
		 * Append content to a message (used for streaming)
		 * @param {ChatState} state
		 * @param {Object} action
		 * @param {number|string} action.payload.messageId - ID of the message to update
		 * @param {string} action.payload.chunk - Content chunk to append
		 */
		appendToMessage(state, action) {
			const { messageId, chunk } = action.payload;
			const conversationId = state.currentConversationId;
			if (!conversationId || !state.conversations[conversationId]) return;

			const conversation = state.conversations[conversationId];
			// Only match messages that have an id matching messageId
			// This prevents matching user messages (which don't have ids) by timestamp
			// Assistant messages always have an id, so we only match those
			const messageIndex = conversation.messages.findIndex((msg) => {
				return msg.id !== undefined && msg.id !== null && msg.id === messageId;
			});
			if (messageIndex !== -1) {
				const currentContent = conversation.messages[messageIndex].content || "";
				conversation.messages[messageIndex].content = currentContent + chunk;
				conversation.updatedAt = Date.now();
			}
		},
		/**
		 * Append reasoning text to a message (used for reasoning streaming)
		 * @param {ChatState} state
		 * @param {Object} action
		 * @param {number|string} action.payload.messageId - ID of the message to update
		 * @param {string} action.payload.chunk - Reasoning chunk to append
		 */
		appendReasoningToMessage(state, action) {
			const { messageId, chunk } = action.payload;
			const conversationId = state.currentConversationId;
			if (!conversationId || !state.conversations[conversationId]) return;

			const conversation = state.conversations[conversationId];
			const messageIndex = conversation.messages.findIndex((msg) => {
				return msg.id !== undefined && msg.id !== null && msg.id === messageId;
			});
			if (messageIndex !== -1) {
				const currentReasoning = conversation.messages[messageIndex].reasoning || "";
				conversation.messages[messageIndex].reasoning = currentReasoning + chunk;
				conversation.updatedAt = Date.now();
			}
		},
		/**
		 * Set the current conversation
		 * @param {ChatState} state
		 * @param {Object} action
		 * @param {string} action.payload.conversationId - ID of the conversation to set as current
		 */
		setCurrentConversation(state, action) {
			const { conversationId } = action.payload;
			if (state.conversations[conversationId]) {
				state.currentConversationId = conversationId;
			}
		},
		/**
		 * Delete a conversation
		 * @param {ChatState} state
		 * @param {Object} action
		 * @param {string} action.payload.conversationId - ID of the conversation to delete
		 */
		deleteConversation(state, action) {
			const { conversationId } = action.payload;
			delete state.conversations[conversationId];
			if (state.currentConversationId === conversationId) {
				// Set current to the most recently updated conversation, or null
				const conversations = Object.values(state.conversations);
				if (conversations.length > 0) {
					const mostRecent = conversations.reduce((latest, conv) =>
						conv.updatedAt > latest.updatedAt ? conv : latest,
					);
					state.currentConversationId = mostRecent.id;
				} else {
					state.currentConversationId = null;
				}
			}
		},
		/**
		 * Clear all conversations
		 * @param {ChatState} state
		 */
		clearAllConversations(state) {
			state.conversations = {};
			state.currentConversationId = null;
		},
		/**
		 * Set loading state
		 * @param {ChatState} state
		 * @param {Object} action
		 * @param {boolean} action.payload.isLoading - Loading flag
		 */
		setLoading(state, action) {
			state.isLoading = action.payload.isLoading;
		},
		/**
		 * Set error state
		 * @param {ChatState} state
		 * @param {Object} action
		 * @param {string|null} action.payload.error - Error message or null to clear
		 */
		setError(state, action) {
			state.error = action.payload.error;
		},
	},
});

export const {
	createConversation,
	addMessage,
	updateMessage,
	appendToMessage,
	appendReasoningToMessage,
	setCurrentConversation,
	deleteConversation,
	clearAllConversations,
	setLoading,
	setError,
} = chatSlice.actions;
export const chatReducer = chatSlice.reducer;

// Selectors
const base = (state) => state.chat ?? initialState;

/**
 * Select all conversations
 */
export const selectAllConversations = createSelector([base], (s) => {
	return Object.values(s.conversations).sort((a, b) => b.updatedAt - a.updatedAt);
});

/**
 * Select current conversation ID
 */
export const selectCurrentConversationId = createSelector([base], (s) => s.currentConversationId);

/**
 * Select current conversation
 */
export const selectCurrentConversation = createSelector([base], (s) => {
	if (!s.currentConversationId) return null;
	return s.conversations[s.currentConversationId] || null;
});

/**
 * Select messages from current conversation
 */
export const selectCurrentMessages = createSelector([selectCurrentConversation], (conversation) => {
	return conversation?.messages || [];
});

/**
 * Select conversation by ID
 */
export const selectConversationById = (conversationId) =>
	createSelector([base], (s) => s.conversations[conversationId] || null);

/**
 * Select loading state
 */
export const selectChatLoading = createSelector([base], (s) => s.isLoading);

/**
 * Select error state
 */
export const selectChatError = createSelector([base], (s) => s.error);
