package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * The quoting at a position of a POSIX shell command line, as {@code sh} reads it, for a value expanded there as
 * an argument. The answer is {@link #UNKNOWN} where the reading depends on constructs this class does not follow (a
 * comment, a here-document, {@code $'...'}, a command substitution inside double quotes, backquotes), and where
 * the value would not be a mere argument: the name of a command, the target of a redirection, or an argument of a
 * command that evaluates its arguments again ({@code eval}, a shell, an interpreter, arithmetic).
 */
public enum ShellQuote {
	/**
	 * Outside of any quote.
	 */
	NONE,
	/**
	 * Inside single quotes.
	 */
	SINGLE,
	/**
	 * Inside double quotes.
	 */
	DOUBLE,
	/**
	 * The quoting cannot be told, or the value would not be a mere argument.
	 */
	UNKNOWN;

	/**
	 * The characters that end a command: the next word starts another one.
	 */
	private static final String COMMAND_BREAKS = ";&|()\n";

	/**
	 * The commands that run their arguments as code or as another command, or evaluate them as arithmetic. A word
	 * of a command matching one of them (by its last path segment) makes the rest of the command {@link #UNKNOWN}.
	 */
	private static final Set<String> EVALUATING_COMMANDS = Set.of(
		"eval",
		"exec",
		"command",
		"builtin",
		"source",
		".",
		"trap",
		"sh",
		"bash",
		"ksh",
		"zsh",
		"dash",
		"ash",
		"csh",
		"tcsh",
		"su",
		"sudo",
		"ssh",
		"env",
		"xargs",
		"nohup",
		"timeout",
		"nice",
		"time",
		"watch",
		"awk",
		"gawk",
		"nawk",
		"mawk",
		"perl",
		"python",
		"python2",
		"python3",
		"ruby",
		"php",
		"node",
		"let",
		"declare",
		"typeset",
		"local"
	);

	/**
	 * The words after which the next word starts a command again.
	 */
	private static final Set<String> COMMAND_PREFIXES = Set.of(
		"if",
		"then",
		"else",
		"elif",
		"do",
		"while",
		"until",
		"!",
		"{"
	);

	/**
	 * @param commandLine a POSIX shell command line
	 * @param position    the position of a value to be expanded as an argument
	 * @return the quoting at the position
	 */
	public static ShellQuote at(final String commandLine, final int position) {
		return new Reader(commandLine).quoteAt(position);
	}

	/**
	 * The state of the command around a parenthesis.
	 *
	 * @param substitution whether the parenthesis opens a command substitution, {@code $(}, rather than a subshell
	 * @param evaluating   whether the command around evaluates its arguments again
	 * @param nameOrTarget whether the word holding the substitution is a command name or a redirection target
	 */
	private record Frame(boolean substitution, boolean evaluating, boolean nameOrTarget) {}

	/**
	 * Reads a command line up to a position, following its quotes, words and commands.
	 */
	private static final class Reader {

		private final String commandLine;
		private ShellQuote quote = NONE;
		private final StringBuilder word = new StringBuilder();
		private boolean inWord;
		private boolean commandNext = true;
		private boolean targetNext;
		private boolean wordIsNameOrTarget;
		private boolean evaluating;
		private final Deque<Frame> frames = new ArrayDeque<>();

		private Reader(final String commandLine) {
			this.commandLine = commandLine;
		}

		/**
		 * @param position the position of a value
		 * @return the quoting at the position
		 */
		private ShellQuote quoteAt(final int position) {
			for (int i = 0; i < position; i++) {
				i = read(i, position);
				if (i < 0) {
					return UNKNOWN;
				}
			}
			final boolean nameOrTarget = inWord ? wordIsNameOrTarget : commandNext || targetNext;
			return nameOrTarget || evaluating ? UNKNOWN : quote;
		}

		/**
		 * @param i        the position of the character to read
		 * @param position the position of the value
		 * @return the position of the last character read, {@code -1} if the reading cannot go on
		 */
		private int read(final int i, final int position) {
			final char c = commandLine.charAt(i);
			if (quote == SINGLE) {
				if (c == '\'') {
					quote = NONE;
				}
				return i;
			}
			if (c == '\\') {
				startWord();
				return i + 1;
			}
			if (quote == DOUBLE) {
				return readDoubleQuoted(i, position);
			}
			if (isUnfollowed(i)) {
				return -1;
			}
			if (c == ' ' || c == '\t' || c == '\r') {
				endWord();
			} else if (c == '(' && inWord && commandLine.charAt(i - 1) == '$') {
				// A command substitution: commands of their own, then part of the word around it
				frames.push(new Frame(true, evaluating, wordIsNameOrTarget));
				inWord = false;
				breakCommand();
			} else if (c == ')' && !frames.isEmpty() && frames.peek().substitution()) {
				endWord();
				final Frame frame = frames.pop();
				// A command whose name is computed is not known
				evaluating = frame.evaluating() || frame.nameOrTarget();
				inWord = true;
				wordIsNameOrTarget = frame.nameOrTarget();
				commandNext = false;
				targetNext = false;
			} else if (COMMAND_BREAKS.indexOf(c) >= 0) {
				if (c == '(') {
					frames.push(new Frame(false, false, false));
				} else if (c == ')' && !frames.isEmpty()) {
					frames.pop();
				}
				endWord();
				breakCommand();
			} else if (c == '<' || c == '>') {
				endWord();
				targetNext = true;
			} else {
				startWord();
				if (c == '\'') {
					quote = SINGLE;
				} else if (c == '"') {
					quote = DOUBLE;
				} else {
					word.append(c);
				}
			}
			return i;
		}

		/**
		 * @param i        the position of a character inside double quotes
		 * @param position the position of the value
		 * @return the position of the last character read, {@code -1} if the reading cannot go on
		 */
		private int readDoubleQuoted(final int i, final int position) {
			final char c = commandLine.charAt(i);
			if (c == '"') {
				quote = NONE;
				return i;
			}
			if (c == '`' || commandLine.startsWith("$(", i)) {
				return -1;
			}
			if (commandLine.startsWith("${", i)) {
				// A reference or a parameter expansion, skipped if it holds no quote of its own
				final int close = commandLine.indexOf('}', i);
				if (close < 0 || close >= position) {
					return -1;
				}
				final boolean quoted = commandLine
					.substring(i, close)
					.chars()
					.anyMatch(ch -> "'\"`\\".indexOf(ch) >= 0);
				return quoted ? -1 : close;
			}
			return i;
		}

		private void breakCommand() {
			commandNext = true;
			targetNext = false;
			evaluating = false;
		}

		private void startWord() {
			if (!inWord) {
				inWord = true;
				wordIsNameOrTarget = commandNext || targetNext;
				commandNext = false;
				targetNext = false;
				word.setLength(0);
			}
		}

		private void endWord() {
			if (!inWord) {
				return;
			}
			inWord = false;
			final String text = word.toString();
			// The last path segment, after a %{SUDO:...} macro too
			final String name = text.substring(Math.max(text.lastIndexOf('/'), text.lastIndexOf('}')) + 1);
			if (EVALUATING_COMMANDS.contains(name)) {
				evaluating = true;
			}
			if (wordIsNameOrTarget && COMMAND_PREFIXES.contains(text)) {
				commandNext = true;
			}
		}

		/**
		 * @param i a position outside of any quote
		 * @return whether a construct this class does not follow starts there: backquotes, {@code $'...'}, a
		 *         here-document, arithmetic ({@code $((...))}, {@code ((...))}, {@code [[...]]}) or a comment
		 */
		private boolean isUnfollowed(final int i) {
			if (commandLine.charAt(i) == '`') {
				return true;
			}
			if (commandLine.startsWith("$'", i) || commandLine.startsWith("<<", i) || commandLine.startsWith("$((", i)) {
				return true;
			}
			if (inWord) {
				return false;
			}
			return (commandLine.charAt(i) == '#' || commandLine.startsWith("((", i) || commandLine.startsWith("[[", i));
		}
	}
}
