import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import ResourceGroupsData from "./ResourceGroupsData";

describe("ResourceGroupsData", () => {
	it("shows empty message when no groups", () => {
		render(<ResourceGroupsData resourceGroups={[]} />);
		expect(screen.getByText("No resource groups")).toBeInTheDocument();
	});

	it("renders group label with env and owner attributes", () => {
		const groups = [
			{
				name: "GroupA",
				attributes: { env: "prod", owner: "team-x" },
				resources: [{ name: "r1" }, { name: "r2" }],
			},
		];
		render(<ResourceGroupsData resourceGroups={groups} />);
		expect(screen.getByText(/GroupA \(env: prod\) \(owner: team-x\)/)).toBeInTheDocument();
		expect(screen.getByText("2")).toBeInTheDocument();
	});

	it("renders group without attributes using plain name and counts zero resources when undefined", () => {
		const groups = [{ name: "GroupB" }];
		render(<ResourceGroupsData resourceGroups={groups} />);
		expect(screen.getByText("GroupB")).toBeInTheDocument();
		expect(screen.getByText("0")).toBeInTheDocument();
	});
});
