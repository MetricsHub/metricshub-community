import { beforeEach, describe, expect, it, vi } from "vitest";
import { createElement } from "react";
import { act, fireEvent, render, renderHook, screen, waitFor } from "@testing-library/react";
import { uiConfigApi } from "../../api/ui-config";
import { useHostConfig } from "./useHostConfig";
import { runProtocolCheck } from "./protocol-test";
import { PROTOCOL_DEFAULTS, PROTOCOL_FIELDS } from "./protocol-definitions";
import { hostConfigToFormState, buildHostPayloadFromForm } from "./host-config-utils";
import HostNameChipInput from "./HostNameChipInput";
import ProtocolConfigForm from "./ProtocolConfigForm";

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
	it("allows saving a remote hostname without credentials, or an expression", async () => {
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
		await waitFor(() => expect(result.current.allStepsValid).toBe(true));
		act(() => result.current.validateBasics());
		expect(result.current.errors.hostName).toBeUndefined();
		expect(buildHostPayloadFromForm(result.current.state).attributes["host.name"]).toBe(
			"${env::COMPUTERNAME:-localhost}",
		);
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

	it.each([
		["${env::UNSET_HOST}", "${env::COLLECTION_HOST}"],
		[
			["remote", "${env::UNSET_HOST}"],
			["remote-proxy", "${env::COLLECTION_HOST}"],
		],
	])(
		"preserves unresolved YAML hostnames through unrelated edits: %j",
		async (hostName, hostname) => {
			const initialState = hostConfigToFormState("unresolved", {
				attributes: { "host.name": hostName, "host.type": "windows" },
				protocols: { wmi: { hostname } },
				originalHostnames: { hostName, protocols: { wmi: hostname } },
			});
			const { result } = renderHook(() => useHostConfig({ mode: "edit", initialState }));
			await waitFor(() => expect(result.current.allStepsValid).toBe(true));
			act(() => result.current.patchProtocolField("wmi", "timeout", 60));
			act(() => result.current.patchState({ hostId: "renamed" }));
			act(() => expect(result.current.validateAllSteps()).toBeNull());
			const payload = buildHostPayloadFromForm(result.current.state);
			expect(payload.attributes["host.name"]).toEqual(hostName);
			expect(payload.protocols.wmi.hostname).toEqual(hostname);
			expect(payload.protocols.wmi.timeout).toBe(60);
			expect(payload.hostId).toBe("renamed");
		},
	);

	it("shows unresolved fields without changing their YAML values, and replaces them only on input", async () => {
		const hostName = "${env::UNSET_HOST}";
		const hostname = "${env::COLLECTION_HOST}";
		const initialState = hostConfigToFormState("unresolved", {
			attributes: { "host.name": hostName, "host.type": "windows" },
			protocols: { wmi: { hostname } },
			originalHostnames: { hostName, protocols: { wmi: hostname } },
		});
		let form;
		const Form = () => {
			form = useHostConfig({ mode: "edit", initialState });
			return createElement(
				"div",
				null,
				createElement(HostNameChipInput, {
					staticLabel: true,
					value: form.state.hostName,
					originalValue: form.state.originalHostName,
					onChange: (value) => form.patchState({ hostName: value }),
					onResolveHostname: form.fetchAgentHostname,
				}),
				createElement(ProtocolConfigForm, {
					protocol: "wmi",
					fields: PROTOCOL_FIELDS.wmi
						.filter((field) => field.name === "hostname")
						.map((field) => ({ ...field, advanced: false })),
					values: form.state.protocols.wmi,
					onChange: (name, value) => form.patchProtocolField("wmi", name, value),
					errors: form.errors,
				}),
			);
		};
		render(createElement(Form));
		await waitFor(() => expect(form.allStepsValid).toBe(true));
		const [resourceField, protocolField] = screen.getAllByPlaceholderText("Unresolved hostname");
		for (const field of [resourceField, protocolField]) {
			expect(field).toHaveValue("");
			expect(field).not.toHaveAttribute("aria-invalid", "true");
			fireEvent.focus(field);
			fireEvent.blur(field);
		}
		expect(form.isDirty).toBe(false);
		expect(buildHostPayloadFromForm(form.state).attributes["host.name"]).toBe(hostName);
		expect(buildHostPayloadFromForm(form.state).protocols.wmi.hostname).toBe(hostname);
		// Any typed value, an expression included, is a hostname.
		fireEvent.change(resourceField, { target: { value: "${env::OTHER_HOST}" } });
		expect(form.allStepsValid).toBe(true);
		expect(buildHostPayloadFromForm(form.state).attributes["host.name"]).toBe("${env::OTHER_HOST}");
		fireEvent.click(screen.getByRole("button", { name: "Use agent hostname" }));
		await waitFor(() => expect(form.state.hostName).toBe("ec-win"));
		expect(form.allStepsValid).toBe(true);
		expect(buildHostPayloadFromForm(form.state).attributes["host.name"]).toBe("ec-win");
		fireEvent.change(protocolField, { target: { value: "${env::OTHER_HOST}" } });
		expect(form.allStepsValid).toBe(true);
		fireEvent.change(protocolField, { target: { value: "remote-proxy" } });
		expect(form.allStepsValid).toBe(true);
		expect(buildHostPayloadFromForm(form.state).protocols.wmi.hostname).toBe("remote-proxy");
	});

	it("keeps every name of a mixed list visible and editable without dropping the others", async () => {
		const hostName = ["${env::UNSET_HOST}", "remote-host"];
		const hostname = ["${env::COLLECTION_HOST}", "remote-proxy"];
		const initialState = hostConfigToFormState("mixed", {
			attributes: { "host.name": hostName, "host.type": "windows" },
			protocols: { wmi: { hostname } },
			originalHostnames: { hostName, protocols: { wmi: hostname } },
		});
		let form;
		const Form = () => {
			form = useHostConfig({ mode: "edit", initialState });
			return createElement(
				"div",
				null,
				createElement(HostNameChipInput, {
					staticLabel: true,
					value: form.state.hostName,
					originalValue: form.state.originalHostName,
					allowedExpressions: form.state.unresolvedHostNames,
					onChange: (value) => form.patchState({ hostName: value }),
				}),
				createElement(ProtocolConfigForm, {
					protocol: "wmi",
					fields: PROTOCOL_FIELDS.wmi
						.filter((field) => field.name === "hostname")
						.map((field) => ({ ...field, advanced: false })),
					values: form.state.protocols.wmi,
					onChange: (name, value) => form.patchProtocolField("wmi", name, value),
					errors: form.errors,
					hostNames: hostName,
				}),
			);
		};
		render(createElement(Form));
		await waitFor(() => expect(form.allStepsValid).toBe(true));
		expect(screen.queryByPlaceholderText("Unresolved hostname")).toBeNull();
		expect(screen.getAllByText("${env::UNSET_HOST}").length).toBeGreaterThan(0);
		expect(screen.getAllByText("remote-host").length).toBeGreaterThan(0);
		expect(screen.getByText(/Unresolved YAML hostname kept as-is/)).toBeInTheDocument();

		const [input] = screen.getAllByRole("combobox");
		fireEvent.change(input, { target: { value: "new-host" } });
		fireEvent.keyDown(input, { key: "Enter" });
		await waitFor(() =>
			expect(form.state.hostName).toEqual(["${env::UNSET_HOST}", "remote-host", "new-host"]),
		);
		expect(form.allStepsValid).toBe(true);
		const payload = buildHostPayloadFromForm(form.state);
		expect(payload.attributes["host.name"]).toEqual([
			"${env::UNSET_HOST}",
			"remote-host",
			"new-host",
		]);
		// The new host gets its own name as protocol hostname, like any other added host.
		expect(payload.protocols.wmi.hostname).toEqual([...hostname, "new-host"]);
	});

	it("accepts a new expression added to a mixed list", async () => {
		const hostName = ["${env::UNSET_HOST}", "remote-host"];
		const initialState = hostConfigToFormState("mixed", {
			attributes: { "host.name": hostName, "host.type": "windows" },
			protocols: { wmi: {} },
			originalHostnames: { hostName },
		});
		const { result } = renderHook(() => useHostConfig({ mode: "edit", initialState }));
		await waitFor(() => expect(result.current.allStepsValid).toBe(true));
		act(() => result.current.patchState({ hostName: [...hostName, "${env::OTHER_HOST}"] }));
		expect(result.current.allStepsValid).toBe(true);
		expect(buildHostPayloadFromForm(result.current.state).attributes["host.name"]).toEqual([
			...hostName,
			"${env::OTHER_HOST}",
		]);
	});

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
	it("sends an expression to the server like any other hostname", async () => {
		const result = await runProtocolCheck({
			protocol: "wmi",
			protocolValues: PROTOCOL_DEFAULTS.wmi,
			hostname: "${env::COMPUTERNAME:-localhost}",
			hostName: "localhost",
		});
		expect(result.severity).toBe("success");
		expect(uiConfigApi.checkProtocol).toHaveBeenCalledWith(
			expect.objectContaining({ hostname: "${env::COMPUTERNAME:-localhost}" }),
			expect.anything(),
		);
	});
	it("tests another host of a list that contains an unresolved expression", async () => {
		const result = await runProtocolCheck({
			protocol: "wmi",
			protocolValues: PROTOCOL_DEFAULTS.wmi,
			hostname: "remote-host",
			hostName: ["${env::UNSET_HOST}", "remote-host"],
		});
		expect(result.severity).toBe("success");
		expect(uiConfigApi.checkProtocol).toHaveBeenCalledWith(
			expect.objectContaining({ hostname: "remote-host" }),
			expect.anything(),
		);
	});
	it("tests an unresolved target whose YAML expression is preserved", async () => {
		const expression = "${env::COLLECTION_HOST}";
		const result = await runProtocolCheck({
			protocol: "wmi",
			protocolValues: {
				...PROTOCOL_DEFAULTS.wmi,
				hostname: expression,
				_originalHostname: expression,
			},
			hostname: expression,
			hostName: "localhost",
		});
		expect(result.severity).toBe("success");
		expect(uiConfigApi.checkProtocol).toHaveBeenCalledWith(
			expect.objectContaining({ hostname: expression }),
			expect.anything(),
		);
	});
});
