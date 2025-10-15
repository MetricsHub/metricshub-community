/**
 * Extract editor range and cleaned message from a YAML validation error.
 * @param {any} err - One error object from the backend.
 * @param {import("@codemirror/view").EditorView} view - The CodeMirror view.
 * @returns {{from:number,to:number,message:string}} Range info for highlighting.
 */
export function extractYamlErrorRange(err, view) {
	if (!view || !err) return { from: 0, to: 0, message: String(err?.message || err) };
	const doc = view.state.doc;
	const message = err.message || String(err || "");

	let from = 0,
		to = 0;
	const ln = Number(err.line ?? err.lineNumber ?? err.ln);
	const col = Number(err.column ?? err.col ?? err.columnNumber);
	const match = !ln || !col ? /line\s*(\d+),?\s*column\s*(\d+)/i.exec(message) : null;

	const lineNum = Math.min(Math.max(1, ln || Number(match?.[1]) || 1), doc.lines);
	const colNum = Math.max(1, col || Number(match?.[2]) || 1);
	const line = doc.line(lineNum);

	from = Math.min(line.to, Math.max(line.from, line.from + (colNum - 1)));
	let t = from;
	while (t < doc.length && /\w/.test(doc.sliceString(t, t + 1))) t++;
	to = t > from ? t : Math.min(doc.length, from + 1);

	return { from, to, message };
}

import { linter, lintGutter } from "@codemirror/lint";

const normalizeMessage = (raw) => {
	if (!raw) return "";
	let txt = String(raw).trim();

	// collapse exact repeats (abcabc → abc)
	for (let k = 2; k <= 4; k++) {
		if (txt.length % k !== 0) continue;
		const part = txt.slice(0, txt.length / k);
		if (part.repeat(k) === txt) {
			txt = part.trim();
			break;
		}
	}

	// remove consecutive identical lines
	const lines = txt.split(/\r?\n/);
	const out = [];
	for (let i = 0; i < lines.length; i++) {
		if (i === 0 || lines[i] !== lines[i - 1]) out.push(lines[i]);
	}
	return out.join("\n").trim();
};

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

export function toDiagnostics(result, cmDoc, shortYamlError) {
	if (!result || result.valid) return [];

	if (Array.isArray(result.errors) && result.errors.length > 0) {
		const seen = new Set();
		const diags = [];
		for (const e of result.errors) {
			try {
				const from = makePos(cmDoc, e.line ?? e.ln ?? 1, e.column ?? e.col ?? 1);
				const to = makePos(
					cmDoc,
					e.endLine ?? e.endLn ?? e.line ?? e.ln ?? 1,
					e.endColumn ?? e.endCol ?? (e.column ?? e.col ?? 1) + 1,
				);
				const msg = normalizeMessage(shortYamlError(e.message || e.msg || "Validation error"));
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
				const msg = normalizeMessage(
					shortYamlError(e?.message || err?.message || "Validation error"),
				);
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
				{
					from,
					to: Math.max(end, from + 1),
					message: normalizeMessage(shortYamlError(text)),
					severity: "error",
				},
			];
		}
		return [
			{
				from: 0,
				to: Math.min(1, cmDoc.length),
				message: normalizeMessage(shortYamlError(text)),
				severity: "error",
			},
		];
	}

	return [];
}

/** Small factory for your existing `validateFn(content, fileName)` */
export function buildYamlLinterExtension(validateFn, fileName, shortYamlError, delay = 400) {
	if (!validateFn || !fileName) return [];
	return [
		lintGutter(),
		linter(
			async (view) => {
				const content = view.state.doc.toString();
				try {
					const res = await validateFn(content, fileName);
					return toDiagnostics(res, view.state.doc, shortYamlError);
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
