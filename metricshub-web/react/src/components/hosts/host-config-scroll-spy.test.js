import { describe, expect, it } from "vitest";
import {
	computeActiveStepIndex,
	computeGeometryActiveStepIndex,
	retryOnAnimationFrames,
	STEP_ACTIVATION_OFFSET,
} from "./host-config-scroll-spy";

const mockRect = (top, height = 200) => ({
	top,
	bottom: top + height,
	left: 0,
	right: 800,
	width: 800,
	height,
});

const mockScrollContainer = ({ scrollTop = 0, scrollHeight = 600, clientHeight = 600 } = {}) => ({
	getBoundingClientRect: () => mockRect(0, clientHeight),
	scrollTop,
	scrollHeight,
	clientHeight,
});

describe("computeGeometryActiveStepIndex", () => {
	const offset = STEP_ACTIVATION_OFFSET;

	it("activates the section whose top has passed the activation line", () => {
		const container = mockScrollContainer();
		const els = [
			{ getBoundingClientRect: () => mockRect(10, 250) },
			{ getBoundingClientRect: () => mockRect(400, 180) },
		];

		expect(computeGeometryActiveStepIndex(container, els, offset)).toBe(0);
	});

	it("activates a short section below the activation line when the previous section ended", () => {
		const container = mockScrollContainer();
		const els = [
			{ getBoundingClientRect: () => mockRect(-120, 100) },
			{ getBoundingClientRect: () => mockRect(80, 90) },
			{ getBoundingClientRect: () => mockRect(220, 200) },
		];

		expect(computeGeometryActiveStepIndex(container, els, offset)).toBe(1);
	});

	it("prefers the section that contains the activation line", () => {
		const container = mockScrollContainer();
		const activationY = offset;
		const els = [
			{ getBoundingClientRect: () => mockRect(-50, 120) },
			{
				getBoundingClientRect: () => mockRect(activationY - 10, 80),
			},
		];

		expect(computeGeometryActiveStepIndex(container, els, offset)).toBe(1);
	});
});

describe("computeActiveStepIndex", () => {
	const offset = STEP_ACTIVATION_OFFSET;

	it("advances to trailing steps near the bottom even when an earlier title stays above the activation line", () => {
		const container = mockScrollContainer({
			scrollTop: 400,
			scrollHeight: 1000,
			clientHeight: 600,
		});
		const els = [
			{ getBoundingClientRect: () => mockRect(-200, 350) },
			{ getBoundingClientRect: () => mockRect(200, 200) },
			{ getBoundingClientRect: () => mockRect(450, 150) },
		];

		expect(computeGeometryActiveStepIndex(container, els, offset)).toBe(0);
		expect(computeActiveStepIndex(container, els, offset)).toBe(2);
	});

	it("uses geometry for short sections before scroll thresholds would advance", () => {
		const container = mockScrollContainer();
		const els = [
			{ getBoundingClientRect: () => mockRect(-120, 100) },
			{ getBoundingClientRect: () => mockRect(80, 90) },
			{ getBoundingClientRect: () => mockRect(220, 200) },
		];

		expect(computeActiveStepIndex(container, els, offset)).toBe(1);
	});
});

describe("retryOnAnimationFrames", () => {
	/** Replaces rAF with a manual queue so frames advance only via runFrame(). */
	const withManualFrames = (run) => {
		const original = {
			raf: globalThis.requestAnimationFrame,
			caf: globalThis.cancelAnimationFrame,
		};
		let nextId = 1;
		const pending = new Map();
		globalThis.requestAnimationFrame = (cb) => {
			const id = nextId++;
			pending.set(id, cb);
			return id;
		};
		globalThis.cancelAnimationFrame = (id) => {
			pending.delete(id);
		};
		const runFrame = () => {
			const entries = [...pending.entries()];
			pending.clear();
			for (const [, cb] of entries) {
				cb(0);
			}
		};
		try {
			run(runFrame, pending);
		} finally {
			globalThis.requestAnimationFrame = original.raf;
			globalThis.cancelAnimationFrame = original.caf;
		}
	};

	it("stops retrying once the attempt succeeds", () => {
		withManualFrames((runFrame, pending) => {
			let calls = 0;
			const attempt = () => {
				calls += 1;
				return calls === 2;
			};
			let gaveUp = false;
			retryOnAnimationFrames(attempt, 5, () => {
				gaveUp = true;
			});

			runFrame();
			runFrame();

			expect(calls).toBe(2);
			expect(gaveUp).toBe(false);
			expect(pending.size).toBe(0);
		});
	});

	it("calls onGiveUp after maxFrames failed attempts", () => {
		withManualFrames((runFrame) => {
			let calls = 0;
			const attempt = () => {
				calls += 1;
				return false;
			};
			let gaveUp = false;
			retryOnAnimationFrames(attempt, 3, () => {
				gaveUp = true;
			});

			runFrame();
			runFrame();
			runFrame();

			expect(calls).toBe(3);
			expect(gaveUp).toBe(true);
		});
	});

	it("cancel stops pending attempts and never gives up", () => {
		withManualFrames((runFrame, pending) => {
			let calls = 0;
			let gaveUp = false;
			const cancel = retryOnAnimationFrames(
				() => {
					calls += 1;
					return false;
				},
				5,
				() => {
					gaveUp = true;
				},
			);

			runFrame();
			cancel();
			runFrame();

			expect(calls).toBe(1);
			expect(gaveUp).toBe(false);
			expect(pending.size).toBe(0);
		});
	});
});
