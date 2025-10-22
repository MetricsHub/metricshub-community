package org.metricshub.cli.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class PrintParameterExceptionHandlerServiceTest {

	@Test
	void shouldPrintErrorMessageAndHelpHintWithoutUsage() {
		final CommandLine cli = new CommandLine(new MetricsHubCliService());
		cli.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF));
		cli.setParameterExceptionHandler(new PrintParameterExceptionHandlerService());

		final StringWriter errWriter = new StringWriter();
		cli.setErr(new PrintWriter(errWriter, true));

		final int exitCode = cli.execute("example-host");

		assertEquals(CommandLine.ExitCode.USAGE, exitCode);

		final String errorOutput = errWriter.toString();
		assertTrue(errorOutput.contains("Missing required option: '--type=TYPE'"));
		assertTrue(errorOutput.contains(PrintParameterExceptionHandlerService.HELP_HINT));
		assertFalse(errorOutput.contains("Usage"));
	}
}
