package org.metricshub.engine.telemetry;

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

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The terminal state of one protocol request attempt, exported as the {@code state} attribute of
 * {@code metricshub.host.requests}.
 */
@AllArgsConstructor
public enum RequestOutcome {
	/**
	 * The request was executed and answered, including with a valid empty result.
	 */
	COMPLETED("completed"),
	/**
	 * The request did not complete before its deadline, reported by the protocol client or by the caller that
	 * waited for it, or it was cancelled by the engine when a deadline of the strategy expired.
	 */
	TIMEOUT("timeout"),
	/**
	 * The request failed for any other reason: connection, authentication, protocol or execution error.
	 */
	FAILED("failed");

	/**
	 * Maximum number of causes visited, a guard against cause chains that loop.
	 */
	private static final int MAX_CAUSE_DEPTH = 32;

	/**
	 * The value of the {@code state} attribute.
	 */
	@Getter
	private final String label;

	/**
	 * Classifies the exception thrown by a request that was started: {@link #TIMEOUT} when the exception or one
	 * of its causes is a timeout ({@link TimeoutException}, {@link java.net.SocketTimeoutException},
	 * {@link SQLTimeoutException}) or an interruption ({@link InterruptedException}, {@link InterruptedIOException},
	 * {@link ClosedByInterruptException}: the engine interrupts a request when a deadline expires), {@link #FAILED}
	 * otherwise.
	 *
	 * @param throwable The exception thrown by the request.
	 * @return {@link #TIMEOUT} or {@link #FAILED}.
	 */
	public static RequestOutcome of(final Throwable throwable) {
		Throwable current = throwable;
		for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
			if (isTimeout(current)) {
				return TIMEOUT;
			}
			current = current.getCause();
		}
		return FAILED;
	}

	/**
	 * Whether the given exception, without its causes, reports a timeout or an interruption.
	 *
	 * @param throwable The exception to test.
	 * @return {@code true} for a timeout or an interruption.
	 */
	private static boolean isTimeout(final Throwable throwable) {
		return (throwable instanceof TimeoutException ||
			throwable instanceof InterruptedIOException ||
			throwable instanceof InterruptedException ||
			throwable instanceof ClosedByInterruptException ||
			throwable instanceof SQLTimeoutException);
	}
}
