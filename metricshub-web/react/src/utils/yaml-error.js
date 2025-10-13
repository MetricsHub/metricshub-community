// Utility to extract a concise human-readable YAML parser error from a verbose backend message
// Exports: shortYamlError(raw)

export function shortYamlError(raw) {
	if (!raw) return "";
	let txt = String(raw).trim();

	// Drop Java/JSON-like stack/source suffixes that start with "at [Source:" or similar
	txt = txt.replace(/\bat \[Source:[\s\S]*$/i, "");

	// Normalize newlines and trim each line
	const lines = txt.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);

	// Try to find the most descriptive message line
	let msgLine = lines.find((l) => /could not find expected|could not find|expected .*:|error/i.test(l));
	if (!msgLine) {
		// prefer a human-readable first line that isn't just context like "while scanning..."
		msgLine = lines.find((l) => !/^while\s+scanning/i.test(l) && !/^in\s+/i.test(l)) || lines[0] || "";
	}

	// Try to extract explicit source name + location like: in 'reader', line 11, column 1
	const locMatch = txt.match(/in\s+['"]?([^'",]+)['"]?\s*,?\s*line\s*(\d+)\s*,?\s*column\s*(\d+)/i);
	if (locMatch) {
		const source = locMatch[1];
		const ln = locMatch[2];
		const col = locMatch[3];
		// Build a compact single-line message
		return `${msgLine} in ${source} line ${ln} column ${col}`;
	}

	// If no explicit "in ... line X" present, fall back to searching a generic "line N, column M"
	const genericLoc = txt.match(/line\s*(\d+)\s*,?\s*column\s*(\d+)/i);
	if (genericLoc) {
		return `${msgLine} line ${genericLoc[1]} column ${genericLoc[2]}`;
	}

	// Final fallback: return the chosen message line collapsed
	return msgLine.replace(/\s+/g, " ").trim();
}
