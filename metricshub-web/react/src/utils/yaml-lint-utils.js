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
