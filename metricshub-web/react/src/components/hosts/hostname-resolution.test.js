import { beforeEach, describe, expect, it, vi } from "vitest";
import { createElement } from "react";
import { act, fireEvent, render, renderHook, screen, waitFor } from "@testing-library/react";
import { uiConfigApi } from "../../api/ui-config";
import { useHostConfig } from "./useHostConfig";
import { runProtocolCheck } from "./protocol-test";
import { PROTOCOL_DEFAULTS } from "./protocol-definitions";
import { hostConfigToFormState, buildHostPayloadFromForm } from "./host-config-utils";
import HostNameChipInput from "./HostNameChipInput";

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

describe("preserving YAML hostname expressions", () => {
	const expression = "${env::HOSTNAME:-localhost}";
	const overrideExpression = "${env::COLLECTION_HOST:-localhost}";
	const editableState = (resourceGroup = null) =>
		hostConfigToFormState(
			"local-resource",
			{
				attributes: { "host.name": "ec-win", "host.type": "windows" },
				protocols: { wmi: { hostname: "collect-host" } },
				originalHostnames: {
					hostName: expression,
					protocols: { wmi: overrideExpression },
				},
			},
			resourceGroup,
		);

	it.each([null, "Paris"])(
		"preserves expressions when editing unrelated fields in %s",
		async (group) => {
			const initialState = editableState(group);
			const { result } = renderHook(() => useHostConfig({ mode: "edit", initialState }));
			await waitFor(() => expect(result.current.allStepsValid).toBe(true));
			expect(result.current.state.hostName).toBe("ec-win");
			expect(result.current.state.protocols.wmi.hostname).toBe("collect-host");
			act(() => result.current.patchProtocolField("wmi", "timeout", 60));
			act(() => result.current.patchState({ hostId: "renamed-resource" }));
			const payload = buildHostPayloadFromForm(result.current.state);
			expect(payload.attributes["host.name"]).toBe(expression);
			expect(payload.protocols.wmi.hostname).toBe(overrideExpression);
			expect(payload.protocols.wmi.timeout).toBe(60);
			expect(payload.hostId).toBe("renamed-resource");
			expect(payload.resourceGroup).toBe(group);
			expect(payload).not.toHaveProperty("originalHostnames");
			expect(payload.protocols.wmi).not.toHaveProperty("_originalHostname");
		},
	);

	it.each(["localhost", "remote-server"])(
		"saves the manually entered hostname %s literally",
		async (hostname) => {
			const initialState = editableState();
			const { result } = renderHook(() => useHostConfig({ mode: "edit", initialState }));
			await waitFor(() => expect(result.current.allStepsValid).toBe(true));
			act(() => result.current.patchState({ hostName: hostname }));
			const payload = buildHostPayloadFromForm(result.current.state);
			expect(payload.attributes["host.name"]).toBe(hostname);
			expect(payload.protocols.wmi.hostname).toBe(overrideExpression);
		},
	);

	it("replaces the expression when the button returns the displayed hostname, including after refresh", async () => {
		const initialState = editableState();
		let form;
		const Form = () => {
			form = useHostConfig({ mode: "edit", initialState, sessionPathname: "/edit/local-resource" });
			return createElement(HostNameChipInput, {
				staticLabel: true,
				value: form.state.hostName,
				onChange: (hostName) => form.patchState({ hostName }),
				onResolveHostname: form.fetchAgentHostname,
			});
		};
		const { unmount } = render(createElement(Form));
		await waitFor(() => expect(form.allStepsValid).toBe(true));
		fireEvent.focus(screen.getByRole("combobox"));
		fireEvent.blur(screen.getByRole("combobox"));
		expect(form.isDirty).toBe(false);
		fireEvent.click(screen.getByRole("button", { name: "Use agent hostname" }));
		await waitFor(() => expect(form.isDirty).toBe(true));
		expect(screen.getByRole("combobox")).toHaveValue("ec-win");
		expect(buildHostPayloadFromForm(form.state).attributes["host.name"]).toBe("ec-win");
		unmount();
		render(createElement(Form));
		await waitFor(() => expect(form.isDirty).toBe(true));
		expect(buildHostPayloadFromForm(form.state).attributes["host.name"]).toBe("ec-win");
		expect(uiConfigApi.getAgentHostname).toHaveBeenCalledTimes(1);
		act(() => form.resetToBaseline());
		expect(buildHostPayloadFromForm(form.state).attributes["host.name"]).toBe(expression);
	});

	it("refreshes the displayed hostname when the same YAML expression resolves differently", async () => {
		const initialState = editableState();
		const { result, rerender } = renderHook(
			({ initialState }) => useHostConfig({ mode: "edit", initialState }),
			{ initialProps: { initialState } },
		);
		await waitFor(() => expect(result.current.allStepsValid).toBe(true));
		rerender({ initialState: { ...initialState, hostName: "renamed-agent" } });
		await waitFor(() => expect(result.current.state.hostName).toBe("renamed-agent"));
		expect(buildHostPayloadFromForm(result.current.state).attributes["host.name"]).toBe(expression);
		expect(result.current.isDirty).toBe(false);
	});

	it("preserves only protocol hostnames that were not edited", async () => {
		const initialState = editableState();
		const { result } = renderHook(() => useHostConfig({ mode: "edit", initialState }));
		await waitFor(() => expect(result.current.allStepsValid).toBe(true));
		act(() => result.current.patchProtocolField("wmi", "hostname", "collect-host"));
		expect(result.current.isDirty).toBe(true);
		const payload = buildHostPayloadFromForm(result.current.state);
		expect(payload.attributes["host.name"]).toBe(expression);
		expect(payload.protocols.wmi.hostname).toBe("collect-host");
	});

	it("preserves lists and keeps protocol expressions aligned when hosts are reordered", async () => {
		const initialState = hostConfigToFormState("multi", {
			attributes: { "host.name": ["ec-win", "remote"], "host.type": "windows" },
			protocols: { wmi: { hostname: ["collect-host", "remote-proxy"] } },
			originalHostnames: {
				hostName: [expression, "remote"],
				protocols: { wmi: [overrideExpression, "remote-proxy"] },
			},
		});
		const { result } = renderHook(() => useHostConfig({ mode: "edit", initialState }));
		await waitFor(() => expect(result.current.allStepsValid).toBe(true));
		act(() => result.current.patchProtocolField("wmi", "timeout", 60));
		expect(buildHostPayloadFromForm(result.current.state).attributes["host.name"]).toEqual([
			expression,
			"remote",
		]);
		act(() => result.current.patchState({ hostName: ["remote", "ec-win"] }));
		const payload = buildHostPayloadFromForm(result.current.state);
		expect(payload.attributes["host.name"]).toEqual(["remote", "ec-win"]);
		expect(payload.protocols.wmi.hostname).toEqual(["remote-proxy", overrideExpression]);
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
