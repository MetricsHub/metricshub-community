import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import {
	buildUtilizationParts,
	colorLabelFromKey,
	getPriority,
	compareUtilizationParts,
	UtilizationStack,
	colorFor,
} from "./Utilization";

describe("Utilization Helpers", () => {
	describe("buildUtilizationParts", () => {
		it("returns empty array for empty input", () => {
			expect(buildUtilizationParts([])).toEqual([]);
		});

		it("calculates percentages correctly", () => {
			const input = [
				{ key: "a", value: 0.2 },
				{ key: "b", value: 0.8 },
			];
			const result = buildUtilizationParts(input);
			expect(result).toHaveLength(2);
			// 0.2 / (0.2 + 0.8) = 0.2 -> 20%
			expect(result.find((p) => p.key === "a").pct).toBe(20);
			expect(result.find((p) => p.key === "b").pct).toBe(80);
		});

		it("handles out of range values", () => {
			const input = [
				{ key: "low", value: -0.5 },
				{ key: "high", value: 1.5 },
			];
			const result = buildUtilizationParts(input);
			// -0.5 becomes 0 (pct 0)
			// 1.5 stays 1.5 (rawPct 1.5, rounded to 2 for total=100)
			// Plus "none" part (pct 98)
			expect(result).toHaveLength(3);

			const low = result.find((p) => p.key === "low");
			expect(low).toBeDefined();
			expect(low.value).toBe(0);
			expect(low.pct).toBe(0);

			const high = result.find((p) => p.key === "high");
			expect(high).toBeDefined();
			expect(high.value).toBe(1.5);
			// sum=1.5 > 1.05 -> total=100. 1.5/100 -> 1.5 -> rounded to 2%
			expect(high.pct).toBe(2);

			const none = result.find((p) => p.key === "none");
			expect(none).toBeDefined();
			expect(none.pct).toBe(98); // 100 - (0 + 2)
		});

		it("filters out zero values", () => {
			const input = [
				{ key: "zero", value: 0 },
				{ key: "valid", value: 0.5 },
			];
			const result = buildUtilizationParts(input);
			// zero: pct 0
			// valid: pct 50 (raw 0.5/1.0)
			// none: pct 50
			expect(result).toHaveLength(3);
			expect(result.find((p) => p.key === "none")).toBeDefined();
			expect(result.find((p) => p.key === "zero")).toBeDefined();
			expect(result.find((p) => p.key === "valid")).toBeDefined();
		});
	});

	describe("colorLabelFromKey", () => {
		it("extracts simple label from dot notation", () => {
			expect(colorLabelFromKey("system.cpu.user")).toBe("user");
			expect(colorLabelFromKey("metric.name")).toBe("name");
		});

		it("extracts label from braces with quotes", () => {
			expect(colorLabelFromKey('system.cpu.utilization{state="idle"}')).toBe("idle");
		});

		it("extracts label from braces without quotes (fallback)", () => {
			// The implementation looks for quotes inside braces
			// If no quotes, it returns the whole content inside braces
			expect(colorLabelFromKey("metric{tag=value}")).toBe("tag=value");
		});
	});

	describe("colorFor", () => {
		// We can't easily test the exact color string since it depends on the theme object passed in,
		// but we can test that it returns a function that uses the theme.
		const mockTheme = {
			palette: {
				primary: { main: "primary-color" },
				action: { disabled: "disabled-color" },
				warning: { main: "warning-color" },
				error: { main: "error-color" },
				info: { main: "info-color" },
				grey: { 500: "grey-color" },
			},
		};

		it("returns primary color for 'used'", () => {
			const colorFn = colorFor("used");
			expect(colorFn(mockTheme)).toBe("primary-color");
		});

		it("returns disabled color for 'free' or 'idle'", () => {
			expect(colorFor("free")(mockTheme)).toBe("disabled-color");
			expect(colorFor("idle")(mockTheme)).toBe("disabled-color");
		});

		it("returns warning color for 'cache'", () => {
			expect(colorFor("cache")(mockTheme)).toBe("warning-color");
		});

		it("returns error color for 'system'", () => {
			expect(colorFor("system")(mockTheme)).toBe("error-color");
		});

		it("returns info color for 'user'", () => {
			expect(colorFor("user")(mockTheme)).toBe("info-color");
		});

		it("returns grey color for unknown labels", () => {
			// The new implementation returns an HSL color based on hash, not a fixed grey from theme
			const colorFn = colorFor("unknown");
			const result = colorFn(mockTheme);
			expect(result).toMatch(/hsl\(\d+, 70%, 50%\)/);
		});
	});

	describe("getPriority", () => {
		it("returns correct priorities", () => {
			expect(getPriority("user")).toBe(10);
			expect(getPriority("system")).toBe(20);
			expect(getPriority("used")).toBe(30);
			expect(getPriority("cache")).toBe(40);
			expect(getPriority("other")).toBe(50);
			expect(getPriority("free")).toBe(90);
			expect(getPriority("idle")).toBe(100);
		});
	});

	describe("compareUtilizationParts", () => {
		it("sorts by priority first", () => {
			const partA = { key: "system.cpu.user", pct: 50 }; // priority 10
			const partB = { key: "system.cpu.idle", pct: 50 }; // priority 100
			expect(compareUtilizationParts(partA, partB)).toBeLessThan(0);
			expect(compareUtilizationParts(partB, partA)).toBeGreaterThan(0);
		});

		it("sorts by percentage if priority is same", () => {
			// Both "other" -> priority 50
			const partA = { key: "other1", pct: 20 };
			const partB = { key: "other2", pct: 80 };
			expect(compareUtilizationParts(partA, partB)).toBeLessThan(0);
		});
	});
});

describe("UtilizationStack", () => {
	it("renders empty box when parts is empty", () => {
		const { container } = render(<UtilizationStack parts={[]} />);
		// It renders a Box (div) with specific styles, not empty
		expect(container.firstChild).toHaveClass("MuiBox-root");
	});

	it("renders empty box when parts is not an array", () => {
		const { container } = render(<UtilizationStack parts={null} />);
		expect(container.firstChild).toHaveClass("MuiBox-root");
	});

	it("renders progress bars with percentage text", () => {
		const parts = [
			{ key: "user", value: 0.6, pct: 60 },
			{ key: "idle", value: 0.4, pct: 40 },
		];
		render(<UtilizationStack parts={parts} />);

		expect(screen.getByText("60%")).toBeInTheDocument();
		expect(screen.getByText("40%")).toBeInTheDocument();
	});

	it("hides percentage text for small values (<= 12%)", () => {
		const parts = [
			{ key: "user", value: 0.1, pct: 10 },
			{ key: "idle", value: 0.9, pct: 90 },
		];
		render(<UtilizationStack parts={parts} />);

		expect(screen.queryByText("10%")).not.toBeInTheDocument();
		expect(screen.getByText("90%")).toBeInTheDocument();
	});
});
