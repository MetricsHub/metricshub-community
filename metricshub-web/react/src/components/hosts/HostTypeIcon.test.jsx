import { describe, it, expect } from "vitest";
import { render } from "@testing-library/react";
import { ThemeProvider } from "@mui/material";
import HostTypeIcon from "./HostTypeIcon";
import { HOST_TYPES } from "./protocol-definitions";
import { createTheme as createMetricsHubTheme } from "../../theme";

const buildTheme = (paletteMode) =>
	createMetricsHubTheme({ direction: "ltr", paletteMode, responsiveFontSizes: false });

const renderIcon = (hostType, paletteMode = "light") =>
	render(
		<ThemeProvider theme={buildTheme(paletteMode)}>
			<HostTypeIcon hostType={hostType} />
		</ThemeProvider>,
	);

describe("HostTypeIcon", () => {
	// The component falls back to an empty box for an unmapped host type, so a
	// missing entry in either icon map degrades silently to a blank slot.
	it.each(HOST_TYPES)("renders artwork for the %s host type", (hostType) => {
		const { container } = renderIcon(hostType);
		expect(container.querySelector("img, svg")).not.toBeNull();
	});

	it("draws other from the DevicesOther glyph rather than a themed SVG", () => {
		const { container } = renderIcon("other");
		expect(container.querySelector("img")).toBeNull();
		const glyph = container.querySelector("svg");
		expect(glyph).not.toBeNull();
		expect(glyph.querySelector("title")?.textContent).toBe("Other");
	});

	it("renders an empty slot for an unknown host type", () => {
		const { container } = renderIcon("nope");
		expect(container.querySelector("img, svg")).toBeNull();
	});
});
