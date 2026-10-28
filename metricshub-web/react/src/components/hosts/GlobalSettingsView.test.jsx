import * as React from "react";
import { describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material";
import { createTheme } from "../../theme";
import GlobalSettingsView from "./GlobalSettingsView";

describe("agent configuration resource groups", () => {
	it("keeps group navigation separate from keyboard expansion", async () => {
		const user = userEvent.setup();
		const onConfigureGroup = vi.fn();
		render(
			<MemoryRouter>
				<ThemeProvider theme={createTheme({ paletteMode: "dark" })}>
					<GlobalSettingsView
						snapshot={{ resourceGroups: { Nanterre: { resources: {} } } }}
						onConfigureGroup={onConfigureGroup}
					/>
				</ThemeProvider>
			</MemoryRouter>,
		);
		const group = screen.getByRole("button", { name: "Nanterre", exact: true });
		const summary = screen.getByRole("button", { name: "Expand Nanterre" });
		expect(summary).not.toContainElement(group);
		act(() => group.focus());
		await user.keyboard("{Enter}");
		expect(onConfigureGroup).toHaveBeenCalledWith("Nanterre");
		expect(summary).toHaveAttribute("aria-expanded", "false");
		await user.tab();
		expect(summary).toHaveFocus();
		await user.keyboard(" ");
		expect(summary).toHaveAttribute("aria-expanded", "true");
		expect(onConfigureGroup).toHaveBeenCalledTimes(1);
	});
});
