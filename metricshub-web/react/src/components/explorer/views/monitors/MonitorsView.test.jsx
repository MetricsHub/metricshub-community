import { describe, it, expect, vi } from "vitest";
import { screen } from "@testing-library/react";
import MonitorsView from "./MonitorsView";
import { renderWithReduxAndRouter } from "../../../../test/test-utils";

vi.mock("./components/ConnectorAccordion", () => ({
	default: ({ connector }) => <div>{`connector:${connector?.name || "unknown"}`}</div>,
}));

describe("MonitorsView", () => {
	it("renders connectors even when they have no monitors", () => {
		renderWithReduxAndRouter(
			<MonitorsView
				connectors={[{ name: "ConnectorA", monitors: [] }]}
				lastUpdatedAt={Date.now()}
			/>,
		);

		expect(screen.getByText("connector:ConnectorA")).toBeInTheDocument();
		expect(
			screen.queryByText(/No connectors available for this resource/i),
		).not.toBeInTheDocument();
	});

	it("shows empty message when there are no connectors", () => {
		renderWithReduxAndRouter(<MonitorsView connectors={[]} lastUpdatedAt={Date.now()} />);

		expect(screen.getByText(/No connectors available for this resource/i)).toBeInTheDocument();
	});
});
