package org.metricshub.agent.process.config;

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

import java.util.function.Function;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;

/**
 * Motivation: There is no way to automatically load a logging method from Slf4j
 * based on a log level.<br>
 * Use the logger enumerations to automatically access Slf4j log methods through
 * the function {@link LogMethod#log(String, Object...)}.<br>
 */
@AllArgsConstructor
public enum Slf4jLevel {
	/**
	 * Trace logging level
	 */
	TRACE(l -> l::trace, Logger::isTraceEnabled),
	/**
	 * Debug logging level
	 */
	DEBUG(l -> l::debug, Logger::isDebugEnabled),
	/**
	 * Info logging level
	 */
	INFO(l -> l::info, Logger::isInfoEnabled),
	/**
	 * Warning logging level
	 */
	WARN(l -> l::warn, Logger::isWarnEnabled),
	/**
	 * Error logging level
	 */
	ERROR(l -> l::error, Logger::isErrorEnabled);

	/**
	 * Functional interface representing a log method.
	 */
	public interface LogMethod {
		/**
		 * Log a message with the specified format and arguments.
		 *
		 * @param format    The format string
		 * @param arguments The arguments referenced by the format string
		 */
		void log(String format, Object... arguments);
	}

	private final Function<Logger, LogMethod> logMethod;
	private final Function<Logger, Boolean> isEnabledMethod;

	/**
	 * Access the log method using the given logger.<br>
	 * Example: call <code>DEBUG.withLogger(Logger).log(String, Object...)</code> to
	 * log your debug message.
	 *
	 * @param logger Slf4j logger
	 * @return The {@link LogMethod} implementation.
	 */
	public LogMethod withLogger(Logger logger) {
		return logMethod.apply(logger);
	}

	/**
	 * Check the log level is enable with the given logger
	 * @param logger Slf4j logger
	 * @return <code>true</code> if the log level is enabled otherwise <code>false</code>
	 */
	public boolean isEnabled(Logger logger) {
		return isEnabledMethod.apply(logger);
	}
}
