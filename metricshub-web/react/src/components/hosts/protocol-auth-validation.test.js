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
			{ ...base, password: "", privateKey: "-----BEGIN OPENSSH PRIVATE KEY-----" },
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
