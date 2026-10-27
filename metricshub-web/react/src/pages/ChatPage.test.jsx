import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import * as React from "react";
import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { ThemeProvider, createTheme } from "@mui/material/styles";

vi.mock("../components/chat/ChatMessage", () => ({
	default: ({ role, content, isStreaming }) => (
		<div data-testid={`chat-${role}`} data-streaming={isStreaming ? "true" : "false"}>
			{content}
		</div>
	),
}));

vi.mock("../api/chat", () => {
	const streamChat = vi.fn();
	return {
		chatApi: {
			streamChat,
		},
	};
});

import ChatPage from "./ChatPage";
import { chatReducer } from "../store/slices/chat-slice";
import { chatApi } from "../api/chat";

const neutralPalette = {
	50: "#f8fafc",
	200: "#e2e8f0",
	300: "#cbd5e1",
	400: "#94a3b8",
	500: "#64748b",
	600: "#475569",
	700: "#334155",
	800: "#1e293b",
	900: "#0f172a",
};

const createChatStore = (preloadedChatState) =>
	configureStore({
		reducer: { chat: chatReducer },
		preloadedState: preloadedChatState ? { chat: preloadedChatState } : undefined,
	});

const renderChatPage = (options = {}) => {
	const store = createChatStore(options.preloadedChatState);
	const theme = createTheme({ palette: { neutral: neutralPalette } });

	return {
		store,
		...render(
			<Provider store={store}>
				<ThemeProvider theme={theme}>
					<ChatPage />
				</ThemeProvider>
			</Provider>,
		),
	};
};

describe("ChatPage", () => {
	let dateNowSpy;

	beforeEach(() => {
		vi.mocked(chatApi.streamChat).mockReset();
		dateNowSpy = vi.spyOn(Date, "now").mockReturnValue(new Date("2024-01-01T00:00:00Z").getTime());
	});

	afterEach(() => {
		dateNowSpy?.mockRestore();
	});

	it("renders empty state when there are no messages", () => {
		renderChatPage();

		expect(screen.getByText("Start a conversation with M8B")).toBeInTheDocument();
		expect(screen.getByPlaceholderText("Tell me about your systems...")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Send message" })).toBeDisabled();
		expect(screen.queryByRole("button", { name: "Close thoughts" })).not.toBeInTheDocument();
		const hiddenCloseButton = screen.getByRole("button", { name: "Close thoughts", hidden: true });
		expect(hiddenCloseButton.closest("[inert]")).toHaveAttribute("aria-hidden", "true");
	});

	it("sends a message, streams a response, and updates UI state", async () => {
		const user = userEvent.setup();
		const { store } = renderChatPage();

		let handlers;
		const abortController = { abort: vi.fn() };
		vi.mocked(chatApi.streamChat).mockImplementation((_payload, streamHandlers) => {
			handlers = streamHandlers;
			return abortController;
		});

		const input = screen.getByPlaceholderText("Tell me about your systems...");
		await user.type(input, "Hello");
		await user.keyboard("{Enter}");

		await waitFor(() => expect(chatApi.streamChat).toHaveBeenCalledTimes(1));
		expect(chatApi.streamChat).toHaveBeenCalledWith(
			{ message: "Hello", history: [] },
			expect.any(Object),
		);
		expect(store.getState().chat.isLoading).toBe(true);
		expect(screen.getByText("Generating response...")).toBeInTheDocument();
		expect(screen.getByRole("button", { name: "Stop response" })).toBeEnabled();

		await act(async () => {
			handlers?.onChunk?.("Hi there");
		});

		await act(async () => {
			handlers?.onDone?.();
		});

		await waitFor(() => expect(store.getState().chat.isLoading).toBe(false));

		const conversationId = store.getState().chat.currentConversationId;
		const messages = store.getState().chat.conversations[conversationId]?.messages || [];
		expect(messages.map((m) => m.content)).toEqual(["Hello", "Hi there"]);

		expect(screen.getByText("Hello")).toBeInTheDocument();
		expect(screen.getByText("Hi there")).toBeInTheDocument();

		await user.click(screen.getByRole("button", { name: "Thinking" }));
		const closeThoughtsButton = screen.getByRole("button", { name: "Close thoughts" });
		expect(closeThoughtsButton.closest("[aria-hidden]")).toHaveAttribute("aria-hidden", "false");
		expect(closeThoughtsButton.closest("[inert]")).toBeNull();
		await user.click(closeThoughtsButton);
		expect(screen.queryByRole("button", { name: "Close thoughts" })).not.toBeInTheDocument();
		expect(closeThoughtsButton.closest("[inert]")).toHaveAttribute("aria-hidden", "true");
	});
});
