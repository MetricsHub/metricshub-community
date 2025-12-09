import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderWithAllProviders } from "../../../../test/test-utils";
import { authApi } from "../../../../api/auth";
import ResourceView from "./ResourceView";

// Mock the auth API to prevent real API calls and handle async initialization
vi.mock("../../../../api/auth", () => ({
	authApi: {
		me: vi.fn(),
		signIn: vi.fn(),
		signOut: vi.fn(),
	},
}));

describe("ResourceView", () => {
	beforeEach(() => {
		// Reset all mocks before each test
		vi.clearAllMocks();
		// Mock authApi.me to resolve immediately to avoid async warnings
		// This simulates an unauthenticated user (most common test scenario)
		authApi.me.mockRejectedValue(new Error("Not authenticated"));
	});

	it("renders loading state initially", async () => {
		renderWithAllProviders(<ResourceView resourceName="test" />, {
			initialState: {
				explorer: { loading: true, hierarchy: null },
			},
		});

		// Wait for AuthProvider to finish initializing to avoid act() warnings
		await waitFor(() => {
			expect(authApi.me).toHaveBeenCalled();
		});

		expect(screen.getByRole("progressbar")).toBeInTheDocument();
	});

	it("renders error state", async () => {
		renderWithAllProviders(<ResourceView />, {
			initialState: {
				explorer: { error: "Failed to load", hierarchy: null },
			},
		});

		// Wait for AuthProvider to finish initializing to avoid act() warnings
		await waitFor(() => {
			expect(authApi.me).toHaveBeenCalled();
		});

		expect(screen.getByText("Failed to load")).toBeInTheDocument();
	});
});
