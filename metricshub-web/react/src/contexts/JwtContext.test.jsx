/**
 * Test file for JwtContext (React Context)
 *
 * This demonstrates how to test React Context providers.
 * Key concepts:
 * - Context providers manage shared state across components
 * - We test that the provider initializes correctly and provides the right values
 * - We test that context consumers can access the values
 * - We use renderWithRouter to provide necessary providers (Router, etc.)
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import * as React from "react";
import { screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AuthProvider, AuthContext } from "./JwtContext";
import { authApi } from "../api/auth";
import { renderWithRouter } from "../test/test-utils";

// Mock the auth API to prevent real API calls
// The AuthProvider calls authApi.me() on mount, so we need to mock it
vi.mock("../api/auth", () => ({
	authApi: {
		me: vi.fn(),
		signIn: vi.fn(),
		signOut: vi.fn(),
	},
}));

// Test component that reads from the auth context
// This component displays the auth state so we can verify it in tests
const TestComponent = () => {
	const auth = React.useContext(AuthContext);
	return (
		<div>
			<div data-testid="isAuthenticated">{String(auth.isAuthenticated)}</div>
			<div data-testid="isInitialized">{String(auth.isInitialized)}</div>
			<div data-testid="user">{auth.user ? JSON.stringify(auth.user) : "null"}</div>
		</div>
	);
};

// Test component that uses the signIn function from context
const SignInTest = () => {
	const { signIn } = React.useContext(AuthContext);
	return (
		<button onClick={() => signIn("testuser", "password")} data-testid="signin-button">
			Sign In
		</button>
	);
};

describe("AuthProvider", () => {
	beforeEach(() => {
		// Reset all mocks before each test
		vi.clearAllMocks();
		// Clean up any event listeners from previous tests
		window.removeEventListener("auth:logout", vi.fn());
	});

	it("should initialize with unauthenticated state", async () => {
		// Arrange: Mock the API to reject (user not authenticated)
		authApi.me.mockRejectedValue(new Error("Not authenticated"));

		// Act: Render the provider with our test component
		// renderWithRouter provides BrowserRouter which is needed by some components
		renderWithRouter(
			<AuthProvider>
				<TestComponent />
			</AuthProvider>,
		);

		// Assert: Wait for initialization to complete, then check the state
		// waitFor() waits for async operations to complete
		// The provider calls authApi.me() on mount, which is async
		await waitFor(() => {
			expect(screen.getByTestId("isInitialized")).toHaveTextContent("true");
		});

		// After initialization fails, user should be unauthenticated
		expect(screen.getByTestId("isAuthenticated")).toHaveTextContent("false");
		expect(screen.getByTestId("user")).toHaveTextContent("null");
	});

	it("should initialize with authenticated state when user exists", async () => {
		// Arrange: Mock the API to return a user (authenticated)
		const mockUser = { id: 1, username: "testuser" };
		authApi.me.mockResolvedValue(mockUser);

		// Act
		renderWithRouter(
			<AuthProvider>
				<TestComponent />
			</AuthProvider>,
		);

		// Assert: Wait for initialization, then verify authenticated state
		await waitFor(() => {
			expect(screen.getByTestId("isInitialized")).toHaveTextContent("true");
			expect(screen.getByTestId("isAuthenticated")).toHaveTextContent("true");
		});

		// User data should be available
		expect(screen.getByTestId("user")).toHaveTextContent(JSON.stringify(mockUser));
	});

	it("should provide signIn function", async () => {
		// Setup userEvent for simulating user interactions
		const user = userEvent.setup();
		const mockUser = { id: 1, username: "testuser" };
		authApi.me.mockResolvedValue(mockUser);
		authApi.signIn.mockResolvedValue({});

		// Render provider with sign-in test component
		renderWithRouter(
			<AuthProvider>
				<SignInTest />
			</AuthProvider>,
		);

		// Simulate clicking the sign-in button
		const button = screen.getByTestId("signin-button");
		await user.click(button);

		// Verify that signIn was called with correct parameters
		await waitFor(() => {
			expect(authApi.signIn).toHaveBeenCalledWith({
				username: "testuser",
				password: "password",
			});
		});
	});

	it("should handle forced logout event", async () => {
		// This test verifies that the provider listens to logout events
		const mockUser = { id: 1, username: "testuser" };
		authApi.me.mockResolvedValue(mockUser);

		renderWithRouter(
			<AuthProvider>
				<TestComponent />
			</AuthProvider>,
		);

		// Wait for initial authentication
		await waitFor(() => {
			expect(screen.getByTestId("isAuthenticated")).toHaveTextContent("true");
		});

		// Act: Trigger a forced logout event (e.g., from axios interceptor)
		// act() wraps state updates to ensure React processes them correctly
		// This prevents warnings about state updates not being wrapped
		act(() => {
			window.dispatchEvent(new CustomEvent("auth:logout"));
		});

		// Assert: Verify user is logged out
		await waitFor(() => {
			expect(screen.getByTestId("isAuthenticated")).toHaveTextContent("false");
			expect(screen.getByTestId("user")).toHaveTextContent("null");
		});
	});
});
