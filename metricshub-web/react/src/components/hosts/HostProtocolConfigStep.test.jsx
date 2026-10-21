import * as React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { httpRequest } from "../../utils/axios-request";
import HostProtocolConfigStep from "./HostProtocolConfigStep";
import { PROTOCOL_DEFAULTS } from "./protocol-definitions";

beforeEach(() => vi.clearAllMocks());

describe("protocol credential errors", () => {
	it.each(["wmi", "winrm"])(
		"sends an empty-credential %s override to the backend and displays its error",
		async (protocol) => {
			const message = "Credentials are required for remote-server";
			if (protocol === "wmi") {
				httpRequest.mockResolvedValueOnce({ data: { errorMessage: message } });
			} else {
				httpRequest.mockRejectedValueOnce({ response: { status: 400, data: { message } } });
			}
			const { container } = render(
				<HostProtocolConfigStep
					protocol={protocol}
					hostName="localhost"
					values={{ ...PROTOCOL_DEFAULTS[protocol], hostname: "remote-server" }}
					onChange={vi.fn()}
					deferEncryptUntilSave
				/>,
			);
			expect(screen.getByText("Username").parentElement).not.toHaveTextContent("*");
			expect(container.querySelector('input[type="password"]')).not.toBeRequired();
			fireEvent.click(screen.getByRole("button", { name: "Test connection" }));
			expect((await screen.findByText(message)).closest('[role="alert"]')).toBeInTheDocument();
			const request = httpRequest.mock.calls[0][0];
			expect(request.url).toBe("/api/ui-config/protocol-check");
			expect(request.data.hostname).toBe("remote-server");
			expect(request.data.protocolConfig[protocol].hostname).toBe("remote-server");
			expect(request.data.protocolConfig[protocol]).not.toHaveProperty("username");
			expect(request.data.protocolConfig[protocol]).not.toHaveProperty("password");
		},
	);
});
