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
			expect(uiConfigApi.getAgentHostname).not.toHaveBeenCalled();
		},
	);
	it("allows saving a remote hostname without credentials and rejects expressions", async () => {
		const { result } = renderHook(() => useHostConfig({ mode: "create" }));
		act(() =>
			result.current.patchState({
				hostId: "my-resource",
				hostName: "remote-server",
				hostType: "windows",
				selectedProtocols: ["wmi"],
				protocols: { wmi: PROTOCOL_DEFAULTS.wmi },
			}),
		);
		await waitFor(() => expect(result.current.allStepsValid).toBe(true));
		act(() => expect(result.current.validateAllSteps()).toBeNull());
		expect(buildHostPayloadFromForm(result.current.state).protocols.wmi).not.toHaveProperty(
			"username",
		);
		expect(buildHostPayloadFromForm(result.current.state).protocols.wmi).not.toHaveProperty(
			"password",
		);
		expect(uiConfigApi.getAgentHostname).not.toHaveBeenCalled();
		act(() => result.current.patchState({ hostName: "${env::COMPUTERNAME:-localhost}" }));
		expect(result.current.allStepsValid).toBe(false);
		act(() => result.current.validateBasics());
		expect(result.current.errors.hostName).toContain("Environment expressions are not supported");
	});
	it.each(["ec-win", "localhost"])("fetches %s only when requested", async (hostname) => {
		uiConfigApi.getAgentHostname.mockResolvedValue(hostname);
		const { result } = renderHook(() => useHostConfig({ mode: "create" }));
		expect(uiConfigApi.getAgentHostname).not.toHaveBeenCalled();
		await act(async () => expect(await result.current.fetchAgentHostname()).toBe(hostname));
		expect(uiConfigApi.getAgentHostname).toHaveBeenCalledTimes(1);
		expect(result.current.agentHostnameLoading).toBe(false);
	});
	it("reports lookup failure without inventing a resolved hostname", async () => {
		uiConfigApi.getAgentHostname.mockRejectedValue(new Error("Offline"));
		const { result } = renderHook(() => useHostConfig({ mode: "create" }));
		await act(async () => expect(await result.current.fetchAgentHostname()).toBeNull());
		expect(result.current.agentHostnameError).toBeTruthy();
		expect(result.current.agentHostnameLoading).toBe(false);
	});
});

describe("protocol checks", () => {
	it.each(["wmi", "winrm"])(
		"tests %s with a remote hostname and empty credentials",
		async (protocol) => {
			const result = await runProtocolCheck({
				protocol,
				protocolValues: PROTOCOL_DEFAULTS[protocol],
				hostname: "remote-server",
				hostName: "remote-server",
			});
			expect(result.severity).toBe("success");
			expect(uiConfigApi.checkProtocol).toHaveBeenCalledWith(
				expect.objectContaining({ hostname: "remote-server" }),
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
		});
		expect(result.severity).toBe("warning");
		expect(uiConfigApi.checkProtocol).not.toHaveBeenCalled();
	});
});
