/**
 * Component tests for ExplorerPage
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import * as React from "react";
import { screen, waitFor, render } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { Provider } from "react-redux";
import ExplorerPage from "./ExplorerPage";
import { createTestStore } from "../test/test-utils";
import { paths } from "../paths";

const mockHierarchy = {
	name: "host.docker.internal",
	type: "agent",
	resourceGroups: [
		{
			name: "Sentry-Thésée",
			type: "resource-group",
			resources: [
				{ name: "babbage", type: "resource" },
				{ name: "dev-hv-01", type: "resource" },
			],
		},
	],
};

// Mock the child components to focus on ExplorerPage logic
vi.mock("../components/explorer/tree/ExplorerTree", () => ({
	default: ({ selectedNodeId, onResourceGroupFocus, onAgentFocus, onResourceFocus }) => (
		<div data-testid="explorer-tree" data-selected-node-id={selectedNodeId || ""}>
			<button
				data-testid="trigger-resource-group"
				onClick={() => onResourceGroupFocus?.("Sentry-Thésée")}
			>
				Trigger Resource Group
			</button>
			<button data-testid="trigger-agent" onClick={() => onAgentFocus?.()}>
				Trigger Agent
			</button>
			<button
				data-testid="trigger-resource"
				onClick={() =>
					onResourceFocus?.(
						{ name: "babbage", type: "resource" },
						{ name: "Sentry-Thésée", type: "resource-group" },
					)
				}
			>
				Trigger Resource
			</button>
		</div>
	),
}));

vi.mock("../components/explorer/views/welcome/WelcomeView", () => ({
	default: () => <div data-testid="welcome-view">Welcome View</div>,
}));

vi.mock("../components/explorer/views/resource-groups/ResourceGroupView", () => ({
	default: () => <div data-testid="resource-group-view">Resource Group View</div>,
}));

vi.mock("../components/explorer/views/resources/ResourceView", () => ({
	default: () => <div data-testid="resource-view">Resource View</div>,
}));

vi.mock("../components/common/AppBreadcrumbs", () => ({
	default: () => <div data-testid="breadcrumbs">Breadcrumbs</div>,
}));

describe("ExplorerPage", () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	const renderExplorerPage = (initialEntries = ["/explorer/welcome"], initialState = {}) => {
		const defaultInitialState = {
			explorer: {
				hierarchy: mockHierarchy,
				loading: false,
				error: null,
				lastUpdatedAt: Date.now(),
				...initialState.explorer,
			},
			...initialState,
		};

		const testStore = createTestStore(defaultInitialState);

		// Custom wrapper with MemoryRouter, Routes, and Redux
		// This matches the route structure from App.jsx
		const Wrapper = () => (
			<Provider store={testStore}>
				<MemoryRouter initialEntries={initialEntries}>
					<Routes>
						<Route path={paths.explorerWelcome} element={<ExplorerPage />} />
						<Route path="/explorer/resource-groups/:name" element={<ExplorerPage />} />
						<Route
							path="/explorer/resource-groups/:group/resources/:resource"
							element={<ExplorerPage />}
						/>
						<Route path="/explorer/resources/:resource" element={<ExplorerPage />} />
					</Routes>
				</MemoryRouter>
			</Provider>
		);

		return render(<Wrapper />);
	};

	it("renders welcome view for welcome route", async () => {
		renderExplorerPage(["/explorer/welcome"]);

		await waitFor(() => {
			expect(screen.getByTestId("welcome-view")).toBeInTheDocument();
		});

		expect(screen.getByTestId("explorer-tree")).toBeInTheDocument();
	});

	it("selects agent node for welcome route", async () => {
		renderExplorerPage(["/explorer/welcome"]);

		await waitFor(() => {
			const tree = screen.getByTestId("explorer-tree");
			expect(tree).toHaveAttribute("data-selected-node-id", "root/host.docker.internal");
		});
	});

	it("renders resource group view for resource group route", async () => {
		renderExplorerPage(["/explorer/resource-groups/Sentry-Thésée"]);

		await waitFor(() => {
			expect(screen.getByTestId("resource-group-view")).toBeInTheDocument();
		});
	});

	it("selects resource group node for resource group route", async () => {
		renderExplorerPage(["/explorer/resource-groups/Sentry-Thésée"]);

		await waitFor(() => {
			const tree = screen.getByTestId("explorer-tree");
			expect(tree).toHaveAttribute(
				"data-selected-node-id",
				"root/host.docker.internal/Sentry-Thésée",
			);
		});
	});

	it("renders resource view for resource route with group", async () => {
		renderExplorerPage(["/explorer/resource-groups/Sentry-Thésée/resources/babbage"]);

		await waitFor(() => {
			expect(screen.getByTestId("resource-view")).toBeInTheDocument();
		});
	});

	it("selects resource node for resource route with group", async () => {
		renderExplorerPage(["/explorer/resource-groups/Sentry-Thésée/resources/babbage"]);

		await waitFor(() => {
			const tree = screen.getByTestId("explorer-tree");
			expect(tree).toHaveAttribute(
				"data-selected-node-id",
				"root/host.docker.internal/Sentry-Thésée/babbage",
			);
		});
	});

	it("renders resource view for resource route without group", async () => {
		renderExplorerPage(["/explorer/resources/babbage"]);

		await waitFor(() => {
			expect(screen.getByTestId("resource-view")).toBeInTheDocument();
		});
	});

	it("selects resource node for resource route without group", async () => {
		renderExplorerPage(["/explorer/resources/babbage"]);

		await waitFor(() => {
			const tree = screen.getByTestId("explorer-tree");
			// When group is not in URL, it should still build the ID with the resource name
			// Note: This might need adjustment based on actual behavior
			expect(tree).toHaveAttribute("data-selected-node-id", "root/host.docker.internal/babbage");
		});
	});

	it("handles URL-encoded resource group names", async () => {
		const encodedName = encodeURIComponent("Sentry-Thésée");
		renderExplorerPage([`/explorer/resource-groups/${encodedName}`]);

		await waitFor(() => {
			const tree = screen.getByTestId("explorer-tree");
			// React Router should decode the URL param automatically
			expect(tree).toHaveAttribute(
				"data-selected-node-id",
				"root/host.docker.internal/Sentry-Thésée",
			);
		});
	});

	it("handles missing hierarchy gracefully", async () => {
		renderExplorerPage(["/explorer/welcome"], {
			explorer: {
				hierarchy: null,
				loading: false,
				error: null,
				lastUpdatedAt: null,
			},
		});

		await waitFor(() => {
			const tree = screen.getByTestId("explorer-tree");
			// Should not have a selected node ID when hierarchy is missing
			expect(tree).toHaveAttribute("data-selected-node-id", "");
		});
	});

	it("selects correct node for different routes", async () => {
		// Test welcome route
		const { unmount } = renderExplorerPage(["/explorer/welcome"]);

		await waitFor(() => {
			const tree = screen.getByTestId("explorer-tree");
			expect(tree).toHaveAttribute("data-selected-node-id", "root/host.docker.internal");
		});

		unmount();

		// Test resource group route
		renderExplorerPage(["/explorer/resource-groups/Sentry-Thésée"]);

		await waitFor(() => {
			const tree = screen.getByTestId("explorer-tree");
			expect(tree).toHaveAttribute(
				"data-selected-node-id",
				"root/host.docker.internal/Sentry-Thésée",
			);
		});
	});
});
