import * as React from "react";
import { describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material";
import { createTheme } from "../../theme";
import ResourceGroupsOverviewView from "./ResourceGroupsOverviewView";

describe("resource group overview actions", () => {
	it("opens a group independently of the accordion using the mouse or keyboard", async () => {
		const user = userEvent.setup();
		const onOpenGroup = vi.fn();
		render(
			<ThemeProvider theme={createTheme({ paletteMode: "dark" })}>
				<ResourceGroupsOverviewView
					snapshot={{ resourceGroups: { Nanterre: { resources: {} } } }}
					onOpenGroup={onOpenGroup}
					onOpenHost={vi.fn()}
					onAddHostToGroup={vi.fn()}
					onDeleteGroup={vi.fn()}
				/>
			</ThemeProvider>,
		);
		const summary = screen.getByRole("button", { name: /Nanterre/ });
		const open = screen.getByRole("button", { name: "Open" });
		expect(summary).not.toContainElement(open);
		expect(summary).toHaveAttribute("aria-expanded", "true");

		await user.click(open);
		expect(onOpenGroup).toHaveBeenLastCalledWith("Nanterre");
		expect(summary).toHaveAttribute("aria-expanded", "true");

		act(() => summary.focus());
		await user.keyboard("{Enter}");
		expect(summary).toHaveAttribute("aria-expanded", "false");
		await user.tab();
		expect(open).toHaveFocus();
		await user.keyboard("{Enter}");
		expect(onOpenGroup).toHaveBeenCalledTimes(2);
		expect(summary).toHaveAttribute("aria-expanded", "false");
	});
});
