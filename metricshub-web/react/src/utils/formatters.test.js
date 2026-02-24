import { describe, it, expect } from "vitest";
import { formatReasoningDuration } from "./formatters";

describe("formatReasoningDuration", () => {
	it("formats milliseconds when less than 1000ms", () => {
		expect(formatReasoningDuration(0)).toBe("0ms");
		expect(formatReasoningDuration(500)).toBe("500ms");
		expect(formatReasoningDuration(999)).toBe("999ms");
	});

	it("formats seconds with one decimal when less than 60s", () => {
		expect(formatReasoningDuration(1000)).toBe("1s");
		expect(formatReasoningDuration(1500)).toBe("1.5s");
		expect(formatReasoningDuration(2300)).toBe("2.3s");
		expect(formatReasoningDuration(59900)).toBe("59.9s");
	});

	it("formats minutes and seconds correctly", () => {
		expect(formatReasoningDuration(60000)).toBe("1m");
		expect(formatReasoningDuration(75000)).toBe("1m 15s");
		expect(formatReasoningDuration(120000)).toBe("2m");
		expect(formatReasoningDuration(125000)).toBe("2m 5s");
	});

	it("handles edge case: 119.6s should not produce '1m 60s'", () => {
		// 119.6s = 119600ms
		// This is the specific case mentioned in the feedback
		const result = formatReasoningDuration(119600);
		expect(result).toBe("2m");
		expect(result).not.toContain("60s");
	});

	it("handles edge cases near minute boundaries", () => {
		// Under 60s: displayed with one decimal
		expect(formatReasoningDuration(59400)).toBe("59.4s");
		expect(formatReasoningDuration(59500)).toBe("59.5s");
		expect(formatReasoningDuration(59600)).toBe("59.6s");

		// 60.4s: rounds to 60s total, displayed as 1m
		expect(formatReasoningDuration(60400)).toBe("1m");

		// 119.4s: rounds to 119s total, displayed as 1m 59s
		expect(formatReasoningDuration(119400)).toBe("1m 59s");

		// 119.5s: rounds to 120s total, displayed as 2m
		expect(formatReasoningDuration(119500)).toBe("2m");
	});

	it("handles large durations", () => {
		expect(formatReasoningDuration(180000)).toBe("3m");
		expect(formatReasoningDuration(185000)).toBe("3m 5s");
		expect(formatReasoningDuration(3600000)).toBe("60m");
	});

	it("never produces invalid combinations like 'Xm 60s'", () => {
		// Test various durations that could potentially round incorrectly
		const testCases = [
			59500, 59600, 59700, 59800, 59900, 119500, 119600, 119700, 119800, 119900, 179500, 179600,
			179700, 179800, 179900,
		];

		testCases.forEach((ms) => {
			const result = formatReasoningDuration(ms);
			expect(result).not.toMatch(/\d+m 60s/);
		});
	});
});
