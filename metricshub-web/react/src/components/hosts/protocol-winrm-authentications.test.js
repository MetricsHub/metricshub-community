import { describe, expect, it } from "vitest";
import {
	buildProtocolConfigFromForm,
	PROTOCOL_FIELDS,
	protocolConfigToForm,
} from "./protocol-definitions";

const authField = PROTOCOL_FIELDS.winrm.find((field) => field.name === "authentications");

describe("WinRM authentications in the guided form", () => {
	it.each([[["basic"]], [["NTLM", "BASIC"]]])("preserves %j set in YAML", (authentications) => {
		const form = protocolConfigToForm("winrm", { authentications });
		expect(authField.extraOption(form.authentications)).not.toBeNull();
		expect(buildProtocolConfigFromForm("winrm", form).authentications).toEqual(
			authentications.map((auth) => auth.toUpperCase()),
		);
	});

	it.each([
		[["basic"], "Basic"],
		[["NTLM", "BASIC"], "NTLM and Basic"],
	])("labels %j as %s", (authentications, label) => {
		const form = protocolConfigToForm("winrm", { authentications });
		expect(authField.extraOption(form.authentications).label).toBe(label);
	});

	it.each([[["NTLM"]], [["KERBEROS"]], [["NTLM", "KERBEROS"]]])(
		"does not offer Basic for %j",
		(authentications) => {
			const form = protocolConfigToForm("winrm", { authentications });
			expect(authField.extraOption(form.authentications)).toBeNull();
		},
	);
});
