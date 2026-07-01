import { describe, expect, it } from "vitest";
import {
	annotateConnectorCatalog,
	applyAdditionalConnectorsChange,
	collectConnectorVariablesErrors,
	connectorDocumentationUrl,
	connectorMatchesCategoryTab,
	connectorMatchesListFilters,
	dedupeConnectorCatalogById,
	evaluateConnectorCompatibility,
	filterDirectivesForAdditionalConnectorChips,
	formatInlineConnectorsText,
	hasSelectionOrForceDirectives,
	normalizeInstanceVariableValues,
	parseConnectorDirective,
	parseInlineConnectorsText,
	partitionExcludeDirectives,
	readInstanceVariableValues,
	reconcileExcludeDirectives,
	removeConnectorDirectiveByRaw,
	removeDirectivesForValue,
	shouldDisableConnectorExcludes,
	upsertConnectorDirective,
} from "./connector-utils";

const linuxSshConnector = {
	id: "Linux",
	displayName: "Linux",
	appliesToHostTypes: ["linux"],
	appliesToDisplayNames: ["Linux"],
	connectionTypes: ["REMOTE"],
	requiredProtocols: ["ssh"],
};

describe("evaluateConnectorCompatibility", () => {
	it("marks connector compatible when host.type and protocols match", () => {
		const result = evaluateConnectorCompatibility(linuxSshConnector, {
			hostType: "linux",
			protocols: ["ssh"],
		});
		expect(result.compatible).toBe(true);
		expect(result.incompatibilityReasons).toEqual([]);
	});

	it("marks connector incompatible when protocols do not overlap", () => {
		const result = evaluateConnectorCompatibility(linuxSshConnector, {
			hostType: "linux",
			protocols: ["snmp"],
		});
		expect(result.compatible).toBe(false);
		expect(result.incompatibilityReasons[0]).toContain("Requires at least one of these protocols");
	});

	it("annotates catalog entries in memory", () => {
		const annotated = annotateConnectorCatalog([linuxSshConnector], {
			hostType: "linux",
			protocols: ["ssh", "ping"],
		});
		expect(annotated[0].compatible).toBe(true);
	});
});

describe("connector directive parsing", () => {
	it("parses plain connector names as selection", () => {
		expect(parseConnectorDirective("HPUXSystem")).toEqual({
			kind: "select",
			value: "HPUXSystem",
			raw: "HPUXSystem",
		});
	});

	it("parses force and exclude prefixes", () => {
		expect(parseConnectorDirective("+HPUXSystem").kind).toBe("force");
		expect(parseConnectorDirective("!HPUXSystem").kind).toBe("exclude");
	});

	it("detects when exclusions should be disabled", () => {
		expect(hasSelectionOrForceDirectives(["HPUXSystem", "!Linux"])).toBe(true);
		expect(hasSelectionOrForceDirectives(["#Windows"])).toBe(true);
		expect(hasSelectionOrForceDirectives(["!Linux", "!#Windows"])).toBe(false);
	});
});

describe("connector directive removal", () => {
	it("removes connector directives when deselecting force, exclude, or select", () => {
		const directives = ["+Linux", "!Windows", "HPUXSystem", "#hardware"];
		expect(removeDirectivesForValue(directives, "Linux", ["force", "exclude", "select"])).toEqual([
			"!Windows",
			"#hardware",
			"HPUXSystem",
		]);
	});

	it("removes tag directives when deselecting include or exclude tag", () => {
		const directives = ["+Linux", "#Windows", "!#MIB2"];
		expect(removeDirectivesForValue(directives, "Windows", ["include-tag", "exclude-tag"])).toEqual(
			["!#MIB2", "+Linux"],
		);
	});

	it("removes a single directive chip by raw value", () => {
		const directives = ["+Linux", "+Windows"];
		expect(removeConnectorDirectiveByRaw(directives, "+Linux")).toEqual(["+Windows"]);
	});

	it("replaces an existing connector directive when toggling selection", () => {
		expect(upsertConnectorDirective(["Linux"], "force", "Linux")).toEqual(["+Linux"]);
		expect(upsertConnectorDirective(["+Linux"], "exclude", "Linux")).toEqual(["!Linux"]);
		expect(upsertConnectorDirective(["+Linux"], "select", "Linux")).toEqual(["Linux"]);
	});

	it("hides force/select directives represented by additional connector instance chips", () => {
		expect(
			filterDirectivesForAdditionalConnectorChips(
				["+PureStorageREST", "+PureStorageREST-1", "Linux"],
				{
					PureStorageREST: { uses: "PureStorageREST" },
					"PureStorageREST-1": { uses: "PureStorageREST" },
				},
			),
		).toEqual(["Linux"]);
	});
});

describe("reconcileExcludeDirectives", () => {
	it("stashes exclude directives when a connector is selected or forced", () => {
		const result = reconcileExcludeDirectives(["!Linux", "!Windows", "+HPUX"], []);
		expect(result.active).toEqual(["+HPUX"]);
		expect(result.stashedExcludes).toEqual(["!Linux", "!Windows"]);
	});

	it("restores stashed exclude directives when nothing is selected or forced", () => {
		const result = reconcileExcludeDirectives([], ["!Linux", "!#Windows"]);
		expect(result.active).toEqual(["!#Windows", "!Linux"]);
		expect(result.stashedExcludes).toEqual([]);
	});

	it("partitions exclude connector and tag directives", () => {
		expect(partitionExcludeDirectives(["+Linux", "!Windows", "!#HPE"])).toEqual({
			excludes: ["!#HPE", "!Windows"],
			rest: ["+Linux"],
		});
	});
});

describe("inline connector directives text", () => {
	it("formats directives as a comma-separated line", () => {
		expect(formatInlineConnectorsText(["+Linux", "Windows", "#HPE"])).toBe(
			"#HPE , +Linux , Windows",
		);
	});

	it("parses comma-separated directives", () => {
		expect(parseInlineConnectorsText("Linux , +Windows , #HPE , !Solaris")).toEqual([
			"!Solaris",
			"#HPE",
			"+Windows",
			"Linux",
		]);
	});
});

describe("shouldDisableConnectorExcludes", () => {
	it("disables excludes when only exclude directives are present but an instance exists", () => {
		expect(
			shouldDisableConnectorExcludes({
				directives: ["!Linux"],
				additionalConnectors: {
					MyPure: { uses: "PureStorageREST", force: false, variables: {} },
				},
			}),
		).toBe(true);
	});

	it("disables excludes when a variable connector template is configured", () => {
		expect(
			shouldDisableConnectorExcludes({
				directives: ["!Linux"],
				selectedVariableConnectorTemplates: ["PureStorageREST"],
			}),
		).toBe(true);
	});

	it("keeps excludes enabled when only exclude directives are present", () => {
		expect(
			shouldDisableConnectorExcludes({
				directives: ["!Linux", "!#Windows"],
			}),
		).toBe(false);
	});
});

describe("applyAdditionalConnectorsChange", () => {
	it("adds a forced instance and upserts a force directive", () => {
		const patch = applyAdditionalConnectorsChange(
			{
				connectors: [],
				additionalConnectors: {},
				connectorDetectionMode: "automatic",
			},
			{
				MyPure: { uses: "PureStorageREST", force: true, variables: { restQueryPath: "/api" } },
			},
		);
		expect(patch.additionalConnectors).toEqual({
			MyPure: { uses: "PureStorageREST", force: true, variables: { restQueryPath: "/api" } },
		});
		expect(patch.connectors).toEqual(["+MyPure"]);
		expect(patch.connectorDetectionMode).toBe("manual");
	});

	it("removes force directive when an instance is deleted", () => {
		const patch = applyAdditionalConnectorsChange(
			{
				connectors: ["+OldInstance", "Linux"],
				additionalConnectors: {
					OldInstance: { uses: "PureStorageREST", force: true, variables: {} },
				},
				connectorDetectionMode: "manual",
			},
			{},
		);
		expect(patch.connectors).toEqual(["Linux"]);
		expect(patch.additionalConnectors).toEqual({});
	});
});

describe("collectConnectorVariablesErrors", () => {
	it("requires at least one instance per selected template", () => {
		const result = collectConnectorVariablesErrors({
			additionalConnectors: {},
			selectedVariableConnectorTemplates: ["PureStorageREST"],
		});
		expect(result.valid).toBe(false);
		expect(result.errors._connectorVariables).toMatch(/at least one instance/i);
	});

	it("flags an empty in-progress connector ID edit", () => {
		const result = collectConnectorVariablesErrors({
			additionalConnectors: {
				MyPure: { uses: "PureStorageREST", force: true, variables: {} },
			},
			selectedVariableConnectorTemplates: ["PureStorageREST"],
			editDraft: { instanceKey: "MyPure", instanceId: "" },
		});
		expect(result.valid).toBe(false);
		expect(result.errors["additionalConnectors.MyPure.id"]).toMatch(/required/i);
		expect(result.highlightInstanceId).toBe("MyPure");
	});

	it("flags duplicate connector IDs in the active edit", () => {
		const result = collectConnectorVariablesErrors({
			additionalConnectors: {
				MyPure: { uses: "PureStorageREST", force: true, variables: {} },
				Other: { uses: "PureStorageREST", force: true, variables: {} },
			},
			selectedVariableConnectorTemplates: ["PureStorageREST"],
			editDraft: { instanceKey: "Other", instanceId: "MyPure" },
		});
		expect(result.valid).toBe(false);
		expect(result.errors["additionalConnectors.Other.id"]).toMatch(/already in use/i);
		expect(result.highlightInstanceId).toBe("Other");
	});
});

describe("instance variable values", () => {
	const defs = [{ name: "restQueryPath", defaultValue: "/api" }];

	it("preserves explicit empty strings when reading stored values", () => {
		expect(readInstanceVariableValues({ restQueryPath: "" }, defs)).toEqual({ restQueryPath: "" });
	});

	it("falls back to defaults only for keys missing from stored values", () => {
		expect(readInstanceVariableValues({}, defs)).toEqual({ restQueryPath: "/api" });
	});

	it("keeps cleared values when normalizing for persistence", () => {
		expect(normalizeInstanceVariableValues({ restQueryPath: "" }, defs)).toEqual({
			restQueryPath: "",
		});
	});
});

describe("connector category tabs", () => {
	it("dedupes catalog entries by connector id", () => {
		const linux = { id: "LinuxProcess", displayName: "Linux Process", tags: ["System"] };
		const deduped = dedupeConnectorCatalogById([linux, { ...linux, information: "dup" }]);
		expect(deduped).toHaveLength(1);
		expect(deduped[0].information).toBeUndefined();
	});

	it("filters connectors by category tag", () => {
		const systemConnector = { id: "LinuxProcess", tags: ["System"] };
		const storageConnector = { id: "Pure", tags: ["Storage"] };
		expect(connectorMatchesCategoryTab(systemConnector, "system")).toBe(true);
		expect(connectorMatchesCategoryTab(systemConnector, "storage")).toBe(false);
		expect(connectorMatchesListFilters(storageConnector, "", new Set(), "system")).toBe(false);
	});
});

describe("connectorDocumentationUrl", () => {
	it("builds the connector documentation URL from the connector id in lowercase", () => {
		expect(connectorDocumentationUrl("Linux")).toBe(
			"https://metricshub.com/docs/latest/connectors/linux",
		);
	});

	it("encodes special characters in connector ids", () => {
		expect(connectorDocumentationUrl("my connector")).toBe(
			"https://metricshub.com/docs/latest/connectors/my%20connector",
		);
	});
});
