import { describe, it, expect, vi } from "vitest";
import { screen } from "@testing-library/react";
import MonitorsView from "./MonitorsView";
import { renderWithReduxAndRouter } from "../../../../test/test-utils";

vi.mock("./components/ConnectorAccordion", () => ({
	default: () => null,
}));

describe("MonitorsView failed connectors alert", () => {
	it("shows failed connectors alert even when there are no monitor instances", () => {
		renderWithReduxAndRouter(
			<MonitorsView
				connectors={[{ name: "ConnectorA", monitors: [] }]}
				failedConnectors={[{ name: "ConnectorA" }]}
				lastUpdatedAt={Date.now()}
			/>,
		);

		expect(screen.getByText(/The following connectors have failed/i)).toBeInTheDocument();
		expect(screen.getByText(/Please check the detection criteria and logs/i)).toBeInTheDocument();
		expect(screen.getByRole("link", { name: /support/i })).toHaveAttribute(
			"href",
			"https://support.metricshub.com/",
		);
	});

	it("does not show failed connectors alert when no connector failed", () => {
		renderWithReduxAndRouter(
			<MonitorsView
				connectors={[{ name: "ConnectorB", monitors: [] }]}
				failedConnectors={[]}
				lastUpdatedAt={Date.now()}
			/>,
		);

		expect(screen.queryByText(/The following connectors have failed/i)).not.toBeInTheDocument();
	});
});
