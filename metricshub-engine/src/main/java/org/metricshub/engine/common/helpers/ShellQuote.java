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

/**
 * The quoting at a position of a POSIX shell command line, as {@code sh} reads it. Where the reading depends on
 * constructs this class does not follow (a comment, a here-document, {@code $'...'}, a command substitution inside
 * double quotes, backquotes), the answer is {@link #UNKNOWN}.
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
	 * The quoting cannot be told.
	 */
	UNKNOWN;

	/**
	 * The characters after which a {@code #} starts a comment.
	 */
	private static final String WORD_BREAKS = " \t\r\n;&|()<>";

	/**
	 * @param commandLine a POSIX shell command line
	 * @param position    a position in the command line
	 * @return the quoting at the position
	 */
	public static ShellQuote at(final String commandLine, final int position) {
		ShellQuote quote = NONE;
		for (int i = 0; i < position; i++) {
			final char c = commandLine.charAt(i);
			if (quote == SINGLE) {
				if (c == '\'') {
					quote = NONE;
				}
			} else if (c == '\\') {
				i++;
			} else if (quote == DOUBLE) {
				if (c == '"') {
					quote = NONE;
				} else if (c == '`' || commandLine.startsWith("$(", i)) {
					return UNKNOWN;
				} else if (commandLine.startsWith("${", i)) {
					// A reference or a parameter expansion, skipped if it holds no quote of its own
					i = endOfExpansion(commandLine, i, position);
					if (i < 0) {
						return UNKNOWN;
					}
				}
			} else if (c == '\'') {
				quote = SINGLE;
			} else if (c == '"') {
				quote = DOUBLE;
			} else if (isUnfollowed(commandLine, i)) {
				return UNKNOWN;
			}
		}
		return quote;
	}

	/**
	 * @param commandLine a command line
	 * @param start       the position of a {@code ${}
	 * @param position    the position the quoting is asked for
	 * @return the position of the closing {@code }}, or {@code -1} if it is missing, past {@code position}, or the
	 *         expansion holds a quote or a backslash
	 */
	private static int endOfExpansion(final String commandLine, final int start, final int position) {
		final int close = commandLine.indexOf('}', start);
		if (close < 0 || close >= position) {
			return -1;
		}
		final boolean quoted = commandLine
			.substring(start, close)
			.chars()
			.anyMatch(ch -> "'\"`\\".indexOf(ch) >= 0);
		return quoted ? -1 : close;
	}

	/**
	 * @param commandLine a command line
	 * @param i           a position outside of any quote
	 * @return whether a construct this class does not follow starts there: backquotes, {@code $'...'}, a
	 *         here-document or a comment
	 */
	private static boolean isUnfollowed(final String commandLine, final int i) {
		final char c = commandLine.charAt(i);
		if (c == '`' || commandLine.startsWith("$'", i) || commandLine.startsWith("<<", i)) {
			return true;
		}
		return c == '#' && (i == 0 || WORD_BREAKS.indexOf(commandLine.charAt(i - 1)) >= 0);
	}
}
