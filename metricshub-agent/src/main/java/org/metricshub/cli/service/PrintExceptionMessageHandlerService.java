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

import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

/**
 * Class in charge of output a nice-to-read error message in case of an exception
 * occurs during the CLI execution (and not related to invalid arguments)
 */
public class PrintExceptionMessageHandlerService implements IExecutionExceptionHandler {

	private static final int MAXIMUM_CAUSE_DEPTH = 10;

	@Override
	public int handleExecutionException(Exception exception, CommandLine commandLine, ParseResult parseResult) {
		int depth = 0;
		Throwable cause = exception;

		// We will loop through a maximum of 10 causes
		while (depth < MAXIMUM_CAUSE_DEPTH && cause != null) {
			String message = " ".repeat(depth * 2) + cause.getClass().getSimpleName();
			if (cause.getMessage() != null) {
				message = message + ": " + cause.getMessage();
			}
			commandLine.getErr().println(commandLine.getColorScheme().errorText(message));

			// Do we have a cause?
			cause = cause.getCause();
			depth++;
		}

		return CommandLine.ExitCode.SOFTWARE;
	}
}
