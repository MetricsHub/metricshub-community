import { describe, it, expect, vi } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithAllProviders } from "../../test/test-utils";
import ExplorerRedirect from "./ExplorerRedirect";
import { paths } from "../../paths";

// Mock Navigate to verify redirection
vi.mock("react-router-dom", async () => {
	const actual = await vi.importActual("react-router-dom");
	return {
		...actual,
		Navigate: ({ to }) => <div data-testid="redirect">{to}</div>,
	};
});

describe("ExplorerRedirect", () => {
	it("redirects to welcome by default", () => {
		renderWithAllProviders(<ExplorerRedirect />, {
			initialState: { explorer: { lastVisitedPath: null } },
		});
		expect(screen.getByTestId("redirect")).toHaveTextContent(paths.explorerWelcome);
	});

	it("redirects to last visited path", () => {
		const lastPath = "/explorer/resource/123";
		renderWithAllProviders(<ExplorerRedirect />, {
			initialState: { explorer: { lastVisitedPath: lastPath } },
		});
		expect(screen.getByTestId("redirect")).toHaveTextContent(lastPath);
	});
});
