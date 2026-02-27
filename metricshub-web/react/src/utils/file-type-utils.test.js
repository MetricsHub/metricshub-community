import { describe, it, expect } from "vitest";
import { isVmFile, getFileType } from "./file-type-utils";

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
