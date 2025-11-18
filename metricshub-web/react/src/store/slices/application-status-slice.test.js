/**
 * Test file for Redux slice (application-status-slice)
 *
 * This demonstrates how to test Redux reducers.
 * Key concepts:
 * - Reducers are pure functions: (state, action) => newState
 * - We test that reducers return the correct state for different actions
 * - No mocking needed - reducers are pure functions with no side effects
 */

import { describe, it, expect } from "vitest";
import { applicationStatusReducer, clearStatus } from "./application-status-slice";
import { fetchApplicationStatus } from "../thunks/application-status-thunks";

describe("applicationStatusSlice", () => {
	// Define the initial state that the reducer should return
	const initialState = {
		data: null,
		loading: false,
		error: null,
		lastUpdatedAt: null,
	};

	it("should return initial state", () => {
		// When reducer is called with undefined state and unknown action,
		// it should return the initial state
		// This tests the default case of the reducer
		expect(applicationStatusReducer(undefined, { type: "unknown" })).toEqual(initialState);
	});

	it("should handle fetchApplicationStatus.pending", () => {
		// Create an action object matching the pending action type
		// Redux Toolkit automatically creates action types like "applicationStatus/fetchApplicationStatus/pending"
		const action = { type: fetchApplicationStatus.pending.type };
		const state = applicationStatusReducer(initialState, action);

		// When a fetch starts, loading should be true and error should be cleared
		expect(state.loading).toBe(true);
		expect(state.error).toBe(null);
	});

	it("should handle fetchApplicationStatus.fulfilled", () => {
		// Mock data that would come from a successful API call
		const mockData = { status: "running", version: "1.0.0" };
		const action = {
			type: fetchApplicationStatus.fulfilled.type,
			payload: mockData, // Redux Toolkit puts the result in action.payload
		};
		const state = applicationStatusReducer(initialState, action);

		// After successful fetch:
		// - loading should be false
		// - data should contain the fetched data
		// - lastUpdatedAt should be set (timestamp)
		// - error should be null
		expect(state.loading).toBe(false);
		expect(state.data).toEqual(mockData);
		expect(state.lastUpdatedAt).toBeTypeOf("number");
		expect(state.error).toBe(null);
	});

	it("should handle fetchApplicationStatus.rejected", () => {
		// Simulate an error from the API call
		const errorMessage = "Network error";
		const action = {
			type: fetchApplicationStatus.rejected.type,
			payload: errorMessage, // Error message is in payload
		};
		const state = applicationStatusReducer(initialState, action);

		// After failed fetch:
		// - loading should be false
		// - error should contain the error message
		// - data should remain null
		expect(state.loading).toBe(false);
		expect(state.error).toBe(errorMessage);
		expect(state.data).toBe(null);
	});

	it("should handle clearStatus", () => {
		// Start with a state that has data and errors
		const stateWithData = {
			data: { status: "running" },
			loading: false,
			error: "Some error",
			lastUpdatedAt: Date.now(),
		};
		// clearStatus() is a regular action creator (not async)
		const action = clearStatus();
		const state = applicationStatusReducer(stateWithData, action);

		// clearStatus should reset everything back to initial state
		expect(state.data).toBe(null);
		expect(state.error).toBe(null);
		expect(state.loading).toBe(false);
		expect(state.lastUpdatedAt).toBe(null);
	});

	it("should handle rejected without payload", () => {
		// Test edge case: what if rejected action has no payload?
		const action = {
			type: fetchApplicationStatus.rejected.type,
			// No payload provided
		};
		const state = applicationStatusReducer(initialState, action);

		// Should use a default error message
		expect(state.loading).toBe(false);
		expect(state.error).toBe("Unable to fetch application status");
	});
});
