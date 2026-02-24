import { describe, it, expect } from "vitest";
import { velocity } from "./codemirror-velocity";

describe("codemirror-velocity", () => {
	it("exports a function that returns a StreamLanguage extension", () => {
		const ext = velocity();
		expect(ext).toBeDefined();
		// StreamLanguage.define returns a LanguageSupport or Language-like object
		expect(ext).toHaveProperty("extension");
	});

	it("can be called multiple times without error", () => {
		expect(() => velocity()).not.toThrow();
		expect(() => velocity()).not.toThrow();
	});
});
