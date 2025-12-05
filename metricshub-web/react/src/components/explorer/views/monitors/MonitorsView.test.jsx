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
});
