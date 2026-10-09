import { describe, expect, it } from "vitest";
import { collectProtocolConfigErrors, PROTOCOL_DEFAULTS } from "./protocol-definitions";

describe("protocol credentials are validated by the backend", () => {
	it.each(["ssh", "wmi", "winrm", "wbem", "http", "snmp", "snmpv3", "ipmi", "jdbc", "jmx"])(
		"allows empty credentials for %s on a remote host",
		(protocol) => {
			const errors = collectProtocolConfigErrors(
				protocol,
				{
					...PROTOCOL_DEFAULTS[protocol],
					username: "",
					password: "",
					privateKey: "",
					community: "",
					privacy: "AES",
					privacyPassword: "",
					url: "jdbc:h2:mem:test",
				},
				{ hostName: "remote-server" },
			);
			expect(errors).toEqual({});
		},
	);

	it.each(["wmi", "winrm"])(
		"allows %s remote overrides and mixed targets without credentials",
		(protocol) => {
			expect(
				collectProtocolConfigErrors(
					protocol,
					{
						...PROTOCOL_DEFAULTS[protocol],
						hostname: "remote-server",
					},
					{ hostName: "localhost" },
				),
			).toEqual({});
			expect(
				collectProtocolConfigErrors(protocol, PROTOCOL_DEFAULTS[protocol], {
					hostName: ["localhost", "remote-server"],
				}),
			).toEqual({});
		},
	);

	it("still rejects environment expressions in resource and protocol hostnames", () => {
		const errors = collectProtocolConfigErrors(
			"wmi",
			{
				...PROTOCOL_DEFAULTS.wmi,
				hostname: "${env::MY_HOST}",
			},
			{ hostName: "${env::MY_HOST}" },
		);
		expect(errors.hostName).toBeTruthy();
		expect(errors.hostname).toBeTruthy();
	});

	it("still validates non-credential fields", () => {
		expect(
			collectProtocolConfigErrors(
				"ssh",
				{
					...PROTOCOL_DEFAULTS.ssh,
					port: "invalid",
					timeout: 0,
				},
				{ hostName: "remote-server" },
			),
		).toEqual({
			port: "Must be between 1 and 65535.",
			timeout: "Enter a duration greater than 0.",
		});
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
