package org.metricshub.engine.common.helpers;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

/**
 * Helper class for logging opeations
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class LoggingHelper {

	static final Pattern PASSWORD_REGEX = Pattern.compile("(?i)(password).*");

	/**
	 * Log the given throwable
	 *
	 * @param connectorId  The identifier of the connector
	 * @param sourceKey    The key of the source
	 * @param hostname     The host's hostname
	 * @param context      Additional information about the operation
	 * @param throwable    The caught throwable to log
	 */
	public static void logSourceError(
		final String connectorId,
		final String sourceKey,
		final String context,
		final String hostname,
		final Throwable throwable
	) {
		if (log.isErrorEnabled()) {
			log.error(
				"Hostname {} - Source [{}] was unsuccessful due to an exception." +
				" Context [{}]. Connector: [{}]. Returning an empty table. Errors:\n{}\n",
				hostname,
				sourceKey,
				context,
				connectorId,
				StringHelper.getStackMessages(throwable)
			);
		}

		if (log.isDebugEnabled()) {
			log.debug(
				String.format(
					"Hostname %s - Source [%s] was unsuccessful due to an exception. Context [%s]. Connector: [%s]. Returning an empty table. Stack trace:",
					hostname,
					sourceKey,
					context,
					connectorId
				),
				throwable
			);
		}
	}

	/**
	 * Run the given runnable if the tracing mode of the logger is enabled
	 *
	 * @param runnable
	 */
	public static void trace(final Runnable runnable) {
		if (log.isTraceEnabled()) {
			runnable.run();
		}
	}

	/**
	 * Run the given runnable if the debug mode of the logger is enabled
	 *
	 * @param runnable
	 */
	public static void debug(final Runnable runnable) {
		if (log.isDebugEnabled()) {
			runnable.run();
		}
	}

	/**
	 * Log the given message at the given logging {@link Level} after replacing the potential passwords with "************".
	 * The number of '*' characters if fixed and independent from the passwords lengths.
	 * @param loggerLevel The logging {@link Level}.
	 * @param log         The message to log.
	 */
	public static void logWithHiddenPassword(final Level loggerLevel, final Supplier<String> messageSupplier) {
		if (!log.isEnabledForLevel(loggerLevel)) {
			return;
		}
		final String message = messageSupplier.get();
		// No message to log, no logging to execute.
		if (loggerLevel == null || message == null || message.isBlank()) {
			return;
		}

		log.makeLoggingEventBuilder(loggerLevel).log(() -> sanatizeMessage(message));
	}

	protected static String sanatizeMessage(final String message) {
		return PASSWORD_REGEX.matcher(message).replaceAll("$1**********");
	}
}
