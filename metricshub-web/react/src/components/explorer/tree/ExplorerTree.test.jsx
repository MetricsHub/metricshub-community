/**
 * Component tests for ExplorerTree
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import * as React from "react";
import { screen, waitFor } from "@testing-library/react";
import ExplorerTree from "./ExplorerTree";
import { renderWithReduxAndRouter } from "../../../test/test-utils";

const mockHierarchySimple = {
	name: "RootGroup",
	type: "resource-group",
	resources: [{ name: "Res-1", type: "resource" }],
	resourceGroups: [{ name: "SubGroupA", type: "resource-group", children: [] }],
};

describe("ExplorerTree", () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it("renders child nodes from merged collections", async () => {
		const initialState = {
			explorer: {
				hierarchy: mockHierarchySimple,
				loading: false,
				error: null,
				lastUpdatedAt: Date.now(),
			},
		};

		renderWithReduxAndRouter(<ExplorerTree />, { initialState });

		// Root label
		expect(await screen.findByText("RootGroup")).toBeInTheDocument();
		// Because all expandable nodes are expanded by default, children should be visible
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

	it("expands all expandable nodes by default", async () => {
		const initialState = {
			explorer: {
				hierarchy: mockHierarchySimple,
				loading: false,
				error: null,
				lastUpdatedAt: Date.now(),
			},
		};

		renderWithReduxAndRouter(<ExplorerTree />, { initialState });

		// Wait for tree to render
		await waitFor(() => {
			expect(screen.getByText("RootGroup")).toBeInTheDocument();
		});

		// All expandable nodes should be visible
		expect(screen.getByText("RootGroup")).toBeInTheDocument();
		expect(screen.getByText("Res-1")).toBeInTheDocument();
		expect(screen.getByText("SubGroupA")).toBeInTheDocument();
	});

	it("accepts selectedNodeId prop without errors", async () => {
		const initialState = {
			explorer: {
				hierarchy: mockHierarchySimple,
				loading: false,
				error: null,
				lastUpdatedAt: Date.now(),
			},
		};

		const selectedNodeId = "root/RootGroup/Res-1";

		renderWithReduxAndRouter(<ExplorerTree selectedNodeId={selectedNodeId} />, { initialState });

		// Wait for tree to render - should not throw
		await waitFor(() => {
			expect(screen.getByText("RootGroup")).toBeInTheDocument();
		});

		// Tree should render normally
		expect(screen.getByText("RootGroup")).toBeInTheDocument();
	});

	it("handles null selectedNodeId gracefully", async () => {
		const initialState = {
			explorer: {
				hierarchy: mockHierarchySimple,
				loading: false,
				error: null,
				lastUpdatedAt: Date.now(),
			},
		};

		renderWithReduxAndRouter(<ExplorerTree selectedNodeId={null} />, { initialState });

		// Wait for tree to render
		await waitFor(() => {
			expect(screen.getByText("RootGroup")).toBeInTheDocument();
		});

		// Tree should render normally without selection
		expect(screen.getByText("RootGroup")).toBeInTheDocument();
	});
});
