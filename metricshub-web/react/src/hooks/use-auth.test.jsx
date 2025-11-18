/**
 * Test file for useAuth custom hook
 *
 * This demonstrates how to test custom React hooks.
 * Key concepts:
 * - Hooks can only be called inside React components
 * - We use renderHook() from React Testing Library to test hooks
 * - We provide a wrapper component that supplies the context
 * - We test that the hook returns the correct values from context
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useAuth } from "./use-auth";
import { AuthContext } from "../contexts/JwtContext";
import * as React from "react";

describe("useAuth", () => {
	beforeEach(() => {
		// Reset mocks before each test
		vi.clearAllMocks();
	});

	it("should return auth context value", () => {
		// Arrange: Create mock auth context value
		const mockAuthValue = {
			isAuthenticated: true,
			isInitialized: true,
			user: { id: 1, username: "testuser" },
			signIn: vi.fn(),
			signOut: vi.fn(),
		};

		// Create a wrapper component that provides the context
		// renderHook needs a wrapper to provide React context
		const wrapper = ({ children }) => (
			<AuthContext.Provider value={mockAuthValue}>{children}</AuthContext.Provider>
		);

		// Act: Use renderHook to call the hook
		// renderHook() is a utility that lets us test hooks in isolation
		const { result } = renderHook(() => useAuth(), { wrapper });

		// Assert: Verify the hook returns the context value
		// result.current contains the return value of the hook
		expect(result.current).toEqual(mockAuthValue);
		expect(result.current.isAuthenticated).toBe(true);
		expect(result.current.user).toEqual({ id: 1, username: "testuser" });
	});

	it("should return default context value when not wrapped in provider", () => {
		// This test verifies the hook doesn't crash when used outside a provider
		// In a real app, this would throw an error, but we're testing the hook's behavior
		// The hook should return the default context value (defined in JwtContext)
		const { result } = renderHook(() => useAuth());

		// Assert: The hook should return the default context value
		// This ensures the hook doesn't crash and provides sensible defaults
		expect(result.current).toBeDefined();
		expect(result.current.isAuthenticated).toBeDefined();
		expect(result.current.signIn).toBeDefined();
		expect(result.current.signOut).toBeDefined();
	});

	it("should return context value with signIn and signOut functions", () => {
		// This test specifically verifies that the hook provides the auth functions
		const mockAuthValue = {
			isAuthenticated: true,
			isInitialized: true,
			user: { id: 1, username: "testuser" },
			signIn: vi.fn(),
			signOut: vi.fn(),
		};

		const wrapper = ({ children }) => (
			<AuthContext.Provider value={mockAuthValue}>{children}</AuthContext.Provider>
		);

		const { result } = renderHook(() => useAuth(), { wrapper });

		// Assert: Verify the functions are available and are actually functions
		expect(result.current.signIn).toBeDefined();
		expect(result.current.signOut).toBeDefined();
		expect(typeof result.current.signIn).toBe("function");
		expect(typeof result.current.signOut).toBe("function");
	});
});
