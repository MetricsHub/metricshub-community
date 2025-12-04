import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import ResourcesTable from "./ResourcesTable";

describe("ResourcesTable", () => {
	it("renders empty state", () => {
		render(<ResourcesTable resources={[]} />);
		expect(screen.getByText("No resources")).toBeInTheDocument();
	});

	it("renders resources", () => {
		const resources = [{ name: "res1", attributes: { "host.name": "host1" } }];
		render(<ResourcesTable resources={resources} />);
		expect(screen.getByText("res1")).toBeInTheDocument();
		expect(screen.getByText("host1")).toBeInTheDocument();
	});

	it("handles click", () => {
		const onClick = vi.fn();
		const resources = [{ name: "res1" }];
		render(<ResourcesTable resources={resources} onResourceClick={onClick} />);
		fireEvent.click(screen.getByText("res1"));
		expect(onClick).toHaveBeenCalledWith(resources[0]);
	});
});
