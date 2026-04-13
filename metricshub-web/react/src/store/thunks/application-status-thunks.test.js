/**
 * Test file for Redux thunk (application-status-thunks)
 *
 * This demonstrates how to test async Redux thunks.
 * Key concepts:
 * - Thunks are async functions that can dispatch actions and call APIs
 * - We need to mock the API calls (we don't want real network requests in tests)
 * - We test the Redux store state after dispatching the thunk
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { configureStore } from "@reduxjs/toolkit";
import { fetchApplicationStatus } from "./application-status-thunks";
import { applicationStatusReducer } from "../slices/application-status-slice";
import { statusApi } from "../../api/status";

// Mock the status API module
// vi.mock() replaces the entire module with our mock implementation
// This prevents real API calls during tests
vi.mock("../../api/status", () => ({
	statusApi: {
		getStatus: vi.fn(), // Create a mock function for getStatus
	},
}));

describe("fetchApplicationStatus thunk", () => {
	let store;

	// beforeEach runs before each test
	// This ensures each test starts with a fresh store and clean mocks
	beforeEach(() => {
		// Create a test Redux store with just the reducer we're testing
		// In a real app, the store has multiple reducers, but for testing we only need the one
		store = configureStore({
			reducer: {
				applicationStatus: applicationStatusReducer,
			},
		});
		// Clear all mock function call history between tests
		vi.clearAllMocks();
	});

	it("should fetch application status successfully", async () => {
		// Arrange: Set up the mock to return successful data
		const mockStatus = { status: "running", version: "1.0.0" };
		// mockResolvedValue() makes the mock function return a resolved promise with this value
		statusApi.getStatus.mockResolvedValue(mockStatus);

		// Act: Dispatch the thunk (it's async, so we await it)
		await store.dispatch(fetchApplicationStatus());

		// Assert: Check the store state after the thunk completes
		const state = store.getState().applicationStatus;
		expect(state.data).toEqual(mockStatus);
		expect(state.loading).toBe(false);
		expect(state.error).toBe(null);
		// Verify the API was called exactly once
		expect(statusApi.getStatus).toHaveBeenCalledTimes(1);
	});

	it("should handle fetch error", async () => {
		// Arrange: Set up the mock to reject (simulate API error)
		const errorMessage = "Network error";
		// mockRejectedValue() makes the mock function return a rejected promise
		statusApi.getStatus.mockRejectedValue(new Error(errorMessage));

		// Act: Dispatch the thunk (it will fail)
		await store.dispatch(fetchApplicationStatus());

		// Assert: Check that error state is set correctly
		const state = store.getState().applicationStatus;
		expect(state.data).toBe(null);
		expect(state.loading).toBe(false);
		expect(state.error).toBe(errorMessage);
	});

	it("should handle fetch error without message", async () => {
		// Test edge case: error without a message
		statusApi.getStatus.mockRejectedValue(new Error());

		await store.dispatch(fetchApplicationStatus());

		const state = store.getState().applicationStatus;
		expect(state.loading).toBe(false);
		// Should use a default error message
		expect(state.error).toBe("Failed to fetch application status");
	});

	it("should set loading state during fetch", async () => {
		// This test verifies the loading state is set correctly during the async operation
		// We create a promise that we can control (resolve it when we want)
		let resolvePromise;
		const promise = new Promise((resolve) => {
			resolvePromise = resolve; // Save the resolve function so we can call it later
		});
		// Make the mock return our controllable promise
		statusApi.getStatus.mockReturnValue(promise);

		// Start the thunk (but don't await it yet)
		const dispatchPromise = store.dispatch(fetchApplicationStatus());

		// Check loading state while the promise is still pending
		let state = store.getState().applicationStatus;
		expect(state.loading).toBe(true);

		// Now resolve the promise to complete the thunk
		resolvePromise({ status: "running" });
		await dispatchPromise;

		// Check final state after completion
		state = store.getState().applicationStatus;
		expect(state.loading).toBe(false);
	});
});
