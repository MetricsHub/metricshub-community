import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import AppBreadcrumbs from "./AppBreadcrumbs";
import { paths } from "../../paths";

/**
 * Helper function to render AppBreadcrumbs with a specific route
 * @param {string} initialPath - The path to set in the router
 * @param {Object} options - Additional render options
 * @returns {Object} Render result
 */
function renderWithPath(initialPath, options = {}) {
	const Wrapper = ({ children }) => (
		<MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
	);
	return render(<AppBreadcrumbs {...options} />, { wrapper: Wrapper });
}

describe("AppBreadcrumbs", () => {
	describe("Non-Explorer routes", () => {
		it("returns null for non-explorer routes", () => {
			const { container } = renderWithPath("/configuration");
			expect(container.firstChild).toBeNull();
		});

		it("returns null for root path", () => {
			const { container } = renderWithPath("/");
			expect(container.firstChild).toBeNull();
		});

		it("returns null for login path", () => {
			const { container } = renderWithPath("/login");
			expect(container.firstChild).toBeNull();
		});
	});

	describe("Explorer routes with single breadcrumb", () => {
		it("returns null when only Explorer breadcrumb exists (welcome page)", () => {
			const { container } = renderWithPath("/explorer/welcome");
			expect(container.firstChild).toBeNull();
		});

		it("returns null for /explorer path", () => {
			const { container } = renderWithPath("/explorer");
			expect(container.firstChild).toBeNull();
		});
	});

	describe("Resource group route", () => {
		it("renders breadcrumbs for resource group", () => {
			renderWithPath("/explorer/resource-groups/My%20Group");
			expect(screen.getByText("Explorer")).toBeInTheDocument();
			expect(screen.getByText("My Group")).toBeInTheDocument();
		});

		it("decodes URL-encoded resource group names", () => {
			renderWithPath("/explorer/resource-groups/Group%20with%20%26%20Symbols");
			expect(screen.getByText("Group with & Symbols")).toBeInTheDocument();
		});

		it("renders Explorer as a link for resource group", () => {
			renderWithPath("/explorer/resource-groups/TestGroup");
			const explorerLink = screen.getByRole("link", { name: "Explorer" });
			expect(explorerLink).toBeInTheDocument();
			expect(explorerLink).toHaveAttribute("href", paths.explorerWelcome);
		});

		it("renders resource group name as non-link (last item)", () => {
			renderWithPath("/explorer/resource-groups/TestGroup");
			const groupText = screen.getByText("TestGroup");
			expect(groupText).toBeInTheDocument();
			expect(groupText.tagName).toBe("P"); // Typography renders as <p>
			expect(screen.queryByRole("link", { name: "TestGroup" })).not.toBeInTheDocument();
		});
	});

	describe("Resource route with group", () => {
		it("renders breadcrumbs for resource with group", () => {
			renderWithPath("/explorer/resource-groups/MyGroup/resources/MyResource");
			expect(screen.getByText("Explorer")).toBeInTheDocument();
			expect(screen.getByText("MyGroup")).toBeInTheDocument();
			expect(screen.getByText("MyResource")).toBeInTheDocument();
		});

		it("decodes URL-encoded resource and group names", () => {
			renderWithPath("/explorer/resource-groups/Group%20A/resources/Resource%20%231");
			expect(screen.getByText("Group A")).toBeInTheDocument();
			expect(screen.getByText("Resource #1")).toBeInTheDocument();
		});

		it("renders Explorer and group as links", () => {
			renderWithPath("/explorer/resource-groups/MyGroup/resources/MyResource");
			const explorerLink = screen.getByRole("link", { name: "Explorer" });
			const groupLink = screen.getByRole("link", { name: "MyGroup" });
			expect(explorerLink).toBeInTheDocument();
			expect(groupLink).toBeInTheDocument();
			expect(groupLink).toHaveAttribute("href", paths.explorerResourceGroup("MyGroup"));
		});

		it("renders resource name as non-link (last item)", () => {
			renderWithPath("/explorer/resource-groups/MyGroup/resources/MyResource");
			const resourceText = screen.getByText("MyResource");
			expect(resourceText).toBeInTheDocument();
			expect(resourceText.tagName).toBe("P"); // Typography renders as <p>
			expect(screen.queryByRole("link", { name: "MyResource" })).not.toBeInTheDocument();
		});
	});

	describe("Resource route without group", () => {
		it("renders breadcrumbs for resource without group", () => {
			renderWithPath("/explorer/resources/StandaloneResource");
			expect(screen.getByText("Explorer")).toBeInTheDocument();
			expect(screen.getByText("StandaloneResource")).toBeInTheDocument();
		});

		it("decodes URL-encoded resource names", () => {
			renderWithPath("/explorer/resources/Resource%20Name%20%2B%20Extra");
			expect(screen.getByText("Resource Name + Extra")).toBeInTheDocument();
		});

		it("renders Explorer as a link", () => {
			renderWithPath("/explorer/resources/StandaloneResource");
			const explorerLink = screen.getByRole("link", { name: "Explorer" });
			expect(explorerLink).toBeInTheDocument();
			expect(explorerLink).toHaveAttribute("href", paths.explorerWelcome);
		});

		it("renders resource name as non-link (last item)", () => {
			renderWithPath("/explorer/resources/StandaloneResource");
			const resourceText = screen.getByText("StandaloneResource");
			expect(resourceText).toBeInTheDocument();
			expect(resourceText.tagName).toBe("P");
			expect(screen.queryByRole("link", { name: "StandaloneResource" })).not.toBeInTheDocument();
		});
	});

	describe("Accessibility", () => {
		it("has aria-label on breadcrumbs", () => {
			renderWithPath("/explorer/resource-groups/TestGroup");
			const breadcrumbs = screen.getByLabelText("breadcrumb");
			expect(breadcrumbs).toBeInTheDocument();
		});
	});

	describe("Custom styling", () => {
		it("applies custom sx prop", () => {
			const customSx = { mt: 2, mb: 3 };
			const { container } = renderWithPath("/explorer/resource-groups/TestGroup", { sx: customSx });
			const box = container.querySelector(".MuiBox-root");
			expect(box).toBeInTheDocument();
		});

		it("merges custom sx with default styles", () => {
			renderWithPath("/explorer/resource-groups/TestGroup", { sx: { mt: 2 } });
			// Component should render without errors
			expect(screen.getByText("Explorer")).toBeInTheDocument();
		});
	});

	describe("Route matching priority", () => {
		it("matches most specific route first (resource with group)", () => {
			// This path could match both patterns, but should match the more specific one
			renderWithPath("/explorer/resource-groups/Group1/resources/Resource1");
			expect(screen.getByText("Group1")).toBeInTheDocument();
			expect(screen.getByText("Resource1")).toBeInTheDocument();
		});
	});

	describe("Edge cases", () => {
		it("returns null for empty resource group name (no match)", () => {
			const { container } = renderWithPath("/explorer/resource-groups/");
			// Path doesn't match any pattern, so returns null
			expect(container.firstChild).toBeNull();
		});

		it("handles special characters in names", () => {
			renderWithPath("/explorer/resource-groups/Group%20%2F%20Subgroup");
			expect(screen.getByText("Group / Subgroup")).toBeInTheDocument();
		});

		it("handles very long names", () => {
			const longName = "A".repeat(100);
			const encodedName = encodeURIComponent(longName);
			renderWithPath(`/explorer/resource-groups/${encodedName}`);
			expect(screen.getByText(longName)).toBeInTheDocument();
		});
	});

	describe("Breadcrumb structure", () => {
		it("renders correct number of breadcrumbs for resource with group", () => {
			renderWithPath("/explorer/resource-groups/Group1/resources/Resource1");
			const links = screen.getAllByRole("link");
			expect(links).toHaveLength(2); // Explorer and Group1
			const textElements = screen.getAllByText(/Explorer|Group1|Resource1/);
			expect(textElements).toHaveLength(3); // All three breadcrumbs
		});

		it("renders correct number of breadcrumbs for resource group", () => {
			renderWithPath("/explorer/resource-groups/Group1");
			const links = screen.getAllByRole("link");
			expect(links).toHaveLength(1); // Only Explorer
			const textElements = screen.getAllByText(/Explorer|Group1/);
			expect(textElements).toHaveLength(2); // Both breadcrumbs
		});
	});
});
