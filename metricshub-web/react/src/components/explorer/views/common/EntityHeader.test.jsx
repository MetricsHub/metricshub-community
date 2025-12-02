import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import EntityHeader from "./EntityHeader";

describe("EntityHeader", () => {
	it("renders title", () => {
		render(<EntityHeader title="My Entity" />);
		expect(screen.getByText("My Entity")).toBeInTheDocument();
	});

	it("renders attributes table when attributes provided", () => {
		const attributes = { "host.name": "localhost" };
		render(<EntityHeader title="Entity" attributes={attributes} />);
		expect(screen.getByText("Attributes")).toBeInTheDocument();
		expect(screen.getByText("host.name")).toBeInTheDocument();
		expect(screen.getByText("localhost")).toBeInTheDocument();
	});

	it("renders children", () => {
		render(
			<EntityHeader title="Entity">
				<div data-testid="child">Child Content</div>
			</EntityHeader>,
		);
		expect(screen.getByTestId("child")).toBeInTheDocument();
	});
});
