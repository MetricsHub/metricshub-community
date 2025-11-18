/**
 * Test file for Auth API
 *
 * This demonstrates how to test API/service classes.
 * Key concepts:
 * - Mock the HTTP client (httpRequest) to prevent real network calls
 * - Test that the API methods call httpRequest with correct parameters
 * - Test both success and error scenarios
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { authApi } from "./index";
import { httpRequest } from "../../utils/axios-request";

// Mock the httpRequest utility
// This replaces the real httpRequest with a mock function
// We can control what it returns in each test
vi.mock("../../utils/axios-request", () => ({
	httpRequest: vi.fn(), // Create a mock function
}));

describe("AuthApi", () => {
	// beforeEach runs before each test to reset mocks
	beforeEach(() => {
		// clearAllMocks() resets call history and return values
		// This ensures tests don't affect each other
		vi.clearAllMocks();
	});

	// Group related tests with describe()
	describe("signIn", () => {
		it("should sign in successfully", async () => {
			// Arrange: Set up what the mock should return
			const mockResponse = {
				data: { token: "mock-jwt-token" },
			};
			// mockResolvedValue() makes the mock return a resolved promise
			httpRequest.mockResolvedValue(mockResponse);

			// Act: Call the method we're testing
			const result = await authApi.signIn({
				username: "testuser",
				password: "testpass",
			});

			// Assert: Check the result
			expect(result).toEqual({ jwt: "mock-jwt-token" });
			// Verify httpRequest was called with correct parameters
			expect(httpRequest).toHaveBeenCalledWith({
				url: "/auth",
				method: "POST",
				data: {
					username: "testuser",
					password: "testpass",
				},
			});
		});

		it("should reject on error", async () => {
			// Arrange: Set up the mock to reject (simulate error)
			const mockError = new Error("Invalid credentials");
			// mockRejectedValue() makes the mock return a rejected promise
			httpRequest.mockRejectedValue(mockError);

			// Act & Assert: Verify the error is propagated
			// expect().rejects.toThrow() checks that the promise rejects with the expected error
			await expect(authApi.signIn({ username: "testuser", password: "wrongpass" })).rejects.toThrow(
				"Invalid credentials",
			);
		});
	});

	describe("me", () => {
		it("should fetch user info successfully", async () => {
			// Arrange
			const mockUser = { id: 1, username: "testuser", email: "test@example.com" };
			const mockResponse = { data: mockUser };
			httpRequest.mockResolvedValue(mockResponse);

			// Act
			const result = await authApi.me();

			// Assert: Check that the method extracts and returns the data correctly
			expect(result).toEqual(mockUser);
			// Verify the correct endpoint was called
			expect(httpRequest).toHaveBeenCalledWith({
				url: "/api/users/me",
				method: "GET",
			});
		});

		it("should reject on error", async () => {
			const mockError = new Error("Unauthorized");
			httpRequest.mockRejectedValue(mockError);

			await expect(authApi.me()).rejects.toThrow("Unauthorized");
		});
	});

	describe("signOut", () => {
		it("should sign out successfully", async () => {
			const mockResponse = { data: { success: true } };
			httpRequest.mockResolvedValue(mockResponse);

			const result = await authApi.signOut();

			expect(result).toEqual({ success: true });
			// Verify DELETE method is used for sign out
			expect(httpRequest).toHaveBeenCalledWith({
				url: "/auth",
				method: "DELETE",
			});
		});

		it("should reject on error", async () => {
			const mockError = new Error("Network error");
			httpRequest.mockRejectedValue(mockError);

			await expect(authApi.signOut()).rejects.toThrow("Network error");
		});
	});
});
