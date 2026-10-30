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
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The quoting at a position of a POSIX shell command line, as {@code sh} reads it, for a value expanded there as
 * an argument. The answer is {@link #UNKNOWN} where the reading depends on constructs this class does not follow (a
 * comment, a here-document, {@code $'...'}, a command substitution inside double quotes, backquotes), and where
 * the value would not be a mere argument: the name of a command, the target of a redirection, or an argument of a
 * command that evaluates its arguments again ({@code eval}, a shell, an interpreter, arithmetic), or an operand
 * choosing what a command does ({@code systemctl stop}).
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
		"chroot",
		"setsid",
		"stdbuf",
		"ionice",
		"taskset",
		"runuser",
		"doas",
		"pkexec",
		"flock",
		"strace",
		"ltrace",
		"unbuffer",
		"expect",
		"parallel",
		"at",
		"batch",
		"script",
		"busybox",
		"nsenter",
		"unshare",
		"systemd-run",
		// Their operands are programs or assignments: of=/dev/sda, a sed script running e or writing w
		"dd",
		"sed",
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
	 * The commands whose first operands choose what they do rather than what they act on, with how many:
	 * {@code systemctl stop sshd}, {@code service sshd stop}. Until the connector has written that many, an operand
	 * is {@link #UNKNOWN}. A word right after an option is taken for its value, not for one of them.
	 */
	private static final Map<String, Integer> ACTION_OPERANDS = Map.ofEntries(
		Map.entry("systemctl", 1),
		Map.entry("service", 2),
		Map.entry("rc-service", 2),
		Map.entry("update-rc.d", 2),
		Map.entry("chkconfig", 2),
		Map.entry("initctl", 1),
		Map.entry("svcadm", 1),
		Map.entry("launchctl", 1),
		Map.entry("supervisorctl", 1),
		Map.entry("loginctl", 1),
		Map.entry("timedatectl", 1),
		Map.entry("hostnamectl", 1),
		Map.entry("docker", 1),
		Map.entry("podman", 1),
		Map.entry("kubectl", 1),
		Map.entry("crictl", 1),
		Map.entry("virsh", 1),
		Map.entry("ip", 2),
		Map.entry("nmcli", 2),
		Map.entry("ufw", 1),
		Map.entry("zfs", 1),
		Map.entry("zpool", 1),
		Map.entry("lvm", 1),
		Map.entry("apt", 1),
		Map.entry("apt-get", 1),
		Map.entry("yum", 1),
		Map.entry("dnf", 1),
		Map.entry("zypper", 1),
		Map.entry("snap", 1),
		Map.entry("git", 1)
	);

	/**
	 * The shells, which run their arguments as code after a {@code -c} option ({@code -c}, {@code -ec}...), and
	 * only pass them to their script otherwise.
	 */
	private static final Set<String> SHELLS = Set.of("sh", "bash", "ksh", "zsh", "dash", "ash", "csh", "tcsh");

	/**
	 * An assignment word, which keeps the next word the command name ({@code MODE=x cmd}, {@code PATH+=:/x cmd},
	 * {@code a[0]=x cmd}).
	 */
	private static final Pattern ASSIGNMENT = Pattern.compile(
		"[A-Za-z_][A-Za-z0-9_]*(?:\\[[^\\]]*\\])?\\+?=.*",
		Pattern.DOTALL
	);

	/**
	 * The start of an assignment to an array element, {@code a[...}, whose subscript is arithmetic.
	 */
	private static final Pattern SUBSCRIPT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*\\[[^\\]]*", Pattern.DOTALL);

	/**
	 * The options whose next word is a command, whatever the command they are given to ({@code find -exec}).
	 */
	private static final Set<String> COMMAND_OPTIONS = Set.of("-exec", "-execdir", "-ok", "-okdir");

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
	 * @param shellSeen    whether the command around runs a shell
	 * @param actionsLeft  the action operands the command around still expects (see {@link #ACTION_OPERANDS})
	 */
	private record Frame(
		boolean substitution,
		boolean evaluating,
		boolean nameOrTarget,
		boolean shellSeen,
		int actionsLeft
	) {}

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
		private boolean wordIsCommandName;
		private boolean evaluating;
		private boolean shellSeen;
		private int actionsLeft;
		private boolean afterOption;
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
			// An action operand, unless in an option word (--unit=...)
			final boolean action = actionsLeft > 0 && !(inWord && word.length() > 0 && word.charAt(0) == '-');
			// In the subscript of an array assignment, a[...]=, which is arithmetic
			final boolean subscript = inWord && wordIsCommandName && SUBSCRIPT.matcher(word).matches();
			// In a command substitution, whose output the shell splits and globs again
			final boolean substituted = frames.stream().anyMatch(Frame::substitution);
			return nameOrTarget || evaluating || action || subscript || substituted ? UNKNOWN : quote;
		}

		/**
		 * @param i        the position of the character to read
		 * @param position the position of the value
		 * @return the position of the last character read, {@code -1} if the reading cannot go on
		 */
		private int read(final int i, final int position) {
			final char c = commandLine.charAt(i);
			// The word keeps the characters as the shell reads them, quotes and backslashes removed: e\val is eval
			if (quote == SINGLE) {
				if (c == '\'') {
					quote = NONE;
				} else {
					word.append(c);
				}
				return i;
			}
			if (c == '\\') {
				if (commandLine.startsWith("\n", i + 1)) {
					// A line continuation adds no character: ev\<newline>al is eval
					return i + 1;
				}
				startWord();
				if (i + 1 < commandLine.length()) {
					word.append(commandLine.charAt(i + 1));
				}
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
				frames.push(new Frame(true, evaluating, wordIsNameOrTarget, shellSeen, actionsLeft));
				inWord = false;
				breakCommand();
			} else if (c == ')' && !frames.isEmpty() && frames.peek().substitution()) {
				endWord();
				final Frame frame = frames.pop();
				// A command whose name is computed is not known
				evaluating = frame.evaluating() || frame.nameOrTarget();
				shellSeen = frame.shellSeen();
				actionsLeft = frame.actionsLeft();
				// Back in the word holding the substitution, whose value is not known
				inWord = true;
				word.setLength(0);
				word.append('$');
				wordIsNameOrTarget = frame.nameOrTarget();
				wordIsCommandName = false;
				commandNext = false;
				targetNext = false;
			} else if (commandLine.startsWith("&>", i)) {
				// Redirects both outputs: the > that follows starts the target
				endWord();
			} else if (COMMAND_BREAKS.indexOf(c) >= 0) {
				if (c == '(') {
					frames.push(new Frame(false, false, false, false, 0));
				} else if (c == ')' && !frames.isEmpty()) {
					frames.pop();
				}
				endWord();
				breakCommand();
			} else if (c == '<' || c == '>') {
				if (inWord && wordIsCommandName && word.chars().allMatch(Character::isDigit)) {
					// A descriptor, 2>, is not the command name
					inWord = false;
					commandNext = true;
				}
				endWord();
				targetNext = true;
				// >&, <& and >| are one operator: the descriptor or the file after them is the target
				if (i + 1 < commandLine.length() && "&|".indexOf(commandLine.charAt(i + 1)) >= 0) {
					return i + 1;
				}
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
				// An expansion: its value is not known
				word.append('$');
				return quoted ? -1 : close;
			}
			word.append(c);
			return i;
		}

		private void breakCommand() {
			commandNext = true;
			targetNext = false;
			evaluating = false;
			shellSeen = false;
			actionsLeft = 0;
			afterOption = false;
		}

		private void startWord() {
			if (!inWord) {
				inWord = true;
				wordIsNameOrTarget = commandNext || targetNext;
				wordIsCommandName = commandNext && !targetNext;
				// A redirection target leaves the command name to come: >/dev/null cmd
				commandNext = commandNext && targetNext;
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
			if (wordIsCommandName && ASSIGNMENT.matcher(text).matches()) {
				// MODE=x cmd: the next word is still the command
				commandNext = true;
				return;
			}
			if (wordIsCommandName && !text.startsWith("${file::") && (text.indexOf('$') >= 0 || text.indexOf('`') >= 0)) {
				// A command whose name is expanded is not known; an embedded file is the connector's own script
				evaluating = true;
			}
			// The last path segment, after a %{SUDO:...} macro too
			final String name = text.substring(Math.max(text.lastIndexOf('/'), text.lastIndexOf('}')) + 1);
			if (EVALUATING_COMMANDS.contains(name)) {
				evaluating = true;
			}
			readActionWord(text, name);
			readShellWord(text, name);
			if ((wordIsCommandName && COMMAND_PREFIXES.contains(text)) || COMMAND_OPTIONS.contains(text)) {
				commandNext = true;
			}
		}

		/**
		 * Counts the action operands the connector writes (see {@link #ACTION_OPERANDS}): literal words, neither an
		 * option nor the value of one, nor computed.
		 *
		 * @param text the word
		 * @param name its last path segment
		 */
		private void readActionWord(final String text, final String name) {
			if (wordIsCommandName) {
				actionsLeft = ACTION_OPERANDS.getOrDefault(name, 0);
				afterOption = false;
			} else if (actionsLeft > 0 && !wordIsNameOrTarget) {
				final boolean option = text.startsWith("-");
				if (!option && !afterOption && text.indexOf('$') < 0) {
					actionsLeft--;
				}
				// -H host: the next word is the option's value; --host=x or -- carries none
				afterOption = option && !text.contains("=") && !"--".equals(text);
			}
		}

		/**
		 * Follows a shell and its options: its first operand is a script to run, like a command name, and after a
		 * {@code -c} (or {@code -ec}...) its arguments are code.
		 *
		 * @param text the word
		 * @param name its last path segment
		 */
		private void readShellWord(final String text, final String name) {
			if (SHELLS.contains(name)) {
				shellSeen = true;
				commandNext = commandNext || wordIsCommandName;
			} else if (shellSeen && !text.startsWith("--") && (text.startsWith("-") || text.startsWith("+"))) {
				if (text.startsWith("-") && text.indexOf('c') > 0) {
					evaluating = true;
				}
				// An option: the script is still to come
				commandNext = commandNext || wordIsCommandName;
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
