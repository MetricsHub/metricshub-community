import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PivotGroupSection from "./PivotGroupSection";
import { renderWithRedux } from "../../../../../test/test-utils";

describe("PivotGroupSection keyboard controls", () => {
	it("opens with Enter and closes with Space", async () => {
		const user = userEvent.setup();
		renderWithRedux(
			<PivotGroupSection
				group={{ baseName: "hw.logical_disk.limit", metricKeys: [] }}
				sortedInstances={[]}
				resourceId="server-1"
				metaMetrics={{}}
			/>,
		);

		const toggle = screen.getByRole("button", { name: "hw.logical_disk.limit" });
		await user.tab();
		expect(toggle).toHaveFocus();
		expect(toggle).toHaveAttribute("aria-expanded", "false");

		await user.keyboard("{Enter}");
		expect(toggle).toHaveAttribute("aria-expanded", "true");
		expect(document.getElementById(toggle.getAttribute("aria-controls"))).toBeInTheDocument();

		await user.keyboard(" ");
		expect(toggle).toHaveAttribute("aria-expanded", "false");
		expect(screen.queryByRole("grid")).not.toBeInTheDocument();
	});
});
