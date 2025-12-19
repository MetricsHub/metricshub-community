import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material";
import CopyButton from "./CopyButton";
import { createTheme as createMetricsHubTheme } from "../../theme";

const setClipboard = (value) =>
	Object.defineProperty(navigator, "clipboard", {
		value,
		writable: true,
		configurable: true,
	});

const originalClipboard = navigator.clipboard;
const theme = createMetricsHubTheme({
	direction: "ltr",
	paletteMode: "light",
	responsiveFontSizes: false,
});

const renderCopyButton = (props) =>
	render(
		<ThemeProvider theme={theme}>
			<CopyButton {...props} />
		</ThemeProvider>,
	);

describe("CopyButton", () => {
	beforeEach(() => {
		vi.useFakeTimers();
		setClipboard({
			writeText: vi.fn().mockResolvedValue(),
		});
	});

	afterEach(() => {
		vi.useRealTimers();
		setClipboard(originalClipboard);
		vi.restoreAllMocks();
	});

	it("copies content and shows success state temporarily", async () => {
		const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync });
		renderCopyButton({ content: "Hello world" });

		const button = screen.getByRole("button");
		expect(button).toHaveAttribute("title", "Copy message");
		expect(screen.getByTestId("ContentCopyIcon")).toBeInTheDocument();

		await user.click(button);

		expect(navigator.clipboard.writeText).toHaveBeenCalledWith("Hello world");
		expect(button).toHaveAttribute("title", "Copied!");
		expect(screen.getByTestId("CheckIcon")).toBeInTheDocument();

		await vi.runAllTimersAsync();
		expect(button).toHaveAttribute("title", "Copy message");
		expect(screen.getByTestId("ContentCopyIcon")).toBeInTheDocument();
	});

	it("logs an error when the copy action fails and keeps default state", async () => {
		const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTimeAsync });
		const error = new Error("Copy failed");
		const writeText = vi.fn().mockRejectedValue(error);
		setClipboard({ writeText });

		const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

		renderCopyButton({ content: "Failure text" });

		const button = screen.getByRole("button");
		await user.click(button);

		expect(writeText).toHaveBeenCalledWith("Failure text");
		expect(consoleErrorSpy).toHaveBeenCalledWith("Failed to copy:", error);
		expect(button).toHaveAttribute("title", "Copy message");
		expect(screen.getByTestId("ContentCopyIcon")).toBeInTheDocument();
	});
});

