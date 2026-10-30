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
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("coproc X"));
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
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("PATH+=:/tmp X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("a[0]=x X"));
		assertEquals(ShellQuote.NONE, quoteOfX("MODE=x ls X"));
		// An array subscript is arithmetic
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("a[X]=1 ls"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("a[b[0]]=x X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("a[b[0]+X]=1 ls"));
		// A command substitution's output is split and globbed again
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("find . -name $(printf %s X)"));
		// A command name from an expansion is not known: a parameter, braces, a pattern
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("$CMD X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("e{v,v}al X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("/bin/b?sh X"));
		assertEquals(ShellQuote.NONE, quoteOfX("%{SUDO:/usr/sbin/raidctl}/usr/sbin/raidctl -l X"));
		assertEquals(ShellQuote.NONE, quoteOfX("[ -f X ]"));
		// The value of an option naming a program is code
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("tar --checkpoint-action=exec=X -cf a.tar ."));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("curl --proxy-command X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("ls --pager=X"));
		// A shell runs its first operand as a script, its -c argument as code; the arguments of a script are passed
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("sh X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("bash -o pipefail -c X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("sh -ec 'X'"));
		// After -- or an option's value, still the script
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("bash -- X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("bash -O extglob X"));
		assertEquals(ShellQuote.NONE, quoteOfX("bash -x script.sh X"));
		// A backslash escapes the first character of the value: its quote or its $
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("echo \"\\X\""));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("echo \\X"));
		assertEquals(ShellQuote.NONE, quoteOfX("echo \\\\X"));
		assertEquals(ShellQuote.NONE, quoteOfX("/bin/sh ${file::detail.sh} X"));
		assertEquals(ShellQuote.NONE, quoteOfX("${file::check.sh} X"));
		// Arguments again after the command ends
		assertEquals(ShellQuote.NONE, quoteOfX("eval date; echo X"));
		assertEquals(ShellQuote.NONE, quoteOfX("echo $(eval date) X"));
		assertEquals(ShellQuote.DOUBLE, quoteOfX("if true; then grep \"X\" f; fi"));
	}

	@Test
	void testActionOperands() {
		// The operands choosing what the command does, before the connector writes them
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("systemctl X sshd"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("/usr/bin/systemctl --no-pager X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("systemctl -H host X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("systemctl -- X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("service sshd X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("ip link X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("git remote X origin"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("docker container X web"));
		// exec runs its operands as a command, wherever it comes
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("docker container exec web X /data"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("systemctl $(echo) X"));
		// Once written, what it acts on
		assertEquals(ShellQuote.NONE, quoteOfX("systemctl status X"));
		assertEquals(ShellQuote.NONE, quoteOfX("systemctl --no-pager=1 status --lines=5 X"));
		assertEquals(ShellQuote.NONE, quoteOfX("systemctl status --unit=X"));
		assertEquals(ShellQuote.NONE, quoteOfX("service sshd status; echo X"));
		assertEquals(ShellQuote.NONE, quoteOfX("ip addr show X"));
		assertEquals(ShellQuote.NONE, quoteOfX("docker container logs X"));
	}

	@Test
	void testLineContinuation() {
		// The shell removes a backslash-newline: ev<newline>al is eval
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("ev\\\nal X"));
		assertEquals(ShellQuote.NONE, quoteOfX("echo \\\n X"));
	}

	@Test
	void testRedirectionsBeforeTheCommand() {
		// A redirection, its descriptor and its target leave the command name to come
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("2>&1 X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX(">/dev/null X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("2>/dev/null <&0 X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX(">|out X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("&>log X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX("{fd}>/tmp/log X"));
		assertEquals(ShellQuote.UNKNOWN, quoteOfX(">sh X"));
		// After the command name, an argument
		assertEquals(ShellQuote.NONE, quoteOfX("ls 2>&1 X"));
		assertEquals(ShellQuote.NONE, quoteOfX(">/dev/null ls X"));
		assertEquals(ShellQuote.NONE, quoteOfX("ls >|out X"));
	}
}
