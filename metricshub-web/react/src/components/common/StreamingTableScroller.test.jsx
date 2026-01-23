import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import StreamingTableScroller from "./StreamingTableScroller";

let rafQueue = [];

const setScrollMetrics = (element, { scrollWidth, clientWidth, scrollLeft }) => {
	Object.defineProperty(element, "scrollWidth", {
		configurable: true,
		writable: true,
		value: scrollWidth,
	});
	Object.defineProperty(element, "clientWidth", {
		configurable: true,
		writable: true,
		value: clientWidth,
	});
	Object.defineProperty(element, "scrollLeft", {
		configurable: true,
		writable: true,
		value: scrollLeft,
	});
};

const flushAnimationFrames = async () => {
	await act(async () => {
		while (rafQueue.length) {
			const queue = rafQueue;
			rafQueue = [];
			queue.forEach((cb) => cb?.());
		}
	});
};

const getScroller = (container) => {
	const scroller = container.querySelector(".md-table-wrapper");
	if (!scroller) {
		throw new Error("Scroller element not found");
	}

	const scrollToSpy = vi.fn(({ left }) => {
		// Keep scrollLeft in sync with the mocked scrollTo behavior
		scroller.scrollLeft = left;
	});
	Object.defineProperty(scroller, "scrollTo", {
		configurable: true,
		writable: true,
		value: scrollToSpy,
	});

	return scroller;
};

beforeEach(() => {
	rafQueue = [];
	vi.stubGlobal("requestAnimationFrame", (cb) => {
		rafQueue.push(cb);
		return rafQueue.length;
	});
	vi.stubGlobal("cancelAnimationFrame", (id) => {
		rafQueue[id - 1] = null;
	});
});

afterEach(() => {
	vi.restoreAllMocks();
	rafQueue = [];
});

describe("StreamingTableScroller", () => {
	it("hides toolbar when there is no horizontal overflow", async () => {
		const { container } = render(
			<StreamingTableScroller>
				<div>Content</div>
			</StreamingTableScroller>,
		);

		const scroller = getScroller(container);
		setScrollMetrics(scroller, { scrollWidth: 200, clientWidth: 200, scrollLeft: 0 });

		await flushAnimationFrames();

		expect(container.querySelectorAll("button")).toHaveLength(0);
	});

	it("shows toolbar and enables right button when overflow exists", async () => {
		const { container } = render(
			<StreamingTableScroller>
				<div style={{ width: 400 }}>Wide content</div>
			</StreamingTableScroller>,
		);

		const scroller = getScroller(container);
		setScrollMetrics(scroller, { scrollWidth: 400, clientWidth: 200, scrollLeft: 0 });

		await flushAnimationFrames();

		const buttons = container.querySelectorAll("button");
		expect(buttons).toHaveLength(2);
		expect(buttons[0]).toBeDisabled();
		expect(buttons[1]).not.toBeDisabled();
	});

	it("updates button states on scroll and clamps scrollTo near the boundary", async () => {
		const user = userEvent.setup();
		const { container } = render(
			<StreamingTableScroller>
				<div style={{ width: 500 }}>Overflowing content</div>
			</StreamingTableScroller>,
		);

		const scroller = getScroller(container);
		setScrollMetrics(scroller, { scrollWidth: 500, clientWidth: 200, scrollLeft: 0 });
		await flushAnimationFrames();

		// Scroll to the end; right button should disable and left should enable
		await act(async () => {
			setScrollMetrics(scroller, { scrollWidth: 500, clientWidth: 200, scrollLeft: 300 });
			scroller.dispatchEvent(new Event("scroll"));
		});

		let buttons = container.querySelectorAll("button");
		expect(buttons[0]).not.toBeDisabled();
		expect(buttons[1]).toBeDisabled();

		// Move slightly left so right button becomes enabled again
		await act(async () => {
			setScrollMetrics(scroller, { scrollWidth: 500, clientWidth: 200, scrollLeft: 190 });
			scroller.dispatchEvent(new Event("scroll"));
		});

		buttons = container.querySelectorAll("button");
		expect(buttons[1]).not.toBeDisabled();

		await user.click(buttons[1]);

		expect(scroller.scrollTo).toHaveBeenCalledWith({ left: 300, behavior: "smooth" });
	});

	it("hides the toolbar while streaming and re-measures after streaming stops", async () => {
		const { container, rerender } = render(
			<StreamingTableScroller isStreaming>
				<div style={{ width: 500 }}>Streaming content</div>
			</StreamingTableScroller>,
		);

		const scroller = getScroller(container);
		setScrollMetrics(scroller, { scrollWidth: 500, clientWidth: 200, scrollLeft: 0 });
		await flushAnimationFrames();

		expect(container.querySelectorAll("button")).toHaveLength(0);

		rerender(
			<StreamingTableScroller isStreaming={false}>
				<div style={{ width: 500 }}>Streaming content</div>
			</StreamingTableScroller>,
		);
		setScrollMetrics(scroller, { scrollWidth: 500, clientWidth: 200, scrollLeft: 0 });

		await flushAnimationFrames();

		expect(container.querySelectorAll("button")).toHaveLength(2);
	});
});
