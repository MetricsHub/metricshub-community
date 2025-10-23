import { linter, lintGutter } from "@codemirror/lint";

/**
 * Factory for the existing `validateFn(content, fileName)`
 * that produces CodeMirror linter extension for YAML files.
 *
 * @param {*} validateFn - The validation function to call
 * @param {*} fileName - The name of the file being validated
 * @param {*} delay - The debounce delay for linting
 * @returns Array - CodeMirror extensions for YAML linting
 */
export function buildYamlLinterExtension(validateFn, fileName, delay = 400) {
	if (!validateFn || !fileName) return [];

	// Local helpers kept private to this module for minimal public API
	const normalizeMessage = (raw) => (raw == null ? "" : String(raw));

	/**
	 * Create a position in the CodeMirror document.
	 *
	 * @param {*} cmDoc - The CodeMirror document.
	 * @param {*} lineNum - The line number (1-based).
	 * @param {*} column - The column number (1-based).
	 * @returns {number} - The position in the document.
	 */
	const makePos = (cmDoc, lineNum, column) => {
		// Validate and normalize line number
		let ln = typeof lineNum === "number" ? lineNum : 1;
		if (ln <= 0) ln += 1; // accept 0-based

		// Clamp line number to valid range
		ln = Math.max(1, Math.min(cmDoc.lines, ln));

		// Validate and normalize column number
		let col = typeof column === "number" ? column : 1;
		if (col <= 0) col += 1;

		// Get line info
		const lineInfo = cmDoc.line(ln);
		// Clamp column number to valid range
		const safeCol = Math.max(1, Math.min(lineInfo.length + 1, col));

		// Calculate and return position
		return lineInfo.from + (safeCol - 1);
	};

	/**
	 * Convert validation result to CodeMirror diagnostics.
	 *
	 * @param {*} result - The validation result from the linter.
	 * @param {*} cmDoc - The CodeMirror document being linted.
	 * @returns {Array} - An array of diagnostic objects.
	 */
	const toDiagnostics = (result, cmDoc) => {
		if (!result || result.valid) return [];

		// If there are structured errors, use them
		if (Array.isArray(result.errors) && result.errors.length > 0) {
			const seen = new Set();
			const diags = [];
			// Loop through errors and convert to diagnostics
			for (const e of result.errors) {
				try {
					// Get line and column from error
					// Support both 'line'/'column' and 'ln'/'col'
					const line = e.line ?? e.ln;
					const column = e.column ?? e.col;

					// If line/column are -1 or null/undefined, skip inline diagnostics.
					const hasPosition =
						typeof line === "number" && typeof column === "number" && line > 0 && column > 0;
					if (!hasPosition) {
						continue;
					}

					// Create from/to positions
					const from = makePos(cmDoc, line, column);

					// If no end position is given, use the start position
					const to = makePos(
						cmDoc,
						e.endLine ?? e.endLn ?? line,
						e.endColumn ?? e.endCol ?? column + 1,
					);

					// Normalize message and create unique key
					const msg = normalizeMessage(e.message || e.msg || "Validation error");

					// Avoid duplicate diagnostics
					const key = `${from}:${to}:${msg}`;

					// Skip if already seen
					if (seen.has(key)) {
						continue;
					}
					// Mark as seen and add diagnostic
					seen.add(key);

					// Add diagnostic entry
					diags.push({
						from,
						to: Math.max(to, from + 1),
						message: msg,
						severity: e.severity || "error",
					});
				} catch (err) {
					// Fallback for any error during processing
					const msg = normalizeMessage(e?.message || err?.message || "Validation error");
					const key = `0:1:${msg}`;
					// Avoid duplicate diagnostics
					if (!seen.has(key)) {
						// Mark as seen and add diagnostic
						seen.add(key);
						diags.push({ from: 0, to: Math.min(1, cmDoc.length), message: msg, severity: "error" });
					}
				}
			}
			return diags;
		}

		// Fallback: parse error from result.error text
		if (result.error) {
			const text = String(result.error || "");
			const m = /line\s+(\d+)\s*,?\s*column\s+(\d+)/i.exec(text);
			if (m) {
				// Extract line and column numbers
				const ln = parseInt(m[1], 10) || 1;
				const col = parseInt(m[2], 10) || 1;
				// Validate and normalize line number
				const from = makePos(cmDoc, ln, col);
				const lineInfo = cmDoc.line(Math.max(1, Math.min(cmDoc.lines, ln)));
				let end = from;
				while (end < lineInfo.to && !/\s/.test(cmDoc.sliceString(end, end + 1))) end++;
				return [
					{ from, to: Math.max(end, from + 1), message: normalizeMessage(text), severity: "error" },
				];
			}
			return [
				{
					from: 0,
					to: Math.min(1, cmDoc.length),
					message: normalizeMessage(text),
					severity: "error",
				},
			];
		}

		return [];
	};

	// Return the CodeMirror extensions for linting
	return [
		// Add gutter for lint markers
		lintGutter(),
		// The linter extension
		linter(
			async (view) => {
				// Get document content
				const content = view.state.doc.toString();
				try {
					// Call the validation function
					const result = await validateFn(content, fileName);
					// Convert and return diagnostics
					return toDiagnostics(result, view.state.doc);
				} catch (err) {
					return [
						{
							from: 0,
							to: Math.min(1, view.state.doc.length),
							message: err?.message || "Validation request failed",
							severity: "error",
						},
					];
				}
			},
			{ delay },
		),
	];
}
