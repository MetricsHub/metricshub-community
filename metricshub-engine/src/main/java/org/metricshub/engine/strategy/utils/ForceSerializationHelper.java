package org.metricshub.engine.strategy.utils;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_LOCK_TIMEOUT;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.HOSTNAME_EXCEPTION_MESSAGE;

import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code ForceSerializationHelper} class provides utility methods for enforcing serialization of operations on
 * connector namespaces. It aims to acquire a lock before running an executable function, ensuring that the
 * serialization is maintained. The class is designed to have a private no-argument constructor to prevent instantiation.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ForceSerializationHelper {

	/**
	 * Force the serialization when processing the given object, this method tries
	 * to acquire the connector namespace <em>lock</em> before running the
	 * executable, if the lock cannot be acquired or there is an exception or an
	 * interruption then the defaultValue is returned
	 *
	 * @param <T>          for example {@link CriterionTestResult} or a
	 *                     {@link SourceTable}
	 *
	 * @param executable       the supplier executable function, e.g. visiting a criterion or a source
	 * @param telemetryManager wraps the host's properties where the connector namespace holds the lock
	 * @param connectorId      the identifier of the connector (criterion, source or compute) belongs to
	 * @param objToProcess     the object to process used for debug purpose
	 * @param description      the object to process description used in the debug messages
	 * @param defaultValue     the default value to return in case of any glitch
	 * @return T instance
	 */
	public static <T> T forceSerialization(
		@NonNull Supplier<T> executable,
		@NonNull TelemetryManager telemetryManager,
		@NonNull final String connectorId,
		final Object objToProcess,
		@NonNull final String description,
		@NonNull final T defaultValue
	) {
		return forceSerialization(
			executable,
			telemetryManager,
			connectorId,
			objToProcess,
			description,
			defaultValue,
			DEFAULT_LOCK_TIMEOUT
		);
	}

	/**
	 * Force the serialization when processing the given object, waiting at most the given time for the lock and for the
	 * requests of a job cut short that are still running on the device.
	 *
	 * @param <T>              for example {@link CriterionTestResult} or a {@link SourceTable}
	 * @param executable       the supplier executable function, e.g. visiting a criterion or a source
	 * @param telemetryManager wraps the host's properties where the connector namespace holds the lock
	 * @param connectorId      the identifier of the connector (criterion, source or compute) belongs to
	 * @param objToProcess     the object to process used for debug purpose
	 * @param description      the object to process description used in the debug messages
	 * @param defaultValue     the default value to return in case of any glitch
	 * @param lockTimeout      the maximum time to wait before running the executable, in seconds
	 * @return T instance
	 */
	static <T> T forceSerialization(
		@NonNull Supplier<T> executable,
		@NonNull TelemetryManager telemetryManager,
		@NonNull final String connectorId,
		final Object objToProcess,
		@NonNull final String description,
		@NonNull final T defaultValue,
		final long lockTimeout
	) {
		final ConnectorNamespace connectorNamespace = telemetryManager.getHostProperties()
			.getConnectorNamespace(connectorId);
		final ReentrantLock forceSerializationLock = connectorNamespace.getForceSerializationLock();
		final Queue<ExecutorService> requestsInFlight = connectorNamespace.getSerializedRequestsInFlight();
		final String hostname = telemetryManager.getHostname();

		// The job deadline expired or the job was cancelled: send no more request and keep the interrupt
		if (Thread.currentThread().isInterrupted()) {
			log.warn(
				"Hostname {} - Thread interrupted: skipping the force serialized {} {}. Connector: {}.",
				hostname,
				description,
				objToProcess,
				connectorId
			);
			return defaultValue;
		}

		boolean interrupted = false;
		boolean isLockAcquired = false;
		boolean isDeviceFree = false;
		T result = defaultValue;
		try {
			try {
				// One budget for the lock and for the requests of a job cut short that are still running on the device
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(lockTimeout);
				// Try to get the lock
				isLockAcquired = forceSerializationLock.tryLock(lockTimeout, TimeUnit.SECONDS);
				isDeviceFree = isLockAcquired && awaitRequestsInFlight(requestsInFlight, deadline);
			} catch (InterruptedException e) {
				interrupted = true;
				log.error(
					"Hostname {} - Interrupted exception detected when trying to acquire the force serialization lock to process {} {}. Connector: {}.",
					hostname,
					description,
					objToProcess,
					connectorId
				);

				log.debug(HOSTNAME_EXCEPTION_MESSAGE, hostname, e);

				return defaultValue;
			}

			if (isDeviceFree) {
				// A request given up on because this thread is interrupted is waited for by the next serialized request
				result = ThreadHelper.trackingAbandonedRequests(requestsInFlight, executable);
			} else if (isLockAcquired) {
				log.error(
					"Hostname {} - A request of a job cut short is still running on the device: could not process {} {}. Connector: {}.",
					hostname,
					description,
					objToProcess,
					connectorId
				);
			} else {
				log.error(
					"Hostname {} - Could not acquire the force serialization lock to process {} {}. Connector: {}.",
					hostname,
					description,
					objToProcess,
					connectorId
				);
			}

			return result;
		} finally {
			if (isLockAcquired && forceSerializationLock.isHeldByCurrentThread()) {
				// Release the lock when the executable is terminated
				forceSerializationLock.unlock();
			}

			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Waits until the deadline for every request of a job cut short that may still be talking to the device. The
	 * requests that ended are removed from the queue.
	 *
	 * @param requestsInFlight the queue of requests, each one terminating when its request ends
	 * @param deadline         the deadline, as a {@link System#nanoTime()} value
	 * @return whether no request is still running
	 * @throws InterruptedException if interrupted while waiting
	 */
	private static boolean awaitRequestsInFlight(final Queue<ExecutorService> requestsInFlight, final long deadline)
		throws InterruptedException {
		for (ExecutorService request = requestsInFlight.peek(); request != null; request = requestsInFlight.peek()) {
			if (!request.awaitTermination(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
				return false;
			}
			requestsInFlight.remove(request);
		}
		return true;
	}
}
