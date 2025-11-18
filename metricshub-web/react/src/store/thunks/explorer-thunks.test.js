/**
 * Tests for fetchExplorerHierarchy thunk
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { configureStore } from "@reduxjs/toolkit";
import { fetchExplorerHierarchy } from "./explorer-thunks";
import { explorerReducer } from "../slices/explorer-slice";
import { explorerApi } from "../../api/explorer";

vi.mock("../../api/explorer", () => ({
	explorerApi: { getHierarchy: vi.fn() },
}));

describe("fetchExplorerHierarchy", () => {
	let store;
	beforeEach(() => {
		store = configureStore({ reducer: { explorer: explorerReducer } });
		vi.clearAllMocks();
	});

	it("fetches hierarchy successfully", async () => {
		const payload = { name: "Root", type: "resource-group", children: [] };
		explorerApi.getHierarchy.mockResolvedValue(payload);

		await store.dispatch(fetchExplorerHierarchy());

		const s = store.getState().explorer;
		expect(s.loading).toBe(false);
		expect(s.hierarchy).toEqual(payload);
		expect(s.error).toBe(null);
		expect(explorerApi.getHierarchy).toHaveBeenCalledTimes(1);
	});

	it("handles error from API", async () => {
		explorerApi.getHierarchy.mockRejectedValue(new Error("net"));

		await store.dispatch(fetchExplorerHierarchy());

		const s = store.getState().explorer;
		expect(s.loading).toBe(false);
		expect(s.hierarchy).toBe(null);
		expect(s.error).toBe("net");
	});
});
