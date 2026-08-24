import { describe, expect, it } from "vitest";
import { buildProtocolConfigFromForm } from "./protocol-definitions";

// The payload override is index-aligned with host.name (see
// PostConfigDeserializer.normalizeProtocolHostnames): full-length, in resource
// order, blanks replaced by the host.name entry, duplicates preserved.
describe("buildProtocolConfigFromForm protocol hostname", () => {
	const hostNames = ["h1", "h2", "h3"];

	it("fills blank slots with the host.name entry", () => {
		const config = buildProtocolConfigFromForm(
			"snmp",
			{ hostname: ["", "proxy", ""] },
			{ hostNames },
		);
		expect(config.hostname).toEqual(["h1", "proxy", "h3"]);
	});

	it("omits the hostname key when no slot is configured", () => {
		const config = buildProtocolConfigFromForm("snmp", { hostname: ["", "", ""] }, { hostNames });
		expect(config).not.toHaveProperty("hostname");
	});

	it("preserves duplicate override values", () => {
		const config = buildProtocolConfigFromForm(
			"ping",
			{ hostname: ["proxy", "proxy", "other"] },
			{ hostNames },
		);
		expect(config.hostname).toEqual(["proxy", "proxy", "other"]);
	});

	it("expands a legacy short list to full length (clamp-to-last)", () => {
		const config = buildProtocolConfigFromForm("ping", { hostname: ["p1", "p2"] }, { hostNames });
		expect(config.hostname).toEqual(["p1", "p2", "p2"]);
	});

	it("keeps the single-host shape a plain string", () => {
		const config = buildProtocolConfigFromForm(
			"snmp",
			{ hostname: "collect-host" },
			{ hostNames: ["server-1"] },
		);
		expect(config.hostname).toBe("collect-host");
	});

	it("omits an empty hostname on a single-host resource", () => {
		const config = buildProtocolConfigFromForm(
			"snmp",
			{ hostname: "" },
			{ hostNames: ["server-1"] },
		);
		expect(config).not.toHaveProperty("hostname");
	});

	it("takes the first non-blank value when no host names are provided", () => {
		const config = buildProtocolConfigFromForm("snmp", { hostname: "collect-host" }, {});
		expect(config.hostname).toBe("collect-host");
	});
});
