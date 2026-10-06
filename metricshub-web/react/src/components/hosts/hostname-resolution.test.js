import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { uiConfigApi } from "../../api/ui-config";
import { useHostConfig } from "./useHostConfig";
import { runProtocolCheck } from "./protocol-test";
import { PROTOCOL_DEFAULTS } from "./protocol-definitions";
import { hostConfigToFormState, buildHostPayloadFromForm } from "./host-config-utils";

vi.mock("../../api/ui-config", () => ({
	uiConfigApi: {
		getAgentHostname: vi.fn(),
		getConnectorCatalog: vi.fn().mockResolvedValue([]),
		checkProtocol: vi.fn(),
	},
}));

beforeEach(() => {
	vi.clearAllMocks();
	window.sessionStorage.clear();
	uiConfigApi.getAgentHostname.mockResolvedValue("ec-win");
	uiConfigApi.checkProtocol.mockResolvedValue({ hostUp: 1 });
});

describe("resolved agent hostname in the form", () => {
	it.each(["pc-elyes", "localhost"])(
		"opens a resolved configuration with %s without clicking the button",
		async (hostname) => {
			uiConfigApi.getAgentHostname.mockResolvedValue(hostname);
			const initialState = hostConfigToFormState(
				"localhost",
				{
					attributes: { "host.name": hostname, "host.type": "windows" },
					protocols: { wmi: {} },
				},
				null,
			);
			const { result } = renderHook(() => useHostConfig({ mode: "edit", initialState }));
			await waitFor(() => expect(result.current.allStepsValid).toBe(true));
			expect(result.current.state.hostName).toBe(hostname);
			expect(buildHostPayloadFromForm(result.current.state).attributes["host.name"]).toBe(hostname);
			expect(result.current.state.hostId).toBe("localhost");
		},
	);
	it("allows saving the agent hostname without credentials and rejects expressions", async () => {
		const { result } = renderHook(() => useHostConfig({ mode: "create" }));
		await waitFor(() => expect(result.current.agentHostname).toBe("ec-win"));
		act(() =>
			result.current.patchState({
				hostId: "my-resource",
				hostName: "ec-win",
				hostType: "windows",
				selectedProtocols: ["wmi"],
				protocols: { wmi: PROTOCOL_DEFAULTS.wmi },
			}),
		);
		await waitFor(() => expect(result.current.allStepsValid).toBe(true));
		act(() => result.current.patchState({ hostName: "${env::COMPUTERNAME:-localhost}" }));
		expect(result.current.allStepsValid).toBe(false);
		act(() => result.current.validateBasics());
		expect(result.current.errors.hostName).toContain("Environment expressions are not supported");
	});
	it("reports lookup failure without inventing a resolved hostname", async () => {
		uiConfigApi.getAgentHostname.mockRejectedValue(new Error("Offline"));
		const { result } = renderHook(() => useHostConfig({ mode: "create" }));
		await waitFor(() => expect(result.current.agentHostnameError).toBeTruthy());
		expect(result.current.agentHostname).toBe("");
		expect(result.current.agentHostnameLoading).toBe(false);
	});
});

describe("protocol checks", () => {
	it.each(["wmi", "winrm"])(
		"tests %s with the resolved agent hostname and empty credentials",
		async (protocol) => {
			const result = await runProtocolCheck({
				protocol,
				protocolValues: PROTOCOL_DEFAULTS[protocol],
				hostname: "ec-win",
				hostName: "ec-win",
				hostId: "my-resource",
				agentHostname: "ec-win",
			});
			expect(result.severity).toBe("success");
			expect(uiConfigApi.checkProtocol).toHaveBeenCalledWith(
				expect.objectContaining({ hostname: "ec-win" }),
				expect.anything(),
			);
		},
	);
	it("blocks an expression before contacting the server", async () => {
		const result = await runProtocolCheck({
			protocol: "wmi",
			protocolValues: PROTOCOL_DEFAULTS.wmi,
			hostname: "${env::COMPUTERNAME:-localhost}",
			hostName: "localhost",
			hostId: "localhost",
		});
		expect(result.severity).toBe("warning");
		expect(uiConfigApi.checkProtocol).not.toHaveBeenCalled();
	});
});
