package org.metricshub.agent.process.config;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import lombok.Builder;
import lombok.Data;
import org.metricshub.agent.process.io.ProcessorHelper;
import org.metricshub.agent.process.io.StreamProcessor;
import org.metricshub.agent.process.runtime.AbstractProcess;
import org.slf4j.Logger;

/**
 * Defines how to process the STDOUT and STDERR.<br> The
 * {@link StreamProcessor} of the STDOUT is mandatory, if you don't define the
 * {@link StreamProcessor} of the STDERR, a redirection will take place and
 * the standard error and standard output will be merged. You can change this behavior by overriding
 * the onBeforeProcessStart method in the {@link AbstractProcess}.
 */
@Data
@Builder
public class ProcessOutput {

	final StreamProcessor outputProcessor;
	final StreamProcessor errorProcessor;

	/**
	 * Builds a {@link ProcessOutput} which prints the process standard output only
	 *
	 * @param label The keyword used in the beginning of each printed line
	 * @return a new {@link ProcessOutput}
	 */
	public static ProcessOutput namedConsoleOutput(final String label) {
		return builder().outputProcessor(ProcessorHelper.namedConsole(String.format("[%s output]", label), false)).build();
	}

	/**
	 * Builds a {@link ProcessOutput} which prints the process standard output
	 * and standard error to the console.
	 *
	 * @param label The keyword used in the beginning of each printed line
	 * @return a new {@link ProcessOutput}
	 */
	public static ProcessOutput namedConsole(final String label) {
		return builder()
			.outputProcessor(ProcessorHelper.namedConsole(String.format("[%s output]", label), false))
			.errorProcessor(ProcessorHelper.namedConsole(String.format("[%s error]", label), true))
			.build();
	}

	/**
	 * Builds a {@link ProcessOutput} which ignores the process standard output
	 * and standard error.
	 *
	 * @return a new {@link ProcessOutput}
	 */
	public static ProcessOutput silent() {
		return builder().build();
	}

	/**
	 * Builds a {@link ProcessOutput} which prints the process standard output and
	 * standard error using the Slf4j {@link Logger}.
	 *
	 * @param logger Slf4j logger interface
	 * @return a new {@link ProcessOutput}
	 */
	public static ProcessOutput log(final Logger logger) {
		return builder()
			.outputProcessor(ProcessorHelper.safeLogger(logger, Slf4jLevel.DEBUG))
			.errorProcessor(ProcessorHelper.safeLogger(logger, Slf4jLevel.ERROR))
			.build();
	}

	/**
	 * Builds a {@link ProcessOutput} which prints the process standard output only
	 * using the Slf4j {@link Logger}.
	 *
	 * @param logger Slf4j logger interface
	 * @return a new {@link ProcessOutput}
	 */
	public static ProcessOutput logOutput(final Logger logger) {
		return builder().outputProcessor(ProcessorHelper.safeLogger(logger, Slf4jLevel.DEBUG)).build();
	}
}
