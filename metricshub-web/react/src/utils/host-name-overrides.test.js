import { describe, expect, it } from "vitest";
import {
	alignHostnameOverrides,
	buildProtocolHostnamePayload,
	countConfiguredOverrides,
	realignHostnameOverrides,
	splitHostnameOverrides,
} from "./host-name-overrides";

// Overrides are positional (PostConfigDeserializer.normalizeProtocolHostnames):
// no dedupe (hosts may share one proxy) and blanks are meaningful slots.
describe("splitHostnameOverrides", () => {
	it("keeps duplicate values", () => {
		expect(splitHostnameOverrides(["proxy", "proxy"])).toEqual(["proxy", "proxy"]);
	});

	it("preserves blank slots in arrays and strings", () => {
		expect(splitHostnameOverrides(["a", "", "b"])).toEqual(["a", "", "b"]);
		expect(splitHostnameOverrides("a,,b")).toEqual(["a", "", "b"]);
	});

	it("trims entries", () => {
		expect(splitHostnameOverrides(" a ; b ")).toEqual(["a", "b"]);
	});

	it("returns an empty list for null or undefined", () => {
		expect(splitHostnameOverrides(null)).toEqual([]);
		expect(splitHostnameOverrides(undefined)).toEqual([]);
	});
});

describe("alignHostnameOverrides", () => {
	it("repeats the last value when the list is shorter (backend clamp)", () => {
		expect(alignHostnameOverrides(["p1", "p2"], 4)).toEqual(["p1", "p2", "p2", "p2"]);
	});

	it("applies a plain string to every slot", () => {
		expect(alignHostnameOverrides("proxy", 3)).toEqual(["proxy", "proxy", "proxy"]);
	});

	it("keeps only the first N entries of a longer list", () => {
		expect(alignHostnameOverrides(["a", "b", "c"], 2)).toEqual(["a", "b"]);
	});

	it("keeps explicit blank slots of a full-length list without clamping", () => {
		expect(alignHostnameOverrides(["a", "", ""], 3)).toEqual(["a", "", ""]);
	});

	it("yields all-blank slots for an empty or blank-only value", () => {
		expect(alignHostnameOverrides("", 3)).toEqual(["", "", ""]);
		expect(alignHostnameOverrides(["", ""], 3)).toEqual(["", "", ""]);
	});
});

describe("countConfiguredOverrides", () => {
	it("counts non-blank slots among the first N", () => {
		expect(countConfiguredOverrides(["a", "", "b"], 3)).toBe(2);
		expect(countConfiguredOverrides("", 5)).toBe(0);
	});
});

describe("buildProtocolHostnamePayload", () => {
	it("fills blank slots with the host.name entry", () => {
		expect(buildProtocolHostnamePayload(["", "proxy", ""], ["h1", "h2", "h3"])).toEqual([
			"h1",
			"proxy",
			"h3",
		]);
	});

	it("omits the field when every slot is blank", () => {
		expect(buildProtocolHostnamePayload(["", "", ""], ["h1", "h2", "h3"])).toBeUndefined();
	});

	it("preserves duplicate override values", () => {
		expect(buildProtocolHostnamePayload(["proxy", "proxy"], ["h1", "h2"])).toEqual([
			"proxy",
			"proxy",
		]);
	});

	it("always emits a full-length array (legacy short lists expanded)", () => {
		expect(buildProtocolHostnamePayload(["p1", "p2"], ["h1", "h2", "h3", "h4"])).toEqual([
			"p1",
			"p2",
			"p2",
			"p2",
		]);
	});

	it("strips separators left in a value", () => {
		expect(buildProtocolHostnamePayload(["a;b", "c"], ["h1", "h2"])).toEqual(["ab", "c"]);
	});

	it("keeps the single-host shape a plain string", () => {
		expect(buildProtocolHostnamePayload("proxy", ["h1"])).toBe("proxy");
		expect(buildProtocolHostnamePayload("", ["h1"])).toBeUndefined();
		expect(buildProtocolHostnamePayload("proxy", [])).toBe("proxy");
	});
});

describe("realignHostnameOverrides", () => {
	it("drops the override of a removed host and keeps the others aligned", () => {
		expect(realignHostnameOverrides(["h1", "h2", "h3"], ["h1", "h3"], ["p1", "p2", "p3"])).toEqual([
			"p1",
			"p3",
		]);
	});

	it("follows hosts across a reorder", () => {
		expect(realignHostnameOverrides(["h1", "h2"], ["h2", "h1"], ["p1", "p2"])).toEqual([
			"p2",
			"p1",
		]);
	});

	it("matches hosts case-insensitively", () => {
		expect(realignHostnameOverrides(["Host-A"], ["host-a", "host-b"], ["p1"])).toEqual(["p1", ""]);
	});

	it("starts added or renamed hosts blank", () => {
		expect(realignHostnameOverrides(["h1"], ["h1", "h2"], ["p1"])).toEqual(["p1", ""]);
		expect(realignHostnameOverrides(["h1"], ["h1-renamed"], ["p1"])).toEqual([""]);
	});

	it("returns non-array values untouched", () => {
		expect(realignHostnameOverrides(["h1", "h2"], ["h1"], "proxy")).toBe("proxy");
	});
});
