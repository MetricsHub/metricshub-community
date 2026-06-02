import { EditorState } from "@codemirror/state";
import { getIndentation, IndentContext } from "@codemirror/language";
import { yaml as cmYaml } from "@codemirror/lang-yaml";
import { describe, expect, it } from "vitest";
import {
	getYamlMappingNewlineIndentColumn,
	yamlMappingIndentService,
} from "./codemirror-yaml-indent";

const indentContext = {
	unit: 2,
	countColumn: (value) => value.length,
};

/**
 * Computes the indentation column the native Enter command would apply at the end
 * of the given document, using the same extension stack as the editor: the YAML
 * language plus our indentation service layered on top. This proves the service
 * overrides CodeMirror's native YAML indentation rather than running in isolation.
 *
 * @param {string} doc The document text; indentation is queried at its end.
 * @returns {number|null} The resolved indentation column.
 */
function indentAtEnd(doc) {
	const state = EditorState.create({
		doc,
		extensions: [cmYaml(), yamlMappingIndentService],
	});
	const context = new IndentContext(state, { simulateBreak: doc.length });

	return getIndentation(context, doc.length);
}

describe("codemirror-yaml-indent", () => {
	it("keeps the current indentation after an inline mapping value with a comment", () => {
		expect(
			getYamlMappingNewlineIndentColumn("           https: true # enter key", indentContext),
		).toBe(11);
	});

	it("keeps sequence item mappings aligned with the current item", () => {
		expect(getYamlMappingNewlineIndentColumn("  - name: localhost", indentContext)).toBe(4);
	});

	it("keeps sequence scalars aligned with the current sequence item", () => {
		expect(
			getYamlMappingNewlineIndentColumn(
				"  - --config=file:C:\\ProgramData\\MetricsHub\\otel\\otel-config.yaml",
				indentContext,
			),
		).toBe(2);
		expect(getYamlMappingNewlineIndentColumn("  - https://example:443/path", indentContext)).toBe(
			2,
		);
	});

	it("resolves quoted keys with escaped quotes as mappings", () => {
		// Single-quote escaping is via doubling ('') and backslash is literal.
		expect(getYamlMappingNewlineIndentColumn("  'it''s': value", indentContext)).toBe(2);
		expect(getYamlMappingNewlineIndentColumn("  'a\\': value", indentContext)).toBe(2);
		// Double-quote escaping uses backslash, so the inner colon is ignored.
		expect(getYamlMappingNewlineIndentColumn('  "a\\":b": value', indentContext)).toBe(2);
	});

	it("defers for open flow collections but aligns balanced ones", () => {
		expect(getYamlMappingNewlineIndentColumn("  key: {", indentContext)).toBeNull();
		expect(getYamlMappingNewlineIndentColumn("  key: [1, 2,", indentContext)).toBeNull();
		expect(getYamlMappingNewlineIndentColumn("  key: { a: 1 }", indentContext)).toBe(2);
	});

	it("indents one level deeper after nested mapping keys", () => {
		expect(getYamlMappingNewlineIndentColumn("      localhost:", indentContext)).toBe(8);
		expect(getYamlMappingNewlineIndentColumn("          http:", indentContext)).toBe(12);
		expect(getYamlMappingNewlineIndentColumn("          http: # comment", indentContext)).toBe(12);
	});

	it("indents one level deeper after sequence mapping keys", () => {
		expect(getYamlMappingNewlineIndentColumn("  - group:", indentContext)).toBe(6);
	});

	it("keeps comment lines at their own indentation", () => {
		expect(
			getYamlMappingNewlineIndentColumn("  # MetricsHub Default configuration", indentContext),
		).toBe(2);
		expect(getYamlMappingNewlineIndentColumn("          # https: true", indentContext)).toBe(10);
	});

	it("falls back to CodeMirror indentation for block scalars", () => {
		expect(getYamlMappingNewlineIndentColumn("          script: |", indentContext)).toBeNull();
		expect(getYamlMappingNewlineIndentColumn("          script: >", indentContext)).toBeNull();
		// Chomping and explicit indentation indicators, in either order.
		expect(getYamlMappingNewlineIndentColumn("          script: |-", indentContext)).toBeNull();
		expect(getYamlMappingNewlineIndentColumn("          script: |+", indentContext)).toBeNull();
		expect(getYamlMappingNewlineIndentColumn("          script: >-", indentContext)).toBeNull();
		expect(getYamlMappingNewlineIndentColumn("          script: |2", indentContext)).toBeNull();
		expect(getYamlMappingNewlineIndentColumn("          script: |2-", indentContext)).toBeNull();
		expect(getYamlMappingNewlineIndentColumn("          script: >+1", indentContext)).toBeNull();
		// A block scalar header may be followed by a comment.
		expect(
			getYamlMappingNewlineIndentColumn("          script: |- # keep", indentContext),
		).toBeNull();
	});

	it("overrides native YAML indentation for an inline mapping value", () => {
		expect(indentAtEnd("      protocols:\n        https: true # enter key")).toBe(8);
	});

	it("overrides native YAML indentation after a nested mapping key", () => {
		expect(indentAtEnd("      localhost:")).toBe(8);
	});

	it("overrides native YAML indentation for a sequence scalar with a colon", () => {
		const doc =
			'otelCollector:\n  commandLine:\n  - "C:\\\\Program Files\\\\MetricsHub\\\\otel\\\\otelcol-contrib.exe"\n  - --config=file:C:\\ProgramData\\MetricsHub\\otel\\otel-config.yaml';

		expect(indentAtEnd(doc)).toBe(2);
	});

	it("overrides native YAML indentation after a comment block", () => {
		expect(
			indentAtEnd(
				"otel:\n  # OpenTelemetry SDK Autoconfigure properties\n  # MetricsHub Default configuration",
			),
		).toBe(2);
	});

	it("deepens a comment that opens an under-indented block", () => {
		expect(indentAtEnd("attributes:\n# fill me in")).toBe(2);
	});

	it("preserves an intentionally dedented comment", () => {
		expect(indentAtEnd("parent:\n  child:\n    # deep comment\n  # dedented comment")).toBe(2);
	});
});
