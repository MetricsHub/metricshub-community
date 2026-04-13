import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useScrollToHash } from "./use-scroll-to-hash";
import * as ReactRouterDom from "react-router-dom";

// Mock react-router-dom
vi.mock("react-router-dom", () => ({
	useLocation: vi.fn(),
}));

describe("useScrollToHash", () => {
	beforeEach(() => {
		vi.clearAllMocks();
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it("should return null if no hash is present", () => {
		vi.mocked(ReactRouterDom.useLocation).mockReturnValue({ hash: "" });

		const { result } = renderHook(() => useScrollToHash());

		expect(result.current).toBeNull();
	});

	it("should return decoded id and scroll to element if hash is present", () => {
		const hash = "#test%20id";
		const decodedId = "test id";
		vi.mocked(ReactRouterDom.useLocation).mockReturnValue({ hash });

		const scrollIntoViewMock = vi.fn();
		const getElementByIdMock = vi.spyOn(document, "getElementById").mockReturnValue({
			scrollIntoView: scrollIntoViewMock,
		});

		const { result } = renderHook(() => useScrollToHash());

		// Initially it might be null or set immediately depending on React version/batching,
		// but we expect the side effect (scroll) to happen after delay.

		// Fast forward timer
		act(() => {
			vi.advanceTimersByTime(100);
		});

		expect(result.current).toBe(decodedId);
		expect(getElementByIdMock).toHaveBeenCalledWith(decodedId);
		expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: "smooth", block: "center" });
	});

	it("should clear highlightedId if hash is removed", () => {
		// Start with hash
		vi.mocked(ReactRouterDom.useLocation).mockReturnValue({ hash: "#test" });
		const { result, rerender } = renderHook(() => useScrollToHash());

		act(() => {
			vi.advanceTimersByTime(100);
		});
		expect(result.current).toBe("test");

		// Update mock to no hash
		vi.mocked(ReactRouterDom.useLocation).mockReturnValue({ hash: "" });
		rerender();

		expect(result.current).toBeNull();
	});
});
