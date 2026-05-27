package org.metricshub.cli;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.regex.Pattern;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

/**
 * Utility class used by CLI entry points to safely configure ANSI support.
 */
public final class CliAnsiSupport {

	private static final String DISABLE_ANSI_PROPERTY = "metricshub.cli.disable.ansi";
	private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");

	private static boolean ansiInstalled;

	/**
	 * Utility class constructor.
	 */
	private CliAnsiSupport() {}

	/**
	 * Enable ANSI support when native access is available; otherwise switch
	 * command output to plain text by stripping escape sequences.
	 *
	 * @param cli The command line instance to configure.
	 */
	public static void install(CommandLine cli) {
		if (isAnsiDisabled() || !isNativeAccessEnabled()) {
			configurePlainText(cli);
			return;
		}

		try {
			AnsiConsole.systemInstall();
			ansiInstalled = true;
		} catch (Exception e) {
			configurePlainText(cli);
		}
	}

	/**
	 * Determine whether ANSI support has been explicitly disabled via system property.
	 *
	 * @return {@code true} when ANSI is disabled; {@code false} otherwise.
	 */
	private static boolean isAnsiDisabled() {
		return Boolean.getBoolean(DISABLE_ANSI_PROPERTY);
	}

	/**
	 * Uninstall ANSI support if it was previously installed.
	 */
	public static void uninstall() {
		if (!ansiInstalled) {
			return;
		}
		AnsiConsole.systemUninstall();
		ansiInstalled = false;
	}

	/**
	 * Determine whether native access has been enabled for the running JVM.
	 *
	 * @return {@code true} when native access is enabled or not required on this JDK.
	 */
	private static boolean isNativeAccessEnabled() {
		return CliAnsiSupport.class.getModule().isNativeAccessEnabled();
	}

	/**
	 * Configure command output and error streams to strip ANSI escape sequences.
	 *
	 * @param cli The command line whose writers should be wrapped.
	 */
	private static void configurePlainText(CommandLine cli) {
		cli.setOut(createStrippingWriter(cli.getOut()));
		cli.setErr(createStrippingWriter(cli.getErr()));
	}

	/**
	 * Wrap the provided writer to transparently remove ANSI escape sequences.
	 *
	 * @param delegate The original writer.
	 * @return A writer that strips ANSI escape sequences.
	 */
	private static PrintWriter createStrippingWriter(PrintWriter delegate) {
		return new PrintWriter(new AnsiStrippingWriter(delegate), true);
	}

	/**
	 * A writer that removes ANSI escape sequences from text before writing to the underlying stream.
	 */
	private static final class AnsiStrippingWriter extends Writer {

		private final PrintWriter delegate;

		/**
		 * Build a stripping writer around an existing print writer.
		 *
		 * @param delegate The underlying print writer.
		 */
		private AnsiStrippingWriter(PrintWriter delegate) {
			this.delegate = delegate;
		}

		/**
		 * Write characters after removing ANSI escape sequences.
		 *
		 * @param cbuf Character buffer.
		 * @param off Offset from which to start reading.
		 * @param len Number of characters to write.
		 */
		@Override
		public void write(char[] cbuf, int off, int len) {
			delegate.write(stripAnsi(new String(cbuf, off, len)));
		}

		/**
		 * Write a string segment after removing ANSI escape sequences.
		 *
		 * @param str Source string.
		 * @param off Offset from which to start reading.
		 * @param len Number of characters to write.
		 */
		@Override
		public void write(String str, int off, int len) {
			delegate.write(stripAnsi(str.substring(off, off + len)));
		}

		/**
		 * Flush the underlying writer.
		 */
		@Override
		public void flush() {
			delegate.flush();
		}

		/**
		 * Close the writer by flushing the underlying stream.
		 *
		 * @throws IOException Never thrown in current implementation; kept for Writer contract.
		 */
		@Override
		public void close() throws IOException {
			delegate.flush();
		}

		/**
		 * Remove ANSI escape sequences from a string.
		 *
		 * @param value The input text.
		 * @return Text with ANSI escape sequences removed.
		 */
		private static String stripAnsi(String value) {
			return ANSI_ESCAPE_PATTERN.matcher(value).replaceAll("");
		}
	}
}
