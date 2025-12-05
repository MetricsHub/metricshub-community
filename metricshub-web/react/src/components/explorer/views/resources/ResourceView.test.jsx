import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithAllProviders } from "../../../../test/test-utils";
import ResourceView from "./ResourceView";

describe("ResourceView", () => {
	it("renders loading state initially", () => {
		renderWithAllProviders(<ResourceView resourceName="test" />, {
			initialState: {
				explorer: { loading: true, hierarchy: null },
			},
		});
		expect(screen.getByRole("progressbar")).toBeInTheDocument();
	});

	it("renders error state", () => {
		renderWithAllProviders(<ResourceView />, {
			initialState: {
				explorer: { error: "Failed to load", hierarchy: null },
			},
		});
		expect(screen.getByText("Failed to load")).toBeInTheDocument();
	});
});
