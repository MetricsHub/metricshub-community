import { describe, it, expect, vi, beforeEach } from "vitest";
import * as React from "react";
import { screen } from "@testing-library/react";
import { renderWithRouter } from "../test/test-utils";
import LoginPage from "./LoginPage";
import { useAuth } from "../hooks/use-auth";

vi.mock("../hooks/use-auth", () => ({
	useAuth: vi.fn(),
}));

describe("LoginPage", () => {
	beforeEach(() => {
		useAuth.mockReturnValue({ signIn: vi.fn() });
	});

	it("links Getting Started to the web interface docs", () => {
		renderWithRouter(<LoginPage />);

		const link = screen.getByRole("link", { name: /getting started/i });
		expect(link).toHaveAttribute(
			"href",
			"https://metricshub.com/docs/latest/operating-web-interface",
		);
		expect(link).toHaveAttribute("target", "_blank");
	});
});
