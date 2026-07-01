import { describe, expect, it } from "vitest";
import { computeActiveStepIndex, STEP_ACTIVATION_OFFSET } from "./host-config-scroll-spy";

const mockRect = (top, height = 200) => ({
	top,
	bottom: top + height,
	left: 0,
	right: 800,
	width: 800,
	height,
});

describe("computeActiveStepIndex", () => {
	const offset = STEP_ACTIVATION_OFFSET;

	it("activates the section whose top has passed the activation line", () => {
		const container = { getBoundingClientRect: () => mockRect(0, 600) };
		const els = [
			{ getBoundingClientRect: () => mockRect(10, 250) },
			{ getBoundingClientRect: () => mockRect(400, 180) },
		];

		expect(computeActiveStepIndex(container, els, offset)).toBe(0);
	});

	it("activates a short section below the activation line when the previous section ended", () => {
		const container = { getBoundingClientRect: () => mockRect(0, 600) };
		const els = [
			{ getBoundingClientRect: () => mockRect(-120, 100) },
			{ getBoundingClientRect: () => mockRect(80, 90) },
			{ getBoundingClientRect: () => mockRect(220, 200) },
		];

		expect(computeActiveStepIndex(container, els, offset)).toBe(1);
	});

	it("prefers the section that contains the activation line", () => {
		const container = { getBoundingClientRect: () => mockRect(0, 600) };
		const activationY = offset;
		const els = [
			{ getBoundingClientRect: () => mockRect(-50, 120) },
			{
				getBoundingClientRect: () => mockRect(activationY - 10, 80),
			},
		];

		expect(computeActiveStepIndex(container, els, offset)).toBe(1);
	});
});
