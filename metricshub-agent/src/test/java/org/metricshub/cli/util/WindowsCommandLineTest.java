package org.metricshub.cli.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class WindowsCommandLineTest {

	private static void assertSplit(final String commandLine, final String... expected) {
		assertArrayEquals(expected, WindowsCommandLine.split(commandLine), commandLine);
	}

	@Test
	void testDoubledQuoteInsideQuotes() {
		assertSplit("sshcli.exe --password \"a\"\"b\" --command x", "--password", "a\"b", "--command", "x");
		assertSplit(
			"sshcli.exe --password \"a b\"\"c\" --command \"echo ok\"",
			"--password",
			"a b\"c",
			"--command",
			"echo ok"
		);
		assertSplit("sshcli.exe \"a\"\"\"\"b\"", "a\"\"b");
	}

	@Test
	void testBackslashEscapedQuote() {
		assertSplit("sshcli.exe --password \"a\\\"b\"", "--password", "a\"b");
		assertSplit("sshcli.exe --password a\\\"b", "--password", "a\"b");
		assertSplit("sshcli.exe a\\\\\\\"b", "a\\\"b");
	}

	@Test
	void testBackslashes() {
		assertSplit("sshcli.exe C:\\dir\\file", "C:\\dir\\file");
		assertSplit("sshcli.exe \"C:\\dir\\\\\" next", "C:\\dir\\", "next");
		assertSplit("sshcli.exe a\\\\b", "a\\\\b");
	}

	@Test
	void testProgramName() {
		assertSplit("\"C:\\Program Files\\MetricsHub\\sshcli.exe\" host --username u", "host", "--username", "u");
		assertSplit("C:\\MetricsHub\\sshcli.exe");
		assertSplit("\"C:\\Program Files\\MetricsHub\\sshcli.exe\"   ");
	}

	@Test
	void testSeparators() {
		assertSplit("sshcli.exe  a\t b   \"c d\" ", "a", "b", "c d");
		assertSplit("sshcli.exe \"\" x", "", "x");
		assertSplit("sshcli.exe a\"b c\"d", "ab cd");
	}

	@Test
	void testArgsOutsideJpackageLauncher() {
		// Unit tests do not run in a jpackage launcher: the JVM arguments are kept as they are
		final String[] args = { "--password", "a\"b" };
		assertSame(args, WindowsCommandLine.args(args));
	}
}
