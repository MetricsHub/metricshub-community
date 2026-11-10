package org.metricshub.cli.util;

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

import static lombok.AccessLevel.PRIVATE;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.LocalOsHandler;

/**
 * Restores the command-line arguments of a MetricsHub launcher on Windows.
 * <p>
 * The jpackage Windows launcher splits its command line with {@code CommandLineToArgvW}, which reads a doubled
 * quote inside a quoted argument ({@code "a""b"}) differently from the C runtime and from {@code java.exe}. As a
 * result, {@code --password "a""b"}, the form that keeps the quotes balanced for {@code cmd.exe} and therefore
 * allows {@code > file} redirections, reaches the application broken.
 * <p>
 * When the application runs in a jpackage launcher on Windows, this class reads the original command line with
 * {@code GetCommandLineW} and splits it again with the rules of the Microsoft C runtime:
 * <ul>
 * <li>Arguments are separated by spaces or tabs, except inside double quotes.</li>
 * <li>{@code 2n} backslashes followed by {@code "} produce {@code n} backslashes, and the quote opens or closes a
 * quoted section.</li>
 * <li>{@code 2n+1} backslashes followed by {@code "} produce {@code n} backslashes and a literal {@code "}.</li>
 * <li>Backslashes that are not followed by {@code "} are literal.</li>
 * <li>Inside a quoted section, {@code ""} produces a literal {@code "}.</li>
 * </ul>
 * This requires the launcher not to restart itself ({@code win.norestart=true} in its {@code .cfg} file), so that
 * {@code GetCommandLineW} returns the command line typed by the user.
 */
@NoArgsConstructor(access = PRIVATE)
public class WindowsCommandLine {

	/**
	 * System property set by the jpackage launcher to the path of the launcher executable.
	 */
	private static final String JPACKAGE_APP_PATH_PROPERTY = "jpackage.app-path";

	/**
	 * Returns the arguments to parse: the original command line split with the Microsoft C runtime rules when the
	 * application runs in a jpackage launcher on Windows, or the given arguments otherwise.
	 *
	 * @param args The arguments received by the {@code main} method.
	 * @return The arguments to give to the command-line parser.
	 */
	public static String[] args(final String[] args) {
		if (!LocalOsHandler.isWindows() || System.getProperty(JPACKAGE_APP_PATH_PROPERTY) == null) {
			return args;
		}
		final String commandLine = readCommandLine();
		return commandLine == null ? args : split(commandLine);
	}

	/**
	 * Reads the command line of the current process with the {@code GetCommandLineW} function of {@code kernel32}.
	 *
	 * @return The command line, or {@code null} when it cannot be read.
	 */
	static String readCommandLine() {
		try {
			final MethodHandle getCommandLineW = Linker.nativeLinker()
				.downcallHandle(
					SymbolLookup.libraryLookup("kernel32", Arena.global()).find("GetCommandLineW").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.ADDRESS)
				);
			final MemorySegment commandLine = (MemorySegment) getCommandLineW.invokeExact();
			return commandLine.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_16LE);
		} catch (Throwable e) { // NOSONAR invokeExact declares Throwable; fall back to the JVM arguments
			return null;
		}
	}

	/**
	 * Splits a Windows command line with the rules of the Microsoft C runtime, and drops the program name.
	 *
	 * @param commandLine The full command line, starting with the program name.
	 * @return The arguments that follow the program name.
	 */
	static String[] split(final String commandLine) {
		final int length = commandLine.length();

		// The program name ends at the first space or tab outside quotes. Backslashes are literal.
		int index = 0;
		boolean quoted = false;
		while (index < length && (quoted || !isBlank(commandLine.charAt(index)))) {
			if (commandLine.charAt(index) == '"') {
				quoted = !quoted;
			}
			index++;
		}

		final List<String> arguments = new ArrayList<>();
		while (true) {
			while (index < length && isBlank(commandLine.charAt(index))) {
				index++;
			}
			if (index >= length) {
				return arguments.toArray(String[]::new);
			}

			final StringBuilder argument = new StringBuilder();
			quoted = false;
			while (index < length && (quoted || !isBlank(commandLine.charAt(index)))) {
				final char c = commandLine.charAt(index);
				if (c == '\\') {
					final int start = index;
					while (index < length && commandLine.charAt(index) == '\\') {
						index++;
					}
					final int backslashes = index - start;
					if (index < length && commandLine.charAt(index) == '"') {
						argument.append("\\".repeat(backslashes / 2));
						if (backslashes % 2 == 1) {
							argument.append('"');
							index++;
						}
					} else {
						argument.append("\\".repeat(backslashes));
					}
				} else if (c == '"') {
					if (quoted && index + 1 < length && commandLine.charAt(index + 1) == '"') {
						argument.append('"');
						index += 2;
					} else {
						quoted = !quoted;
						index++;
					}
				} else {
					argument.append(c);
					index++;
				}
			}
			arguments.add(argument.toString());
		}
	}

	/**
	 * Whether the given character separates arguments.
	 *
	 * @param c The character to check.
	 * @return {@code true} for a space or a tab.
	 */
	private static boolean isBlank(final char c) {
		return c == ' ' || c == '\t';
	}
}
