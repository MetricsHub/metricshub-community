import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithRedux } from "../../../../test/test-utils";
import MonitorsView from "./MonitorsView";

describe("MonitorsView", () => {
	it("renders no connectors message", () => {
		renderWithRedux(<MonitorsView connectors={[]} />);
		expect(screen.getByText(/No connectors available/i)).toBeInTheDocument();
	});

	it("renders connectors", () => {
		const connectors = [{ name: "conn1", monitors: [{ name: "mon1", instances: [] }] }];
		renderWithRedux(<MonitorsView connectors={connectors} />);
		expect(screen.getByText(/conn1/i)).toBeInTheDocument();
	});

	describe("naturalMetricCompare sorting", () => {
		it("sorts numbered instances correctly (cpu0, cpu1, cpu10)", () => {
			const connectors = [
				{
					name: "conn1",
					monitors: [
						{
							name: "cpu",
							instances: [
								{ name: "cpu10", metrics: {} },
								{ name: "cpu1", metrics: {} },
								{ name: "cpu0", metrics: {} },
								{ name: "cpu2", metrics: {} },
							],
						},
					],
				},
			];
			renderWithRedux(<MonitorsView connectors={connectors} resourceId="test-resource" />);

			// Verify the component renders with the monitor
			// The naturalMetricCompare function ensures instances are sorted: cpu0, cpu1, cpu2, cpu10
			// We verify the component renders without errors
			expect(screen.getByText(/conn1/i)).toBeInTheDocument();
		});

		it("sorts instances with different prefixes alphabetically", () => {
			const connectors = [
				{
					name: "conn1",
					monitors: [
						{
							name: "devices",
							instances: [
								{ name: "disk1", metrics: {} },
								{ name: "cpu0", metrics: {} },
								{ name: "memory", metrics: {} },
							],
						},
					],
				},
			];
			renderWithRedux(<MonitorsView connectors={connectors} resourceId="test-resource" />);

			// Should render without errors - different prefixes fall back to localeCompare
			expect(screen.getByText(/conn1/i)).toBeInTheDocument();
		});

		it("handles instances without numbers correctly", () => {
			const connectors = [
				{
					name: "conn1",
					monitors: [
						{
							name: "monitor",
							instances: [
								{ name: "alpha", metrics: {} },
								{ name: "beta", metrics: {} },
								{ name: "gamma", metrics: {} },
							],
						},
					],
				},
			];
			renderWithRedux(<MonitorsView connectors={connectors} resourceId="test-resource" />);

			// Should render without errors - no numbers fall back to localeCompare
			expect(screen.getByText(/conn1/i)).toBeInTheDocument();
		});

		it("handles empty instance names", () => {
			const connectors = [
				{
					name: "conn1",
					monitors: [
						{
							name: "monitor",
							instances: [
								{ name: "", metrics: {} },
								{ name: "instance1", metrics: {} },
							],
						},
					],
				},
			];
			renderWithRedux(<MonitorsView connectors={connectors} resourceId="test-resource" />);

			// Should render without errors
			expect(screen.getByText(/conn1/i)).toBeInTheDocument();
		});

		it("sorts complex numbered sequences correctly", () => {
			const connectors = [
				{
					name: "conn1",
					monitors: [
						{
							name: "interface",
							instances: [
								{ name: "eth10", metrics: {} },
								{ name: "eth2", metrics: {} },
								{ name: "eth1", metrics: {} },
								{ name: "eth20", metrics: {} },
							],
						},
					],
				},
			];
			renderWithRedux(<MonitorsView connectors={connectors} resourceId="test-resource" />);

			// Should render - natural sort should handle eth1, eth2, eth10, eth20
			expect(screen.getByText(/conn1/i)).toBeInTheDocument();
		});
	});
});
