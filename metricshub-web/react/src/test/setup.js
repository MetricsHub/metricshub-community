/**
 * Test Setup File
 *
 * This file runs before all tests and sets up the testing environment.
 * It's configured in vite.config.js as setupFiles: "./src/test/setup.js"
 *
 * What this file does:
 * 1. Imports jest-dom matchers (like toBeInTheDocument())
 * 2. Sets up global mocks to prevent real network calls
 */

import "@testing-library/jest-dom";
import { vi } from "vitest";

// Mock axios/httpRequest to prevent real network calls in tests
// This global mock prevents connection errors when components try to make API calls
// Individual tests can override this by mocking httpRequest in their own test files
vi.mock("../utils/axios-request", () => {
	const mockHttpRequest = vi.fn((options) => {
		// Return a rejected promise by default to prevent real network calls
		// Tests that need specific behavior should mock httpRequest locally
		const error = new Error(
			`[Test] httpRequest was called without being mocked: ${options?.method || "GET"} ${options?.url || ""}`,
		);
		// Mark as a test error so it's clear this is expected in tests
		error.name = "TestNetworkError";
		return Promise.reject(error);
	});

	return {
		httpRequest: mockHttpRequest,
	};
});
