import { describe, expect, it } from "vitest";
import { collectProtocolConfigErrors } from "./protocol-definitions";

describe("collectProtocolConfigErrors SSH credentials", () => {
	const base = {
		username: "admin",
		port: 22,
		timeout: "2m",
	};

	it("requires a password or a private key on a remote host", () => {
		const errors = collectProtocolConfigErrors(
			"ssh",
			{ ...base, password: "", privateKey: "" },
			{ hostId: "server-1", hostName: "server-1" },
		);
		expect(errors.password).toBe("Password or private key is required");
	});

	it("accepts a password alone", () => {
		const errors = collectProtocolConfigErrors(
			"ssh",
			{ ...base, password: "secret", privateKey: "" },
			{ hostId: "server-1", hostName: "server-1" },
		);
		expect(errors.password).toBeUndefined();
		expect(errors.privateKey).toBeUndefined();
	});

	it("accepts a private key alone", () => {
		const errors = collectProtocolConfigErrors(
			"ssh",
			{ ...base, password: "", privateKey: "/home/admin/.ssh/id_rsa" },
			{ hostId: "server-1", hostName: "server-1" },
		);
		expect(errors.password).toBeUndefined();
		expect(errors.privateKey).toBeUndefined();
	});

	it("does not require credentials on localhost", () => {
		const errors = collectProtocolConfigErrors(
			"ssh",
			{ ...base, password: "", privateKey: "" },
			{ hostId: "localhost", hostName: "localhost" },
		);
		expect(errors.password).toBeUndefined();
	});
});

// Protocol hostnames are matched to host.name entries by position. Partial
// override arrays are valid: blank slots fall back to the host.name entry at
// payload build (buildProtocolHostnamePayload), so no count check applies.
describe("collectProtocolConfigErrors protocol hostname", () => {
	const ping = { timeout: 5 };

	it("accepts a partial override array (blank slots use host.name entries)", () => {
		const errors = collectProtocolConfigErrors(
			"ping",
			{ ...ping, hostname: ["host1", "", "host3"] },
			{ hostId: "multi", hostName: ["host1-sys", "host2-sys", "host3-sys"] },
		);
		expect(errors.hostname).toBeUndefined();
	});

	it("accepts fewer protocol hostnames than host.name entries (legacy clamp)", () => {
		const errors = collectProtocolConfigErrors(
			"ping",
			{ ...ping, hostname: ["host1", "host2"] },
			{ hostId: "multi", hostName: ["host1-sys", "host2-sys", "host3-sys"] },
		);
		expect(errors.hostname).toBeUndefined();
	});

	it("accepts one protocol hostname per host.name entry", () => {
		const errors = collectProtocolConfigErrors(
			"ping",
			{ ...ping, hostname: ["host1", "host2", "host3"] },
			{ hostId: "multi", hostName: ["host1-sys", "host2-sys", "host3-sys"] },
		);
		expect(errors.hostname).toBeUndefined();
	});

	it("accepts an empty protocol hostname (host.name entries are used)", () => {
		const errors = collectProtocolConfigErrors(
			"ping",
			{ ...ping, hostname: "" },
			{ hostId: "multi", hostName: ["host1-sys", "host2-sys"] },
		);
		expect(errors.hostname).toBeUndefined();
	});

	it("accepts a single override hostname on a single-host resource", () => {
		const errors = collectProtocolConfigErrors(
			"ping",
			{ ...ping, hostname: "collect-host" },
			{ hostId: "server-1", hostName: "server-1" },
		);
		expect(errors.hostname).toBeUndefined();
	});

	it("rejects multiple override hostnames on a single-host resource", () => {
		// The payload builder would silently keep only the first value.
		const errors = collectProtocolConfigErrors(
			"ping",
			{ ...ping, hostname: "proxy-a,proxy-b" },
			{ hostId: "server-1", hostName: "server-1" },
		);
		expect(errors.hostname).toBe(
			"Define a single hostname (this resource has one host.name entry)",
		);
	});
});
