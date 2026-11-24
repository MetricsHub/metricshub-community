package org.metricshub.extension.ping;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Ping Extension
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

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.RequestStats;

/**
 * PingRequestExecutor is responsible for sending ping requests to a specified host to verify its reachability.
 * It utilizes the ICMP protocol to perform the ping operation.
 */
@Slf4j
public class PingRequestExecutor {

	/**
	 * The {@code operation_type} label of the ping requests in {@code metricshub.host.requests}.
	 */
	public static final String OPERATION_TYPE = "ping";

	/**
	 * Margin under the timeout above which a negative answer is counted as a timeout. The JDK measures the
	 * timeout with its own millisecond timers (poll loops on Unix, IcmpSendEcho on Windows, ~15.6 ms ticks),
	 * so a ping that ran out of time can return a few milliseconds before the timeout measured here. 100 ms
	 * covers that rounding while an immediate failure (no route, host unreachable) returns far below a timeout
	 * configured in seconds.
	 */
	static final long TIMEOUT_TOLERANCE_MS = 100;

	/**
	 * Sends a ping request to the specified host to verify its reachability using the ICMP protocol, and records
	 * the outcome of the attempt (name resolution and reachability test) in the given statistics.
	 *
	 * @param hostname     The name of the host that will be tested. It must be a valid hostname or IP address.
	 * @param timeout      The timeout in milliseconds for the ping request.
	 * @param requestStats The request statistics of the monitored resource, or {@code null} to record nothing.
	 * @return true if the host is reachable within the specified timeout; false otherwise, or when the caller is
	 *         interrupted (its interrupt status is kept).
	 * @throws UnknownHostException if the host cannot be determined from the given hostname.
	 */
	@WithSpan("Ping")
	boolean ping(
		@SpanAttribute("host.hostname") final String hostname,
		@SpanAttribute("ping.timeout") final int timeout,
		final RequestStats requestStats
	) throws UnknownHostException {
		// A thread already interrupted (an expired deadline of its job) resolves and pings nothing, and counts nothing
		if (Thread.currentThread().isInterrupted()) {
			return false;
		}
		final InetAddress pingAddress;
		try {
			pingAddress = interruptibly(() -> resolve(hostname));
		} catch (UnknownHostException e) {
			record(requestStats, RequestOutcome.FAILED);
			throw e;
		} catch (IOException e) {
			// Interrupted: the name resolution throws nothing else
			record(requestStats, RequestOutcome.of(e));
			log.debug("Hostname {} - Ping interrupted.", hostname);
			return false;
		}

		final long start = nanoTime();
		try {
			final boolean reachable = interruptibly(() -> isReachable(pingAddress, timeout));
			record(requestStats, reachable ? RequestOutcome.COMPLETED : classifyNoAnswer(nanoTime() - start, timeout));
			return reachable;
		} catch (IOException e) {
			// A SocketTimeoutException or another InterruptedIOException (the caller was interrupted) is a timeout
			record(requestStats, RequestOutcome.of(e));
			if (e instanceof InterruptedIOException && Thread.currentThread().isInterrupted()) {
				log.debug("Hostname {} - Ping interrupted.", hostname);
			} else {
				log.error("Hostname {}. A network error occurred: %s", hostname, e);
			}
			return false;
		}
	}

	/**
	 * Runs a probe on its own thread, which the caller stops waiting for when it is interrupted: the probe may still
	 * run there, but its result is dropped.
	 *
	 * @param <T>   The type of the result of the probe.
	 * @param probe The name resolution or the reachability test.
	 * @return The result of the probe.
	 * @throws IOException The exception of the probe, an {@link InterruptedIOException} when the caller is interrupted
	 *                     (its interrupt status is kept).
	 */
	private static <T> T interruptibly(final Callable<T> probe) throws IOException {
		try {
			return ThreadHelper.executeInterruptibly(probe);
		} catch (InterruptedException e) {
			final InterruptedIOException interrupted = new InterruptedIOException("Ping interrupted");
			interrupted.initCause(e);
			throw interrupted;
		} catch (ExecutionException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof IOException ioException) {
				throw ioException;
			}
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IOException(cause);
		}
	}

	/**
	 * Classifies a negative answer of {@link InetAddress#isReachable(int)}, which reports both "no answer before
	 * the timeout" and "unreachable" as {@code false}. This is a deliberate, delay-based classification: a negative
	 * answer that took the whole timeout is a timeout, a faster one is a failure.
	 * ponytail: delay-based, a route that silently drops the packets until the timeout is counted as a timeout;
	 * only a JDK API that reports the ICMP answer (or a native ping) would tell them apart.
	 *
	 * @param elapsedNanos The time spent in {@link InetAddress#isReachable(int)}, in nanoseconds.
	 * @param timeout      The timeout of the ping request, in milliseconds.
	 * @return {@link RequestOutcome#TIMEOUT} or {@link RequestOutcome#FAILED}.
	 */
	static RequestOutcome classifyNoAnswer(final long elapsedNanos, final int timeout) {
		return TimeUnit.NANOSECONDS.toMillis(elapsedNanos) >= timeout - TIMEOUT_TOLERANCE_MS
			? RequestOutcome.TIMEOUT
			: RequestOutcome.FAILED;
	}

	/**
	 * Records the outcome of one ping attempt when statistics are given.
	 *
	 * @param requestStats The request statistics of the monitored resource, or {@code null}.
	 * @param outcome      The outcome of the attempt.
	 */
	private static void record(final RequestStats requestStats, final RequestOutcome outcome) {
		if (requestStats != null) {
			requestStats.record(OPERATION_TYPE, outcome);
		}
	}

	/**
	 * Resolves the hostname. Test seam.
	 *
	 * @param hostname The hostname or IP address.
	 * @return The resolved address.
	 * @throws UnknownHostException if the hostname cannot be resolved.
	 */
	InetAddress resolve(final String hostname) throws UnknownHostException {
		return InetAddress.getByName(hostname);
	}

	/**
	 * Tests the reachability of the address. Test seam.
	 *
	 * @param address The address to ping.
	 * @param timeout The timeout in milliseconds.
	 * @return {@code true} if the address answered before the timeout.
	 * @throws IOException if a network error occurs.
	 */
	boolean isReachable(final InetAddress address, final int timeout) throws IOException {
		return address.isReachable(timeout);
	}

	/**
	 * Returns the current value of the monotonic clock. Test seam.
	 *
	 * @return {@link System#nanoTime()}.
	 */
	long nanoTime() {
		return System.nanoTime();
	}
}
