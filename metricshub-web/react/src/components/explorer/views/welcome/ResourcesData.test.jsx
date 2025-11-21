import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import ResourcesData from "./ResourcesData";

describe("ResourcesData", () => {
	it("shows empty message when no resources", () => {
		render(<ResourcesData resources={[]} />);
		expect(screen.getByText("No resources")).toBeInTheDocument();
	});

	it("renders resource row with attribute columns", () => {
		const resources = [
			{
				name: "ResA",
				attributes: { "host.name": "host-1", "host.type": "vm", "os.type": "linux" },
			},
		];
		render(<ResourcesData resources={resources} />);
		expect(screen.getByText("ResA")).toBeInTheDocument();
		expect(screen.getByText("host-1")).toBeInTheDocument();
		expect(screen.getByText("vm")).toBeInTheDocument();
		expect(screen.getByText("linux")).toBeInTheDocument();
	});

	it("renders empty cells when attributes missing", () => {
		const resources = [{ name: "ResB", attributes: {} }];
		render(<ResourcesData resources={resources} />);
		expect(screen.getByText("ResB")).toBeInTheDocument();
		// query for empty cell count by role (cells with no text)
		const cells = screen.getAllByRole("cell");
		// Ensure at least one empty cell exists (host.name column)
		expect(cells.some((c) => c.textContent === "")).toBe(true);
	});
});
