import { linter, lintGutter } from "@codemirror/lint";

/** Small factory for your existing `validateFn(content, fileName)` */
export function buildYamlLinterExtension(validateFn, fileName, delay = 400) {
	if (!validateFn || !fileName) return [];

	// Local helpers kept private to this module for minimal public API
	const normalizeMessage = (raw) => (raw == null ? "" : String(raw));
	const makePos = (cmDoc, lineNum, column) => {
		let ln = typeof lineNum === "number" ? lineNum : 1;
		if (ln <= 0) ln += 1; // accept 0-based
		ln = Math.max(1, Math.min(cmDoc.lines, ln));

		let col = typeof column === "number" ? column : 1;
		if (col <= 0) col += 1;

		const lineInfo = cmDoc.line(ln);
		const safeCol = Math.max(1, Math.min(lineInfo.length + 1, col));
		return lineInfo.from + (safeCol - 1);
	};

	const toDiagnostics = (result, cmDoc) => {
		if (!result || result.valid) return [];

		if (Array.isArray(result.errors) && result.errors.length > 0) {
			const seen = new Set();
			const diags = [];
			for (const e of result.errors) {
				try {
					const line = e.line ?? e.ln;
					const column = e.column ?? e.col;

					// If line/column are -1 or null/undefined, skip inline diagnostics.
					const hasPosition =
						typeof line === "number" && typeof column === "number" && line > 0 && column > 0;
					if (!hasPosition) continue; // header shows these

					const from = makePos(cmDoc, line, column);
					const to = makePos(
						cmDoc,
						e.endLine ?? e.endLn ?? line,
						e.endColumn ?? e.endCol ?? column + 1,
					);
					const msg = normalizeMessage(e.message || e.msg || "Validation error");
					const key = `${from}:${to}:${msg}`;
					if (seen.has(key)) continue;
					seen.add(key);
					diags.push({
						from,
						to: Math.max(to, from + 1),
						message: msg,
						severity: e.severity || "error",
					});
				} catch (err) {
					const msg = normalizeMessage(e?.message || err?.message || "Validation error");
					const key = `0:1:${msg}`;
					if (!seen.has(key)) {
						seen.add(key);
						diags.push({ from: 0, to: Math.min(1, cmDoc.length), message: msg, severity: "error" });
					}
				}
			}
			return diags;
		}

		if (result.error) {
			const text = String(result.error || "");
			const m = /line\s+(\d+)\s*,?\s*column\s+(\d+)/i.exec(text);
			if (m) {
				const ln = parseInt(m[1], 10) || 1;
				const col = parseInt(m[2], 10) || 1;
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

	return [
		lintGutter(),
		linter(
			async (view) => {
				const content = view.state.doc.toString();
				try {
					const res = await validateFn(content, fileName);
					return toDiagnostics(res, view.state.doc);
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
