import { describe, expect, it } from "vitest";
import { collectProtocolConfigErrors } from "./protocol-definitions";

describe("collectProtocolConfigErrors SSH auth mode", () => {
	const base = {
		username: "admin",
		port: 22,
		timeout: "2m",
	};

	it("requires private key when private key auth is selected", () => {
		const errors = collectProtocolConfigErrors(
			"ssh",
			{ ...base, password: "secret", privateKey: "", _authMethod: "privateKey" },
			{ hostId: "server-1", hostName: "server-1" },
		);
		expect(errors.privateKey).toBe("Private key is required");
		expect(errors.password).toBeUndefined();
	});

	it("does not accept a password when private key auth is selected", () => {
		const errors = collectProtocolConfigErrors(
			"ssh",
			{ ...base, password: "secret", privateKey: "", _authMethod: "privateKey" },
			{ hostId: "server-1", hostName: "server-1" },
		);
		expect(errors.privateKey).toBeTruthy();
	});

	it("requires password when password auth is selected", () => {
		const errors = collectProtocolConfigErrors(
			"ssh",
			{ ...base, password: "", privateKey: "/home/user/.ssh/id_rsa", _authMethod: "password" },
			{ hostId: "server-1", hostName: "server-1" },
		);
		expect(errors.password).toBe("Password is required");
		expect(errors.privateKey).toBeUndefined();
	});
});
