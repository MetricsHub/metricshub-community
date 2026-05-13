import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import AgentData from "./AgentData";

vi.mock("../common/EntityHeader", () => ({
	default: ({ children }) => <div>{children}</div>,
}));
vi.mock("../common/MetricsAccordion", () => ({
	default: () => null,
}));
vi.mock("../../../common/MetricCard", () => ({
	default: () => null,
}));

const baseAgent = {
	attributes: {
		"service.name": "MetricsHub Enterprise",
		"os.type": "linux",
		version: "3.9.04-SNAPSHOT",
	},
	metrics: {},
};

describe("AgentData license alerts", () => {
	it("shows enterprise no-valid-license alert when days remaining is null", () => {
		render(
			<AgentData
				agent={baseAgent}
				status={{
					licenseType: "Enterprise",
					licenseDaysRemaining: null,
				}}
			/>,
		);

		expect(screen.getByText(/No valid enterprise license was detected/i)).toBeInTheDocument();
		expect(screen.getByRole("link", { name: /support/i })).toHaveAttribute(
			"href",
			"https://support.metricshub.com/",
		);
	});

	it("shows expired license alert when days remaining is zero or less", () => {
		render(
			<AgentData
				agent={baseAgent}
				status={{
					licenseType: "Enterprise",
					licenseDaysRemaining: 0,
				}}
			/>,
		);

		expect(screen.getByText(/License has expired/i)).toBeInTheDocument();
	});
});
