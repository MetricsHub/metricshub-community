package org.metricshub.agent.process.io;

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

import java.io.Reader;
import java.util.Optional;
import java.util.function.BiFunction;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.metricshub.agent.process.config.Slf4jLevel;
import org.slf4j.Logger;

/**
 * Use this helper class to create console, log and silent {@link StreamProcessor}
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessorHelper {

	/**
	 * Create a new {@link ConsoleStreamProcessor} wrapped by the {@link NamedStreamProcessor}
	 *
	 * @param name    The name which each line starts with
	 * @param isError Whether the console processor handles output errors or not
	 * @return new {@link StreamProcessor}
	 */
	public static StreamProcessor namedConsole(final String name, final boolean isError) {
		return named(name, console(isError));
	}

	/**
	 * Create a new {@link NamedStreamProcessor} instance
	 *
	 * @param name        The name which each line starts with
	 * @param destination The next {@link StreamProcessor} to be called after adding the name to the beginning of the line
	 * @return new {@link StreamProcessor}
	 */
	public static StreamProcessor named(final String name, final StreamProcessor destination) {
		return new NamedStreamProcessor(name, destination);
	}

	/**
	 * Create a new {@link ConsoleStreamProcessor} which prints the block to the
	 * console
	 *
	 * @param isError Whether the console processor handles output errors or not
	 * @return new {@link StreamProcessor}
	 */
	public static StreamProcessor console(final boolean isError) {
		return new ConsoleStreamProcessor(isError);
	}

	/**
	 * Create a new {@link Slf4jStreamProcessor} which uses the given logger to log
	 * output messages
	 *
	 * @param logger Slf4j {@link Logger}
	 * @param level  Level used to log messages
	 * @return new {@link StreamProcessor}
	 */
	public static StreamProcessor logger(final Logger logger, final Slf4jLevel level) {
		return new Slf4jStreamProcessor(logger, level);
	}

	/**
	 * Constructs a new {@link Slf4jStreamProcessor} using the specified logger to log output messages,
	 * and then encapsulates it within a {@link Slf4jSafeStreamProcessor} for enhanced logging safety.
	 *
	 * @param logger Slf4j {@link Logger}
	 * @param level  Level used to log messages
	 * @return new {@link StreamProcessor}
	 */
	public static StreamProcessor safeLogger(final Logger logger, final Slf4jLevel level) {
		return new Slf4jSafeStreamProcessor(logger(logger, level));
	}

	/**
	 * Create a new {@link Slf4jSafeStreamProcessor} wrapped by the {@link NamedStreamProcessor}
	 *
	 * @param name    The name which each line starts with
	 * @param logger  Slf4j {@link Logger}
	 * @param level   Log level such as {@link Slf4jLevel#DEBUG}, {@link Slf4jLevel#INFO}, {@link Slf4jLevel#ERROR}, ...etc.
	 * @return new {@link StreamProcessor}
	 */
	public static StreamProcessor namedLogger(final String name, final Logger logger, final Slf4jLevel level) {
		return named(name, safeLogger(logger, level));
	}

	/**
	 * Connect the given {@link Reader} to the {@link StreamProcessor} in order to
	 * start absorbing the process' output
	 *
	 * @param reader         Output reader
	 * @param processor      Output processor
	 * @param linkerFunction linker function creator witch builds runnable instance
	 *                       of {@link AbstractReaderProcessor}
	 * @return new {@link Thread} instance
	 */
	public static Optional<Thread> connect(
		final Reader reader,
		final StreamProcessor processor,
		@NonNull final BiFunction<Reader, StreamProcessor, AbstractReaderProcessor> linkerFunction
	) {
		if (reader == null || processor == null) {
			return Optional.empty();
		}

		final Thread thread = new Thread(linkerFunction.apply(reader, processor));

		thread.setDaemon(true);
		thread.start();

		return Optional.of(thread);
	}
}
