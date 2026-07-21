import { describe, expect, it } from "vitest";
import { isExplorerResourceDataReady } from "./explorer-resource-readiness";

describe("isExplorerResourceDataReady", () => {
	it("returns false when data is missing", () => {
		expect(isExplorerResourceDataReady(null)).toBe(false);
		expect(isExplorerResourceDataReady(undefined)).toBe(false);
	});

	it("returns false when the resource payload is empty", () => {
		expect(isExplorerResourceDataReady({})).toBe(false);
		expect(isExplorerResourceDataReady({ connectors: [] })).toBe(false);
	});

	it("returns true when at least one connector exists", () => {
		expect(isExplorerResourceDataReady({ connectors: [{ name: "Linux" }] })).toBe(true);
	});

	it("returns true when metrics are present without connectors", () => {
		expect(isExplorerResourceDataReady({ connectors: [], metrics: { "host.up": 1 } })).toBe(true);
		expect(
			isExplorerResourceDataReady({ metrics: [{ name: "metricshub.host.up", value: 1 }] }),
		).toBe(true);
	});

	it("returns true when attributes are present without connectors", () => {
		expect(
			isExplorerResourceDataReady({
				connectors: [],
				attributes: { "host.name": "server01" },
			}),
		).toBe(true);
	});
});
