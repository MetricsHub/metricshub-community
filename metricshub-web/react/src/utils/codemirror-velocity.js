import { StreamLanguage } from "@codemirror/language";

/**
 * Lightweight Velocity Template Language (VTL) tokenizer for CodeMirror 6.
 *
 * Highlights:
 * - Directives: #set, #if, #else, #elseif, #end, #foreach, #macro, etc.
 * - Variables: $var, $!var, ${var}, $!{var}, $tool.method()
 * - Comments: ## single-line and #* multi-line *#
 * - Strings: single-quoted and double-quoted
 * - Operators: ==, !=, &&, ||, >=, <=, >, <, !
 * - Numbers
 * - Everything else: plain text (since output is typically YAML)
 */
function velocityTokenizer() {
	return {
		startState() {
			return { inMultiLineComment: false };
		},
		token(stream, state) {
			// multi-line comment continuation
			if (state.inMultiLineComment) {
				if (stream.match(/.*?\*#/)) {
					state.inMultiLineComment = false;
				} else {
					stream.skipToEnd();
				}
				return "comment";
			}
			// multi-line comment start
			if (stream.match("#*")) {
				state.inMultiLineComment = true;
				return "comment";
			}
			// single-line comment
			if (stream.match(/^##.*/)) {
				return "comment";
			}
			// directives
			if (
				stream.match(
					/#(set|if|elseif|else|end|foreach|in|macro|include|parse|stop|break|define|evaluate)\b/,
				)
			) {
				return "keyword";
			}
			// variables: $! or $ followed by { ... } or word characters with dots
			if (stream.match(/\$!?\{[^}]*\}/) || stream.match(/\$!?[a-zA-Z_][\w.]*/)) {
				return "variableName";
			}
			// strings
			if (stream.match(/"[^"]*"/) || stream.match(/'[^']*'/)) {
				return "string";
			}
			// operators
			if (stream.match(/==|!=|&&|\|\||>=|<=|[><!=]/)) {
				return "operator";
			}
			// numbers
			if (stream.match(/\d+(\.\d+)?/)) {
				return "number";
			}
			stream.next();
			return null;
		},
	};
}

/**
 * Returns a CodeMirror language extension for Apache Velocity Template Language.
 * @returns {import("@codemirror/language").StreamLanguage} The Velocity language support.
 */
export const velocity = () => StreamLanguage.define(velocityTokenizer());
