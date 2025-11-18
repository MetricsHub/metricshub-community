/**
 * Tests for explorerSlice
 */
import { describe, it, expect } from "vitest";
import { explorerReducer, clearHierarchy } from "./explorer-slice";
import { fetchExplorerHierarchy } from "../thunks/explorer-thunks";

const initialState = {
	hierarchy: null,
	loading: false,
	error: null,
};

describe("explorerSlice", () => {
	it("should return initial state", () => {
		expect(explorerReducer(undefined, { type: "unknown" })).toEqual(initialState);
	});

	it("should handle fetchExplorerHierarchy.pending", () => {
		const action = { type: fetchExplorerHierarchy.pending.type };
		const s = explorerReducer(initialState, action);
		expect(s.loading).toBe(true);
		expect(s.error).toBe(null);
	});

	it("should handle fetchExplorerHierarchy.fulfilled", () => {
		const payload = { name: "Root", type: "resource-group", children: [] };
		const action = { type: fetchExplorerHierarchy.fulfilled.type, payload };
		const s = explorerReducer(initialState, action);
		expect(s.loading).toBe(false);
		expect(s.hierarchy).toEqual(payload);
		expect(s.error).toBe(null);
	});

	it("should handle fetchExplorerHierarchy.rejected", () => {
		const action = { type: fetchExplorerHierarchy.rejected.type, payload: "boom" };
		const s = explorerReducer(initialState, action);
		expect(s.loading).toBe(false);
		expect(s.error).toBe("boom");
	});

	it("should clear hierarchy", () => {
		const prev = {
			hierarchy: { name: "x", type: "resource-group", children: [] },
			loading: true,
			error: "oops",
		};
		const s = explorerReducer(prev, clearHierarchy());
		expect(s.hierarchy).toBe(null);
		expect(s.loading).toBe(false);
		expect(s.error).toBe(null);
	});
});
