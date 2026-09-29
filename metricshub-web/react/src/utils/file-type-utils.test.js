import { describe, it, expect } from "vitest";
import {
	isVmFile,
	isDraftFile,
	getFileType,
	stripDraftSuffix,
	isSameConfigFile,
} from "./file-type-utils";

describe("isVmFile", () => {
	it("returns true for .vm files", () => {
		expect(isVmFile("template.vm")).toBe(true);
		expect(isVmFile("my-config.vm")).toBe(true);
	});

	it("returns true for .vm.draft files", () => {
		expect(isVmFile("template.vm.draft")).toBe(true);
	});

	it("is case-insensitive", () => {
		expect(isVmFile("template.VM")).toBe(true);
		expect(isVmFile("template.Vm")).toBe(true);
		expect(isVmFile("template.VM.draft")).toBe(true);
	});

	it("returns false for yaml files", () => {
		expect(isVmFile("config.yaml")).toBe(false);
		expect(isVmFile("config.yml")).toBe(false);
		expect(isVmFile("config.yaml.draft")).toBe(false);
	});

	it("returns false for null/empty/undefined", () => {
		expect(isVmFile(null)).toBe(false);
		expect(isVmFile("")).toBe(false);
		expect(isVmFile(undefined)).toBe(false);
	});
});

describe("getFileType", () => {
	it("returns 'vm' for Velocity template files", () => {
		expect(getFileType("template.vm")).toBe("vm");
	});

	it("returns 'vm' for Velocity template draft files", () => {
		expect(getFileType("template.vm.draft")).toBe("vm");
	});

	it("returns 'backup' for backup files", () => {
		expect(getFileType("backup-20251016-104205__config.yaml")).toBe("backup");
	});

	it("returns 'file' for regular yaml files", () => {
		expect(getFileType("config.yaml")).toBe("file");
		expect(getFileType("config.yml")).toBe("file");
	});

	it("returns 'file' for yaml draft files", () => {
		expect(getFileType("config.yaml.draft")).toBe("file");
	});
});

describe("stripDraftSuffix", () => {
	it("removes a trailing .draft suffix", () => {
		expect(stripDraftSuffix("new-config.vm.draft")).toBe("new-config.vm");
	});

	it("leaves a saved file name untouched", () => {
		expect(stripDraftSuffix("new-config.vm")).toBe("new-config.vm");
	});

	it("tolerates null and undefined", () => {
		expect(stripDraftSuffix(null)).toBe("");
		expect(stripDraftSuffix(undefined)).toBe("");
	});
});

describe("isSameConfigFile", () => {
	it("matches a draft against its saved counterpart", () => {
		expect(isSameConfigFile("new-config.vm.draft", "new-config.vm")).toBe(true);
	});

	it("ignores the case", () => {
		expect(isSameConfigFile("Hosts.vm", "hosts.vm")).toBe(true);
	});

	it("does not match different files", () => {
		expect(isSameConfigFile("new-config.vm", "new-config-1.vm")).toBe(false);
		expect(isSameConfigFile("hosts.vm", "hosts.yaml")).toBe(false);
	});
});

describe("isDraftFile", () => {
	it("returns true for a draft", () => {
		expect(isDraftFile("new-config.vm.draft")).toBe(true);
		expect(isDraftFile("metricshub.yaml.draft")).toBe(true);
	});

	it("is case-insensitive", () => {
		expect(isDraftFile("new-config.vm.DRAFT")).toBe(true);
	});

	it("returns false for a saved file", () => {
		expect(isDraftFile("new-config.vm")).toBe(false);
		expect(isDraftFile("draft.vm")).toBe(false);
	});

	it("tolerates null and undefined", () => {
		expect(isDraftFile(null)).toBe(false);
		expect(isDraftFile(undefined)).toBe(false);
	});
});
