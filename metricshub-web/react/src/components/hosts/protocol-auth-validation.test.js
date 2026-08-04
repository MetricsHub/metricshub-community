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

// Protocol hostnames are matched to host.name entries by position, so when the
// protocol declares hostnames it must declare exactly one per host.name entry.
describe("collectProtocolConfigErrors protocol hostname count", () => {
	const ping = { timeout: 5 };

	it("rejects fewer protocol hostnames than host.name entries", () => {
		const errors = collectProtocolConfigErrors(
			"ping",
			{ ...ping, hostname: ["host1", "host2"] },
			{ hostId: "multi", hostName: ["host1-sys", "host2-sys", "host3-sys"] },
		);
		expect(errors.hostname).toBe(
			"Define one hostname per host.name entry (3 expected, 2 configured)",
		);
	});

	it("rejects more protocol hostnames than host.name entries", () => {
		const errors = collectProtocolConfigErrors(
			"ping",
			{ ...ping, hostname: ["host1", "host2"] },
			{ hostId: "server-1", hostName: "server-1" },
		);
		expect(errors.hostname).toBe(
			"Define one hostname per host.name entry (1 expected, 2 configured)",
		);
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
});
