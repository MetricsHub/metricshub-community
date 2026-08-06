import { describe, expect, it } from "vitest";
import { getHostNames, normalizeHostNameValue } from "./host-names";

// Order is significant end to end: multi-host protocol hostnames are matched to
// host.name entries by position (PostConfigDeserializer.normalizeProtocolHostnames),
// so the UI must never reorder what the user entered.
describe("getHostNames", () => {
	it("preserves the entry order of a comma-separated string", () => {
		expect(getHostNames("ecs1, ecs2, ecs")).toEqual(["ecs1", "ecs2", "ecs"]);
	});

	it("preserves the entry order of an array", () => {
		expect(getHostNames(["ecs1-system", "ecs-system"])).toEqual(["ecs1-system", "ecs-system"]);
	});

	it("supports semicolon separators and trims whitespace", () => {
		expect(getHostNames(" b-host ;a-host; c-host ")).toEqual(["b-host", "a-host", "c-host"]);
	});

	it("deduplicates case-insensitively, keeping the first occurrence", () => {
		expect(getHostNames("Alpha, beta, ALPHA, beta")).toEqual(["Alpha", "beta"]);
	});

	it("drops empty entries", () => {
		expect(getHostNames("host1,, ,host2,")).toEqual(["host1", "host2"]);
	});

	it("returns an empty list for null, undefined, or blank input", () => {
		expect(getHostNames(null)).toEqual([]);
		expect(getHostNames(undefined)).toEqual([]);
		expect(getHostNames("  ")).toEqual([]);
	});
});

describe("normalizeHostNameValue", () => {
	it("keeps a single host name as a plain string", () => {
		expect(normalizeHostNameValue("ecs1")).toBe("ecs1");
		expect(normalizeHostNameValue(["ecs1"])).toBe("ecs1");
	});

	it("emits multiple host names as an array in entry order", () => {
		expect(normalizeHostNameValue("zeta, alpha, mike")).toEqual(["zeta", "alpha", "mike"]);
		expect(normalizeHostNameValue(["ecs1", "ecs"])).toEqual(["ecs1", "ecs"]);
	});

	it("returns an empty string when nothing is entered", () => {
		expect(normalizeHostNameValue("")).toBe("");
		expect(normalizeHostNameValue(null)).toBe("");
	});
});
