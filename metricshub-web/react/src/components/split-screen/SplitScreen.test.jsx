import * as React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { SplitScreen, Left, Right } from "./SplitScreen";
import { ResizableVerticalSplit, Top, Bottom } from "./ResizableVerticalSplit";

beforeEach(() => localStorage.clear());
afterEach(() => {
	document.body.style.cursor = "";
});

const resizablePanels = [
	{
		name: "Resize panels",
		content: (
			<SplitScreen>
				<Left>Explorer tree</Left>
				<Right>Resource details</Right>
			</SplitScreen>
		),
		increaseKey: "ArrowRight",
		decreaseKey: "ArrowLeft",
		initial: 40,
		min: 15,
		max: 85,
		cursor: "col-resize",
	},
	{
		name: "Resize editor and logs",
		content: (
			<ResizableVerticalSplit>
				<Top>OTel editor</Top>
				<Bottom>Collector logs</Bottom>
			</ResizableVerticalSplit>
		),
		increaseKey: "ArrowDown",
		decreaseKey: "ArrowUp",
		initial: 60,
		min: 20,
		max: 90,
		cursor: "row-resize",
	},
];

describe("Split panes", () => {
	it("places the Explorer panes on either side of the resize handle", () => {
		render(
			<SplitScreen>
				<Left>Explorer tree</Left>
				<Right>Resource details</Right>
			</SplitScreen>,
		);

		expect(screen.getByText("Explorer tree")).toHaveStyle({ gridColumn: "1", gridRow: "1" });
		expect(screen.getByRole("separator", { name: "Resize panels" })).toHaveStyle({
			gridColumn: "2",
		});
		expect(screen.getByText("Resource details")).toHaveStyle({ gridColumn: "3", gridRow: "1" });
	});

	it("keeps the Collector log pane scrolling inside its own content", () => {
		render(
			<ResizableVerticalSplit>
				<Top>OTel editor</Top>
				<Bottom disableScroll>Collector logs</Bottom>
			</ResizableVerticalSplit>,
		);

		expect(screen.getByText("OTel editor")).toHaveStyle({ gridRow: "1" });
		expect(screen.getByText("Collector logs")).toHaveStyle({ gridRow: "3", overflow: "hidden" });
	});

	it.each(resizablePanels)(
		"resizes $name with the keyboard within its existing bounds",
		async ({ name, content, increaseKey, decreaseKey, initial, min, max }) => {
			const user = userEvent.setup();
			render(content);
			const separator = screen.getByRole("separator", { name });
			await user.tab();
			expect(separator).toHaveFocus();
			expect(separator).toHaveAttribute("aria-valuemin", String(min));
			expect(separator).toHaveAttribute("aria-valuemax", String(max));
			await user.keyboard(`{${increaseKey}}`);
			expect(separator).toHaveAttribute("aria-valuenow", String(initial + 5));
			await user.keyboard(Array(30).fill(`{${increaseKey}}`).join(""));
			expect(separator).toHaveAttribute("aria-valuenow", String(max));
			await user.keyboard(Array(30).fill(`{${decreaseKey}}`).join(""));
			expect(separator).toHaveAttribute("aria-valuenow", String(min));
		},
	);

	it.each(resizablePanels)(
		"restores the previous cursor when $name unmounts during a drag",
		({ name, content, cursor }) => {
			document.body.style.cursor = "crosshair";
			const { unmount } = render(content);
			fireEvent.pointerDown(screen.getByRole("separator", { name }), { pointerId: 1 });
			expect(document.body.style.cursor).toBe(cursor);
			unmount();
			expect(document.body.style.cursor).toBe("crosshair");
		},
	);

	it("skips the resize handle in keyboard navigation when logs are closed", async () => {
		const user = userEvent.setup();
		render(
			<>
				<ResizableVerticalSplit bottomVisible={false}>
					<Top>OTel editor</Top>
					<Bottom>Collector logs</Bottom>
				</ResizableVerticalSplit>
				<button>Next action</button>
			</>,
		);
		expect(
			screen.queryByRole("separator", { name: "Resize editor and logs" }),
		).not.toBeInTheDocument();
		await user.tab();
		expect(screen.getByRole("button", { name: "Next action" })).toHaveFocus();
	});
});
