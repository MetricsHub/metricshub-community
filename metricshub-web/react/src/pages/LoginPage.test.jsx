import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import * as ReactRouterDom from "react-router-dom";
import * as AuthHook from "../hooks/use-auth";
import LoginPage from "./LoginPage";

vi.mock("../hooks/use-auth", () => ({
	useAuth: vi.fn(),
}));

vi.mock("react-router-dom", async () => {
	const actual = await vi.importActual("react-router-dom");
	return {
		...actual,
		useNavigate: vi.fn(),
		useSearchParams: vi.fn(),
	};
});

describe("LoginPage", () => {
	beforeEach(() => {
		vi.clearAllMocks();
		vi.mocked(AuthHook.useAuth).mockReturnValue({ signIn: vi.fn() });
		vi.mocked(ReactRouterDom.useNavigate).mockReturnValue(vi.fn());
		vi.mocked(ReactRouterDom.useSearchParams).mockReturnValue([new URLSearchParams(), vi.fn()]);
	});

	it("renders getting started link with the docs URL", () => {
		render(<LoginPage />);

		const gettingStartedLink = screen.getByRole("link", { name: /getting started/i });
		expect(gettingStartedLink).toHaveAttribute(
			"href",
			"https://metricshub.com/docs/latest/operating-web-interface",
		);
	});
});
