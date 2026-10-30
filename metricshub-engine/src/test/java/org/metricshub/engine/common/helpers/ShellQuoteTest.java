package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShellQuoteTest {

	private static ShellQuote quoteOfX(final String commandLine) {
		return ShellQuote.at(commandLine, commandLine.indexOf('X'));
	}

	@Test
	void testQuotes() {
		assertEquals(ShellQuote.NONE, quoteOfX("rm -- X"));
		assertEquals(ShellQuote.SINGLE, quoteOfX("echo 'a X b'"));
		assertEquals(ShellQuote.DOUBLE, quoteOfX("echo \"a X b\""));
		assertEquals(ShellQuote.NONE, quoteOfX("echo 'a' \"b\" X"));
		// Quotes that do not count: escaped, or inside the other kind
		assertEquals(ShellQuote.NONE, quoteOfX("echo \\' X"));
		assertEquals(ShellQuote.DOUBLE, quoteOfX("echo \"it's X\""));
		assertEquals(ShellQuote.SINGLE, quoteOfX("echo 'say \"X'"));
		assertEquals(ShellQuote.DOUBLE, quoteOfX("echo \"a \\\" X\""));
		// A reference without quotes inside double quotes is skipped
		assertEquals(ShellQuote.DOUBLE, quoteOfX("echo \"${esc(bash)::param::a} X\""));
		// A comment, a here-document, $'...' or backquotes: cannot tell
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("echo ok # it's\necho X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("cat <<EOF\nit's\nEOF\necho X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("echo $'\\'' X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("echo `echo '` X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("echo \"$(echo \"'\")\" X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("echo \"${x:-\"'\"}\" X"));
		// Not a comment: # inside a word
		assertEquals(ShellQuote.NONE, quoteOfX("echo a#b ${#x} X"));
		// Arithmetic evaluates an expanded value again, quotes or not
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("echo $(( X + 1 ))"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("(( X > 1 ))"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("[[ X -eq 1 ]]"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("let n=X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("declare -i n; n=X"));
		// Not arithmetic: a command substitution, a word starting like a command
		assertEquals(ShellQuote.NONE, quoteOfX("echo $(ls) letter localhost X"));
	}

	@Test
	void testNotAMereArgument() {
		// A command name, after a keyword or an operator too
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("X --version"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("ls | X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("if X; then echo; fi"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("$(which ls) X"));
		// A redirection target
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("echo ok >X"));
		// An argument of a command evaluating it again, wherever that command comes in the command
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("eval X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("/usr/bin/sudo /bin/sh -c 'X'"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("eval $(date) X"));
		// Quotes and backslashes removed from a command name, as the shell reads it
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("e\\val X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("'eval' X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("\"ev\"al X"));
		// An assignment keeps the next word the command name
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("MODE=x X"));
		assertEquals(ShellQuote.NONE, quoteOfX("MODE=x ls X"));
		// A command name from an expansion is not known
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("$CMD X"));
		// A shell runs its first operand as a script, its -c argument as code; the arguments of a script are passed
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("sh X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("bash -o pipefail -c X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("sh -ec 'X'"));
		assertEquals(ShellQuote.NONE, quoteOfX("/bin/sh ${file::detail.sh} X"));
		assertEquals(ShellQuote.NONE, quoteOfX("${file::check.sh} X"));
		// Arguments again after the command ends
		assertEquals(ShellQuote.NONE, quoteOfX("eval date; echo X"));
		assertEquals(ShellQuote.NONE, quoteOfX("echo $(eval date) X"));
		assertEquals(ShellQuote.DOUBLE, quoteOfX("if true; then grep \"X\" f; fi"));
	}
}
