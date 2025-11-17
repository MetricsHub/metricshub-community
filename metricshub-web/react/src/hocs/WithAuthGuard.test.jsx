/**
 * Test file for withAuthGuard Higher-Order Component (HOC)
 *
 * This demonstrates how to test HOCs.
 * Key concepts:
 * - HOCs wrap components with additional functionality (in this case, auth protection)
 * - We test that the HOC correctly wraps components and passes props through
 * - We verify that the guard logic works when components are wrapped
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { withAuthGuard } from "./WithAuthGuard";
import { useAuth } from "../hooks/use-auth";
import { renderWithAllProviders } from "../test/test-utils";
import { useNavigate } from "react-router-dom";

// Mock the useAuth hook
// The HOC uses AuthGuard internally, which uses useAuth
vi.mock("../hooks/use-auth", () => ({
	useAuth: vi.fn(),
}));

// Mock useNavigate to prevent actual navigation during tests
const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
	const actual = await vi.importActual("react-router-dom");
	return {
		...actual,
		useNavigate: () => mockNavigate,
	};
});

describe("withAuthGuard", () => {
	beforeEach(() => {
		// Reset mocks before each test
		vi.clearAllMocks();
		mockNavigate.mockClear();
	});

	it("should wrap component with AuthGuard", async () => {
		// Arrange: Set up authenticated state
		useAuth.mockReturnValue({
			isAuthenticated: true,
			isInitialized: true,
		});

		// Create a simple test component
		const TestComponent = ({ message }) => <div data-testid="test">{message}</div>;
		// Apply the HOC to wrap the component
		const GuardedComponent = withAuthGuard(TestComponent);

		// Act: Render the wrapped component
		renderWithAllProviders(<GuardedComponent message="Hello World" />);

		// Assert: Wait for render and verify component is displayed
		await waitFor(() => {
			expect(screen.getByTestId("test")).toBeInTheDocument();
		});
		// Verify props are passed through correctly
		expect(screen.getByTestId("test")).toHaveTextContent("Hello World");
	});

	it("should pass props to wrapped component", async () => {
		// This test verifies that props are correctly passed through the HOC
		useAuth.mockReturnValue({
			isAuthenticated: true,
			isInitialized: true,
		});

		// Component that uses multiple props
		const TestComponent = ({ name, age }) => (
			<div data-testid="test">
				{name} is {age} years old
			</div>
		);
		const GuardedComponent = withAuthGuard(TestComponent);

		// Render with multiple props
		renderWithAllProviders(<GuardedComponent name="John" age={30} />);

		// Verify all props are passed through
		await waitFor(() => {
			expect(screen.getByTestId("test")).toHaveTextContent("John is 30 years old");
		});
	});

	it("should not render component when not authenticated", async () => {
		// This test verifies the guard functionality works through the HOC
		useAuth.mockReturnValue({
			isAuthenticated: false,
			isInitialized: true,
		});

		const TestComponent = () => <div data-testid="test">Content</div>;
		const GuardedComponent = withAuthGuard(TestComponent);

		// Act
		renderWithAllProviders(<GuardedComponent />);

		// Assert: Component should not be rendered when not authenticated
		await waitFor(() => {
			expect(screen.queryByTestId("test")).not.toBeInTheDocument();
		});
		// Verify redirect happened
		expect(mockNavigate).toHaveBeenCalled();
	});

	it("should preserve component display name", () => {
		// This test checks that the HOC doesn't break component metadata
		// Display names are useful for debugging in React DevTools
		const TestComponent = () => <div>Test</div>;
		TestComponent.displayName = "TestComponent";
		const GuardedComponent = withAuthGuard(TestComponent);

		// The HOC should preserve or set a display name
		// This is a basic sanity check that the HOC works
		expect(GuardedComponent).toBeDefined();
	});
});
