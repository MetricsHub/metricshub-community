/**
 * Test file for QuestionDialog component
 *
 * This demonstrates how to test React components using React Testing Library.
 * Key concepts:
 * - render(): Renders a component into a virtual DOM for testing
 * - screen: Provides queries to find elements in the rendered component
 * - userEvent: Simulates user interactions (clicks, keyboard, etc.)
 * - vi.fn(): Creates a mock function to track calls
 */

import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import QuestionDialog from "./QuestionDialog";

// describe() groups related tests together
describe("QuestionDialog", () => {
	// it() defines a single test case
	it("renders when open is true", () => {
		// render() creates a virtual DOM and renders the component
		// vi.fn() creates a mock function that we can track (check if it was called, how many times, etc.)
		render(<QuestionDialog open={true} onClose={vi.fn()} />);

		// screen.getByRole() finds an element by its accessible role (dialog, button, etc.)
		// This is the preferred way to query elements (better than getByTestId)
		// toBeInTheDocument() is a custom matcher from @testing-library/jest-dom
		expect(screen.getByRole("dialog")).toBeInTheDocument();
	});

	it("does not render when open is false", () => {
		render(<QuestionDialog open={false} onClose={vi.fn()} />);

		// queryByRole returns null if element not found (doesn't throw error)
		// Use queryBy* when checking for absence of elements
		// Use getBy* when you expect the element to exist (throws error if not found)
		expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
	});

	it("displays default title and question", () => {
		render(<QuestionDialog open={true} onClose={vi.fn()} />);

		// getByText() finds elements by their text content
		// We're checking that default values are displayed
		expect(screen.getByText("Question")).toBeInTheDocument();
		expect(screen.getByText("Do you confirm?")).toBeInTheDocument();
	});

	it("displays custom title and question", () => {
		// Testing that custom props override defaults
		render(
			<QuestionDialog
				open={true}
				title="Custom Title"
				question="Custom question?"
				onClose={vi.fn()}
			/>,
		);

		expect(screen.getByText("Custom Title")).toBeInTheDocument();
		expect(screen.getByText("Custom question?")).toBeInTheDocument();
	});

	it("renders action buttons", () => {
		// Create mock functions to track if callbacks are called
		const handleConfirm = vi.fn();
		const handleCancel = vi.fn();
		const actionButtons = [
			{ btnTitle: "Cancel", callback: handleCancel },
			{ btnTitle: "Confirm", callback: handleConfirm },
		];

		render(<QuestionDialog open={true} actionButtons={actionButtons} onClose={vi.fn()} />);

		// getByRole with name option finds button by its accessible name (the text inside)
		const cancelButton = screen.getByRole("button", { name: "Cancel" });
		const confirmButton = screen.getByRole("button", { name: "Confirm" });

		expect(cancelButton).toBeInTheDocument();
		expect(confirmButton).toBeInTheDocument();
	});

	it("calls callback when action button is clicked", async () => {
		// userEvent.setup() is required for userEvent in Vitest
		const user = userEvent.setup();
		const handleConfirm = vi.fn();
		const actionButtons = [{ btnTitle: "Confirm", callback: handleConfirm }];

		render(<QuestionDialog open={true} actionButtons={actionButtons} onClose={vi.fn()} />);

		const confirmButton = screen.getByRole("button", { name: "Confirm" });
		// user.click() simulates a real user click (fires all events like mouseDown, mouseUp, etc.)
		// Must be awaited because user interactions are async
		await user.click(confirmButton);

		// Verify the callback was called exactly once
		expect(handleConfirm).toHaveBeenCalledTimes(1);
	});

	it("calls onClose when dialog is closed", async () => {
		const user = userEvent.setup();
		const handleClose = vi.fn();

		render(<QuestionDialog open={true} onClose={handleClose} />);

		// Simulate pressing the Escape key (common way to close dialogs)
		await user.keyboard("{Escape}");

		expect(handleClose).toHaveBeenCalledTimes(1);
	});

	it("applies button color and variant", () => {
		// Testing that Material-UI props are correctly applied
		const actionButtons = [
			{
				btnTitle: "Delete",
				btnColor: "error",
				btnVariant: "contained",
				callback: vi.fn(),
			},
		];

		render(<QuestionDialog open={true} actionButtons={actionButtons} onClose={vi.fn()} />);

		const button = screen.getByRole("button", { name: "Delete" });
		// Check that Material-UI classes are applied correctly
		expect(button).toHaveClass("MuiButton-contained");
		expect(button).toHaveClass("MuiButton-colorError");
	});
});
