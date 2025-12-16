import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import MetricsTable from "./MetricsTable";

describe("MetricsTable", () => {
	it("renders nothing when metrics is null", () => {
		const { container } = render(<MetricsTable metrics={null} />);
		expect(container).toBeEmptyDOMElement();
	});

	it("renders metrics from array", () => {
		const metrics = [{ name: "cpu", value: 50, unit: "%" }];
		render(<MetricsTable metrics={metrics} />);
		expect(screen.getByText("cpu")).toBeInTheDocument();
		expect(screen.getByText("50.00 %")).toBeInTheDocument();
	});

	it("renders metrics from object", () => {
		const metrics = { cpu: { value: 50, unit: "%" } };
		render(<MetricsTable metrics={metrics} />);
		expect(screen.getByText("cpu")).toBeInTheDocument();
		expect(screen.getByText("50.00 %")).toBeInTheDocument();
	});
});
