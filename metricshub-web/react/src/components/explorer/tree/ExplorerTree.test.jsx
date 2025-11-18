/**
 * Component tests for ExplorerTree
 */
import { describe, it, expect } from "vitest";
import * as React from "react";
import { screen } from "@testing-library/react";
import ExplorerTree from "./ExplorerTree";
import { renderWithReduxAndRouter } from "../../../test/test-utils";

const mockHierarchy = {
	name: "RootGroup",
	type: "resource-group",
	resources: [{ name: "Res-1", type: "resource" }],
	resourceGroups: [{ name: "SubGroupA", type: "resource-group", children: [] }],
};

describe("ExplorerTree", () => {
	it("renders child nodes from merged collections", async () => {
		const initialState = {
			explorer: {
				hierarchy: mockHierarchy,
				loading: false,
				error: null,
				lastUpdatedAt: Date.now(),
			},
		};

		renderWithReduxAndRouter(<ExplorerTree />, { initialState });

		// Root label
		expect(await screen.findByText("RootGroup")).toBeInTheDocument();
		// Because root is expanded by default, children should be visible
		expect(await screen.findByText("Res-1")).toBeInTheDocument();
		expect(await screen.findByText("SubGroupA")).toBeInTheDocument();
	});

	it("shows loading indicator when loading and no data", async () => {
		const initialState = {
			explorer: { hierarchy: null, loading: true, error: null, lastUpdatedAt: null },
		};

		renderWithReduxAndRouter(<ExplorerTree />, { initialState });

		expect(await screen.findByText(/Loading hierarchy/i)).toBeInTheDocument();
	});

	it("renders error message when error and no data", async () => {
		const initialState = {
			explorer: { hierarchy: null, loading: false, error: "boom", lastUpdatedAt: null },
		};
		renderWithReduxAndRouter(<ExplorerTree />, { initialState });
		expect(await screen.findByText(/Failed to load hierarchy: boom/i)).toBeInTheDocument();
	});

	// Removed expand/collapse interaction test per request
});
