/**
 * Test file for AuthGuard component
 *
 * This demonstrates how to test route guard components.
 * Key concepts:
 * - Guards protect routes by conditionally rendering children
 * - We mock hooks (useAuth, useNavigate) to control the guard's behavior
 * - We test different authentication states and verify correct behavior
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, act } from "@testing-library/react";
import { AuthGuard } from "./AuthGuard";
import { renderWithAllProviders } from "../test/test-utils";
import { useAuth } from "../hooks/use-auth";

// Mock the useAuth hook
// AuthGuard uses this hook to check authentication status
// We'll control what it returns in each test
vi.mock("../hooks/use-auth", () => ({
	useAuth: vi.fn(),
}));

// Mock useNavigate from react-router-dom
// AuthGuard uses navigate() to redirect unauthenticated users
// We mock it to verify redirects happen without actually navigating
const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
	// Import the actual module first
	const actual = await vi.importActual("react-router-dom");
	return {
		...actual, // Keep other exports (like BrowserRouter) as they are
		useNavigate: () => mockNavigate, // Replace useNavigate with our mock
	};
});

describe("AuthGuard", () => {
	beforeEach(() => {
		// Reset all mocks before each test
		vi.clearAllMocks();
		mockNavigate.mockClear();
	});

	it("should render children when authenticated and initialized", async () => {
		// Arrange: Mock useAuth to return authenticated state
		useAuth.mockReturnValue({
			isAuthenticated: true,
			isInitialized: true,
		});

		// Act: Render the guard with protected content
		// renderWithAllProviders provides Redux, Router, and Auth providers
		renderWithAllProviders(
			<AuthGuard>
				<div data-testid="protected-content">Protected Content</div>
			</AuthGuard>,
		);

		// Assert: Wait for the guard to check auth and render children
		// waitFor() is needed because the guard has async logic
		await waitFor(() => {
			expect(screen.getByTestId("protected-content")).toBeInTheDocument();
		});
	});

	it("should not render children when not authenticated", async () => {
		// Arrange: Mock useAuth to return unauthenticated state
		useAuth.mockReturnValue({
			isAuthenticated: false,
			isInitialized: true, // Initialized but not authenticated
		});

		// Act
		renderWithAllProviders(
			<AuthGuard>
				<div data-testid="protected-content">Protected Content</div>
			</AuthGuard>,
		);

		// Assert: Content should not be rendered, and user should be redirected
		await waitFor(() => {
			// queryByTestId returns null if not found (doesn't throw)
			expect(screen.queryByTestId("protected-content")).not.toBeInTheDocument();
		});
		// Verify that navigate was called to redirect to login
		expect(mockNavigate).toHaveBeenCalled();
	});

	it("should not render children when not initialized", async () => {
		// Arrange: Mock useAuth to return uninitialized state
		useAuth.mockReturnValue({
			isAuthenticated: false,
			isInitialized: false, // Not yet initialized
		});

		// Act: Wrap render in act() because AuthProvider initialization causes state updates
		// act() ensures React processes all state updates before we check the result
		await act(async () => {
			renderWithAllProviders(
				<AuthGuard>
					<div data-testid="protected-content">Protected Content</div>
				</AuthGuard>,
			);
		});

		// Assert: When not initialized, the guard waits and doesn't render children
		// The guard's check() function returns early if not initialized
		expect(screen.queryByTestId("protected-content")).not.toBeInTheDocument();
	});

	it("should not render children when not authenticated and not on login page", async () => {
		// This test is similar to the "not authenticated" test
		// It verifies the guard redirects when user is not authenticated
		useAuth.mockReturnValue({
			isAuthenticated: false,
			isInitialized: true,
		});

		renderWithAllProviders(
			<AuthGuard>
				<div data-testid="protected-content">Protected Content</div>
			</AuthGuard>,
		);

		// Assert: Guard should redirect and not render children
		await waitFor(() => {
			expect(screen.queryByTestId("protected-content")).not.toBeInTheDocument();
		});
		// Verify redirect happened
		expect(mockNavigate).toHaveBeenCalled();
	});
});
