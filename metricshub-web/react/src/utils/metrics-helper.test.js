import { describe, it, expect } from "vitest";
import { isUtilizationUnit } from "./metrics-helper";

describe("isUtilizationUnit", () => {
	it("treats unit '1' metrics as utilization", () => {
		expect(isUtilizationUnit("1", "system.cpu.utilization")).toBe(true);
		expect(isUtilizationUnit("1", 'system.memory.utilization{state="used"}')).toBe(true);
	});

	it("returns false when the unit is not '1'", () => {
		expect(isUtilizationUnit("By", "system.memory.usage")).toBe(false);
		expect(isUtilizationUnit("s", "system.uptime")).toBe(false);
	});

	it("excludes threshold (.limit) metrics", () => {
		expect(isUtilizationUnit("1", "system.filesystem.limit")).toBe(false);
	});

	it("excludes ratio metrics so they render as a percentage value, not a 0-100% bar", () => {
		expect(isUtilizationUnit("1", "storage.reduction.ratio")).toBe(false);
		expect(isUtilizationUnit("1", 'storage.reduction.ratio{storage.type="storage_system"}')).toBe(
			false,
		);
	});
});
