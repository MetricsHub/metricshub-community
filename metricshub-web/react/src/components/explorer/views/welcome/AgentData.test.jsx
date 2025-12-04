import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import AgentData from "./AgentData";

describe("AgentData", () => {
	it("returns null when agent is null", () => {
		const { container } = render(<AgentData agent={null} totalResources={0} />);
		expect(container.firstChild).toBeNull();
	});

	it("renders title and version preference order (version over cc_version)", () => {
		render(
			<AgentData
				agent={{ attributes: { version: "1.2.3", cc_version: "9.9.9" }, metrics: {} }}
				totalResources={5}
			/>,
		);
		expect(screen.getByText("MetricsHub Community")).toBeInTheDocument();
		expect(screen.getByText(/Version: 1.2.3/)).toBeInTheDocument();
		expect(screen.getByText(/Total resources: 5/)).toBeInTheDocument();
	});

	it("falls back to cc_version when version missing", () => {
		render(<AgentData agent={{ attributes: { cc_version: "4.5.6" }, metrics: {} }} />);
		expect(screen.getByText(/Version: 4.5.6/)).toBeInTheDocument();
	});

	it("renders attribute rows and metrics rows", () => {
		const agent = {
			attributes: { region: "us-east", env: "prod" },
			metrics: {
				cpu: { value: 50, unit: "%", lastUpdate: "2025-11-21" },
				mem: { value: 2048, unit: "MB" },
			},
		};
		render(<AgentData agent={agent} />);
		expect(screen.getByText("region")).toBeInTheDocument();
		expect(screen.getByText("us-east")).toBeInTheDocument();
		expect(screen.getByText("cpu")).toBeInTheDocument();
		expect(screen.getByText("50")).toBeInTheDocument();
		expect(screen.getByText("%")).toBeInTheDocument();
		expect(screen.getByText("2025-11-21")).toBeInTheDocument();
		expect(screen.getByText("mem")).toBeInTheDocument();
		expect(screen.getByText("2048")).toBeInTheDocument();
		expect(screen.getByText("MB")).toBeInTheDocument();
	});
});
