import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import WelcomeView from "./WelcomeView";
import { renderWithRedux } from "../../../../test/test-utils.jsx";

function renderWithHierarchy(hierarchyState) {
	const initialState = {
		explorer: {
			hierarchy: hierarchyState.hierarchy,
			loading: hierarchyState.loading,
			error: hierarchyState.error,
		},
	};
	return renderWithRedux(<WelcomeView />, { initialState });
}

describe("WelcomeView", () => {
	it("shows spinner while loading without hierarchy", () => {
		renderWithHierarchy({ hierarchy: null, loading: true, error: null });
		expect(screen.getByRole("progressbar")).toBeInTheDocument();
	});

	it("shows error when load failed and no hierarchy", () => {
		renderWithHierarchy({ hierarchy: null, loading: false, error: "Some error" });
		expect(screen.getByText("Some error")).toBeInTheDocument();
	});

	it("shows no data message when hierarchy absent but not loading or error", () => {
		renderWithHierarchy({ hierarchy: null, loading: false, error: null });
		expect(screen.getByText("No data available.")).toBeInTheDocument();
	});
});
