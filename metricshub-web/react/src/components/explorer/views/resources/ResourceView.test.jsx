import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import ResourceView from "./ResourceView";
import { renderWithReduxAndRouter } from "../../../../test/test-utils";

describe("ResourceView errors", () => {
	it("shows the resource fetch error when the hierarchy is already available", () => {
		renderWithReduxAndRouter(<ResourceView resourceName="server-1" isPaused />, {
			initialState: {
				explorer: {
					hierarchy: { resources: [{ name: "server-1" }] },
					loading: false,
					error: null,
					currentResource: null,
					resourceLoading: false,
					resourceError: "Unable to fetch resource details",
				},
			},
		});

		expect(screen.getByText("Unable to fetch resource details")).toBeInTheDocument();
	});

	it("keeps the hierarchy error visible when no hierarchy is available", () => {
		renderWithReduxAndRouter(<ResourceView resourceName="server-1" isPaused />, {
			initialState: {
				explorer: {
					hierarchy: null,
					loading: false,
					error: "Unable to fetch hierarchy",
					currentResource: null,
					resourceLoading: false,
					resourceError: null,
				},
			},
		});

		expect(screen.getByText("Unable to fetch hierarchy")).toBeInTheDocument();
	});
});
