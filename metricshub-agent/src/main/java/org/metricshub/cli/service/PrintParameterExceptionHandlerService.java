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
import picocli.CommandLine.IParameterExceptionHandler;

/**
 * Handles parameter parsing errors for the MetricsHub CLI.
 *
 * <p>When an invalid argument is provided, this handler prints the error message followed by a
 * short hint explaining how to display the command usage.</p>
 */
public class PrintParameterExceptionHandlerService implements IParameterExceptionHandler {

        static final String HELP_HINT = "Run metricshub --help to display usage.";

        @Override
        public int handleParseException(CommandLine.ParameterException exception, String[] args) {
                final CommandLine commandLine = exception.getCommandLine();
                commandLine.getErr().println(commandLine.getColorScheme().errorText(exception.getMessage()));
                commandLine.getErr().println(commandLine.getColorScheme().errorText(HELP_HINT));

                if (commandLine.getExitCodeExceptionMapper() != null) {
                        return commandLine.getExitCodeExceptionMapper().getExitCode(exception);
                }

                return CommandLine.ExitCode.USAGE;
        }
}
