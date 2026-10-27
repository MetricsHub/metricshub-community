import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ConnectorAccordion from "./ConnectorAccordion";
import { renderWithReduxAndRouter } from "../../../../../test/test-utils";
import { paths } from "../../../../../paths";

describe("ConnectorAccordion monitor controls", () => {
	it("provides separate keyboard controls for expansion and navigation", async () => {
		const user = userEvent.setup();
		const { store } = renderWithReduxAndRouter(
			<ConnectorAccordion
				connector={{ name: "Linux", monitors: [{ name: "logical_disk", instances: [] }] }}
				connectorIndex={0}
				resourceId="server-1"
				resourceName="server-1"
			/>,
			{
				initialState: {
					explorer: {
						uiState: { "server-1": { monitors: { Linux: true }, pivotGroups: {} } },
					},
				},
			},
		);

		const toggle = screen.getByRole("button", { name: "Toggle Logical Disk, 0 instances" });
		const link = screen.getByRole("link", { name: "Logical Disk" });
		expect(link.closest("button")).toBeNull();
		expect(toggle.querySelector("a, button")).toBeNull();

		await user.tab();
		await user.tab();
		expect(toggle).toHaveFocus();
		await user.keyboard("{Enter}");
		expect(toggle).toHaveAttribute("aria-expanded", "true");
		await user.keyboard(" ");
		expect(toggle).toHaveAttribute("aria-expanded", "false");

		await user.tab();
		expect(link).toHaveFocus();
		await user.keyboard("{Enter}");
		expect(window.location.pathname).toBe(
			paths.explorerMonitorType(undefined, "server-1", "Linux", "logical_disk"),
		);
		expect(store.getState().explorer.uiState["server-1"].monitors["Linux-logical_disk"]).toBe(
			false,
		);
	});
});
