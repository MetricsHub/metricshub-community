import { indentService } from "@codemirror/language";

// Matches a YAML block scalar header: a literal (|) or folded (>) indicator,
// optionally followed by indentation (1-9) and chomping (- or +) indicators in
// either order, then end-of-line or whitespace (e.g. |, >, |-, |+, >2, |2-, >+1).
const BLOCK_SCALAR_HEADER = /^[|>](?:[1-9][-+]?|[-+][1-9]?)?(?:\s|$)/;

/**
 * Finds the indentation column CodeMirror should use after pressing Enter on a
 * YAML mapping line.
 *
 * @param {string} lineText The full current line text.
 * @param {import("@codemirror/language").IndentContext} context The indentation context.
 * @param {string} [previousLineText] The immediately preceding physical line, when available.
 * @returns {number|null} The desired indentation column, or null to use the default behavior.
 */
export function getYamlMappingNewlineIndentColumn(lineText, context, previousLineText) {
	const leadingWhitespace = lineText.match(/^\s*/)?.[0] ?? "";
	const trimmedLine = lineText.slice(leadingWhitespace.length);
	const leadingColumn = context.countColumn(leadingWhitespace);

	if (!trimmedLine) {
		return null;
	}

	// A comment line keeps its own indentation on the next line, except when it
	// directly opens a deeper block (see resolveCommentIndentColumn).
	if (trimmedLine.startsWith("#")) {
		return resolveCommentIndentColumn(leadingColumn, previousLineText, context);
	}

	const sequenceMatch = trimmedLine.match(/^-\s+/);
	const mappingText = sequenceMatch ? trimmedLine.slice(sequenceMatch[0].length) : trimmedLine;
	const mappingColonIndex = findUnquotedMappingColon(mappingText);

	if (mappingColonIndex <= 0) {
		return sequenceMatch ? leadingColumn : null;
	}

	const valueText = mappingText.slice(mappingColonIndex + 1).trimStart();
	const sequenceOffset = sequenceMatch ? context.unit : 0;

	if (!valueText || valueText.startsWith("#")) {
		return leadingColumn + sequenceOffset + context.unit;
	}

	// Block scalars (| and >) and open flow collections defer to CodeMirror, which
	// understands how to indent their continuation lines.
	if (BLOCK_SCALAR_HEADER.test(valueText) || hasUnbalancedFlowOpener(valueText)) {
		return null;
	}

	return leadingColumn + sequenceOffset;
}

/**
 * CodeMirror indentation service that fixes YAML mapping indentation when the
 * native Enter command asks for it.
 */
export const yamlMappingIndentService = indentService.of((context, pos) => {
	if (context.simulatedBreak !== pos) {
		return undefined;
	}

	const currentLine = context.lineAt(pos, -1);
	const previousLineText = getPreviousLineText(context, currentLine.from);

	return (
		getYamlMappingNewlineIndentColumn(currentLine.text, context, previousLineText) ?? undefined
	);
});

/**
 * Resolves the indentation for the line following a comment line.
 *
 * Comments keep their own indentation by default. The only exception is the
 * narrow, unambiguous case where the comment directly follows a block-opening
 * key (a line ending with an unquoted colon) and is indented at or shallower
 * than that key: there the next line belongs inside the freshly opened block.
 * Intentional dedents are preserved because a dedented comment's preceding
 * physical line is another comment, not a colon line.
 *
 * @param {number} commentColumn The comment line's own indentation column.
 * @param {string} [previousLineText] The immediately preceding physical line.
 * @param {import("@codemirror/language").IndentContext} context The indentation context.
 * @returns {number} The desired indentation column.
 */
function resolveCommentIndentColumn(commentColumn, previousLineText, context) {
	if (previousLineText == null) {
		return commentColumn;
	}

	const previousLeading = previousLineText.match(/^\s*/)?.[0] ?? "";
	const previousTrimmed = previousLineText.slice(previousLeading.length);
	const previousColumn = context.countColumn(previousLeading);
	const mappingText = previousTrimmed.replace(/^-\s+/, "");
	const colonIndex = findUnquotedMappingColon(mappingText);
	const opensBlock = colonIndex >= 0 && mappingText.slice(colonIndex + 1).trim() === "";

	if (opensBlock && commentColumn <= previousColumn) {
		return previousColumn + context.unit;
	}

	return commentColumn;
}

/**
 * Returns the text of the physical line immediately preceding the given line.
 *
 * @param {import("@codemirror/language").IndentContext} context The indentation context.
 * @param {number} lineFrom The start position of the current line.
 * @returns {string|undefined} The previous line text, or undefined when at the document start.
 */
function getPreviousLineText(context, lineFrom) {
	const { doc } = context.state;
	const currentLine = doc.lineAt(lineFrom);

	if (currentLine.number <= 1) {
		return undefined;
	}

	return doc.line(currentLine.number - 1).text;
}

/**
 * Determines whether the given value text opens a flow collection ({ or [) that
 * is not closed on the same line.
 *
 * @param {string} text The value text to inspect.
 * @returns {boolean} True when an unbalanced flow opener is present.
 */
function hasUnbalancedFlowOpener(text) {
	let depth = 0;
	let i = 0;

	while (i < text.length) {
		const char = text[i];

		if (char === '"') {
			i = skipDoubleQuoted(text, i);
			continue;
		}

		if (char === "'") {
			i = skipSingleQuoted(text, i);
			continue;
		}

		if (char === "#" && (i === 0 || /\s/.test(text[i - 1]))) {
			break;
		}

		if (char === "{" || char === "[") {
			depth += 1;
		} else if (char === "}" || char === "]") {
			depth = Math.max(0, depth - 1);
		}

		i += 1;
	}

	return depth > 0;
}

/**
 * Finds the index of the unquoted colon that separates a YAML mapping key from
 * its value, or -1 when the text is not a mapping entry.
 *
 * @param {string} text The text to scan (leading sequence markers removed).
 * @returns {number} The colon index, or -1.
 */
function findUnquotedMappingColon(text) {
	let i = 0;

	while (i < text.length) {
		const char = text[i];

		if (char === '"') {
			i = skipDoubleQuoted(text, i);
			continue;
		}

		if (char === "'") {
			i = skipSingleQuoted(text, i);
			continue;
		}

		if (char === "#" && (i === 0 || /\s/.test(text[i - 1]))) {
			return -1;
		}

		if (char === ":" && isMappingSeparator(text, i)) {
			return i;
		}

		i += 1;
	}

	return -1;
}

/**
 * Skips a double-quoted scalar starting at the opening quote. Backslash escapes
 * the following character (YAML double-quote escaping).
 *
 * @param {string} text The text being scanned.
 * @param {number} start The index of the opening double quote.
 * @returns {number} The index just past the closing quote, or the text length when unterminated.
 */
function skipDoubleQuoted(text, start) {
	for (let i = start + 1; i < text.length; i += 1) {
		const char = text[i];

		if (char === "\\") {
			i += 1;
			continue;
		}

		if (char === '"') {
			return i + 1;
		}
	}

	return text.length;
}

/**
 * Skips a single-quoted scalar starting at the opening quote. A doubled quote
 * ('') is a literal quote; backslash is literal (YAML single-quote escaping).
 *
 * @param {string} text The text being scanned.
 * @param {number} start The index of the opening single quote.
 * @returns {number} The index just past the closing quote, or the text length when unterminated.
 */
function skipSingleQuoted(text, start) {
	for (let i = start + 1; i < text.length; i += 1) {
		if (text[i] === "'") {
			if (text[i + 1] === "'") {
				i += 1;
				continue;
			}

			return i + 1;
		}
	}

	return text.length;
}

/**
 * Tests whether the character at the given index is a mapping separator colon
 * (:) followed by end-of-line or whitespace.
 *
 * @param {string} text The text being scanned.
 * @param {number} index The index of the colon to test.
 * @returns {boolean} True when the colon is a mapping separator.
 */
function isMappingSeparator(text, index) {
	const nextChar = text[index + 1] ?? "";

	return nextChar === "" || /\s/.test(nextChar);
}
