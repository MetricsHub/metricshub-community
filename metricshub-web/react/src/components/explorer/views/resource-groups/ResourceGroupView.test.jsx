import { describe, it, expect, vi } from "vitest";
import { screen, fireEvent } from "@testing-library/react";
import ResourceGroupView from "./ResourceGroupView";
import { renderWithRedux } from "../../../../test/test-utils.jsx";

function renderWithHierarchy(hierarchyState, props) {
	const initialState = {
		explorer: {
			hierarchy: hierarchyState.hierarchy,
			loading: hierarchyState.loading,
			error: hierarchyState.error,
		},
	};
	return renderWithRedux(<ResourceGroupView {...props} />, { initialState });
}

describe("ResourceGroupView", () => {
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

	it("shows message when no resource groups exist", () => {
		renderWithHierarchy({ hierarchy: { resourceGroups: [] }, loading: false, error: null });
		expect(screen.getByText("No resource groups available.")).toBeInTheDocument();
	});

	it("renders header, attributes, metrics and resources", () => {
		const hierarchy = {
			resourceGroups: [
				{
					id: "rg-1",
					attributes: { region: "us-east", env: "prod" },
					metrics: [
						{ key: "cpu", value: "10%" },
						{ key: "mem", value: "512MB" },
					],
					resources: [
						{
							key: "res-1",
							name: "res-1",
							attributes: { "host.name": "host1", "host.type": "vm" },
						},
					],
				},
			],
		};

		renderWithHierarchy({ hierarchy, loading: false, error: null });

		expect(screen.getByRole("heading", { level: 4, name: "rg-1" })).toBeInTheDocument();
		expect(screen.getByText("region")).toBeInTheDocument();
		expect(screen.getByText("us-east")).toBeInTheDocument();
		expect(screen.getByText("cpu")).toBeInTheDocument();
		expect(screen.getByText("10%")).toBeInTheDocument();
		expect(screen.getByText("res-1")).toBeInTheDocument();
	});

	it("calls onResourceClick when resource row clicked", () => {
		const hierarchy = {
			resourceGroups: [
				{
					id: "rg-1",
					attributes: {},
					metrics: [],
					resources: [
						{
							key: "res-1",
							name: "res-1",
							attributes: { "host.name": "host1", "host.type": "vm" },
						},
					],
				},
			],
		};
		const onResourceClick = vi.fn();

		renderWithHierarchy({ hierarchy, loading: false, error: null }, { onResourceClick });

		fireEvent.click(screen.getByText("res-1"));
		expect(onResourceClick).toHaveBeenCalledTimes(1);
		expect(onResourceClick).toHaveBeenCalledWith(expect.objectContaining({ key: "res-1" }));
	});
});
